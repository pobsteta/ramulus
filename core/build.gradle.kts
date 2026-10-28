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
            include("deperis/*.csv")
        }
    }
}

dependencies {
    // Lecture et écriture du .ramsync (JSON Lines, arbre JSON sans réflexion), comme Surculus (FormatSursync.kt).
    implementation(libs.gson)

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
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
