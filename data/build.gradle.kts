import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Journal Room en ajout seul et sauvegarde .ramsync (ADR 0005, mission 005). Réglage repris de Surculus
// (data/build.gradle.kts, commit 3c01f4b).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.pobsteta.ramulus.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    // Tests Room sous Robolectric : les schémas exportés servent d'assets de test (ADR 0005).
    sourceSets {
        named("test") { assets.directories.add("$projectDir/schemas") }
    }
    testOptions.unitTests.isIncludeAndroidResources = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Schémas Room versionnés (exportSchema = true) : base des tests de migration.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Robolectric (SDK 36) touche aux champs internes de FileDescriptor par jdk.internal.access : à ouvrir sous JDK 17 et
// plus, sinon chaque test échoue dès sa mise en place (« Failed to interact with raw FileDescriptor internals »).
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
