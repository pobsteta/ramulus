pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // terrain-style (mission 019) : bibliothèque commune de pobsteta, publiée par JitPack (comme Surculus, settings.gradle.kts).
        maven("https://jitpack.io") { content { includeGroup("com.github.pobsteta.terrain") } }
    }
}

rootProject.name = "Ramulus"

// Modules, sur le découpage de Marculus et Surculus : domaine pur (Kotlin JVM), application Compose.
// :data : journal Room et sauvegarde .ramsync (mission 005) ; :gpkg (GeoPackage) et :ia (modèle embarqué) viendront
// avec leurs missions.
include(":core")
include(":data")
include(":app")
