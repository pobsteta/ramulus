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
    }
}

rootProject.name = "Ramulus"

// Modules, sur le découpage de Marculus et Surculus : domaine pur (Kotlin JVM), application Compose.
// :data (journal Room), :gpkg (GeoPackage) et :ia (modèle embarqué) viendront avec leurs missions.
include(":core")
include(":app")
