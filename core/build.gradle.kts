import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

// Bytecode JVM 17 pour rester consommable par le module Android.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Les référentiels DEPERIS sont lus là où ils sont tenus (docs/referentiels/deperis/), sans copie dans le module.
sourceSets {
    main {
        resources {
            srcDir(rootProject.file("docs/referentiels"))
            // Le schéma du format d'échange (docs/formats/) est lu là aussi, sans copie : le motif de chaque refus y est écrit.
            srcDir(rootProject.file("docs/formats"))
            include("deperis/*.csv", "deperis-v1.schema.json")
        }
    }
}

dependencies {
    // Lecture et écriture du .ramsync (JSON Lines, arbre JSON sans réflexion), comme Surculus (FormatSursync.kt).
    implementation(libs.gson)

    // Lecture et écriture du deperis.gpkg (ADR 0003 § 1, Surculus ADR 0011) : sqlite-jdbc, jamais le SQLite d'Android.
    implementation(libs.sqlite.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
    inputs.dir(rootProject.file("fixtures")).withPropertyName("fixtures")
    inputs.dir(rootProject.file("docs/referentiels")).withPropertyName("referentiels")
    inputs.dir(rootProject.file("docs/formats")).withPropertyName("formats")
    // RAMULUS_ECHANGE_EXIGER=1 : les tests d'acceptation de l'échange (003) ne sont jamais sautés sans bruit. La
    // variable fait partie des entrées de la tâche, pour qu'un passage "exigé" ne soit pas pris pour un passage normal.
    inputs.property("echangeExiger", providers.environmentVariable("RAMULUS_ECHANGE_EXIGER").orElse(""))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
