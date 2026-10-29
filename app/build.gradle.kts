import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// ---------------------------------------------------------------------------------------------------------
// sqlite-jdbc sur l'appareil (ADR 0011). Maven Central publie, à côté du jar, un classifieur `natives-android`
// qui contient libsqlitejdbc.so par ABI (aarch64, arm, x86_64, x86), liées en pages de 16 Ko depuis 3.50.3.0.
// On en extrait les ABI embarquées (abiFilters ci-dessous) vers un dossier jniLibs généré ; sqlite-jdbc le charge
// alors par System.loadLibrary("sqlitejdbc") sur Android.
// ---------------------------------------------------------------------------------------------------------
val nativesSqlite: Configuration = configurations.create("nativesSqlite") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val natifsSqliteDir = layout.buildDirectory.dir("generated/jniLibs-sqlite")

abstract class ExtraireNativesSqlite : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val sortie: DirectoryProperty

    @TaskAction
    fun extraire() {
        // Dossier du jar -> ABI Android. x86 (32 bits) n'est pas embarquée (abiFilters).
        val abis = mapOf("aarch64" to "arm64-v8a", "arm" to "armeabi-v7a", "x86_64" to "x86_64")
        val racine = sortie.get().asFile
        racine.deleteRecursively()
        var extraites = 0
        for (jar in jars.files) {
            ZipFile(jar).use { zip ->
                for (entree in zip.entries().asSequence().filter { !it.isDirectory }) {
                    val m = Regex("org/sqlite/native/Linux-Android/([^/]+)/libsqlitejdbc\\.so").matchEntire(entree.name) ?: continue
                    val abi = abis[m.groupValues[1]] ?: continue
                    val cible = File(racine, "$abi/libsqlitejdbc.so")
                    cible.parentFile.mkdirs()
                    zip.getInputStream(entree).use { entrant -> cible.outputStream().use { entrant.copyTo(it) } }
                    extraites++
                }
            }
        }
        check(extraites == abis.size) { "libsqlitejdbc.so : $extraites ABI extraites sur ${abis.size} attendues" }
    }
}

val extraireNativesSqlite = tasks.register<ExtraireNativesSqlite>("extraireNativesSqlite") {
    group = "build"
    description = "Extrait libsqlitejdbc.so (arm64-v8a, armeabi-v7a, x86_64) du classifieur natives-android de sqlite-jdbc."
    jars.from(nativesSqlite)
    sortie.set(natifsSqliteDir)
}

android {
    namespace = "io.github.pobsteta.ramulus"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.pobsteta.ramulus"
        minSdk = 26
        targetSdk = 36
        // Version injectée par la CI (-PappVersionName / -PappVersionCode) ; valeurs de repli en local.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ABI embarquées (reprise de Surculus, arbitrage de Pascal du 2026-10-16) : téléphones ARM 64 et 32 bits,
        // émulateur x86_64.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Signature release : à décider en ADR (comme Surculus, ADR 0010) avant toute publication.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Essai de fumée (reprise de Surculus, ADR 0011) : calqué sur la release (même R8, mêmes natifs, même
        // signature), avec en plus les règles de proguard-fumee.pro, qui n'atteignent jamais l'APK release distribué.
        create("fumee") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            // Indispensable : l'APK de fumée s'installe à côté de l'application et ne peut ni la remplacer ni,
            // par le nettoyage du lanceur de test, la désinstaller avec ses données.
            applicationIdSuffix = ".fumee"
            versionNameSuffix = "-fumee"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-fumee.pro",
            )
            testProguardFiles("proguard-test-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // sqlite-jdbc sur Android (ADR 0011 de Surculus) : désucrage des API Java récentes.
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            // Le jar de sqlite-jdbc embarque les bibliothèques natives de bureau : inutiles sur Android, où l'on
            // charge libsqlitejdbc.so depuis jniLibs. On les retire de l'APK.
            excludes += "org/sqlite/native/**"
            excludes += "META-INF/native-image/**"
        }
    }

    // Pas de useLegacyPackaging : les natifs restent non compressés et alignés sur 16 Ko (comme Surculus).

    // Les tests instrumentés jouent sur l'APK « fumee » (R8, natifs, désucrage), pas sur le debug :
    // `./gradlew :app:connectedFumeeAndroidTest`, sur émulateur ou appareil.
    testBuildType = "fumee"
    // La tournée de départ de l'essai est la fixture A1 de l'échange (un seul exemplaire dans le dépôt).
    sourceSets.getByName("androidTest").assets.directories.add(rootProject.file("fixtures/echange/A1-tournee-depart").path)

    // Natifs Android de sqlite-jdbc, extraits du classifieur `natives-android`.
    sourceSets.getByName("main").jniLibs.directories.add(natifsSqliteDir.get().asFile.path)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Lecture et écriture de deperis.gpkg (EchangeDeperis, mission 004) : sqlite-jdbc arrive avec :core, ses natifs
    // par la tâche extraireNativesSqlite.
    implementation(project(":core"))
    implementation(project(":data"))
    // Les écrans lisent les résultats JSON de l'échange (EchangeDeperis) ; Room n'est vu que par la fabrique du journal.
    implementation(libs.gson)
    implementation(libs.room.runtime)

    nativesSqlite("org.xerial:sqlite-jdbc:${libs.versions.sqliteJdbc.get()}:natives-android")
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
}

// Les natifs doivent être là avant toute fusion de ressources JNI.
tasks.named("preBuild") { dependsOn(extraireNativesSqlite) }
