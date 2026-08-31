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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "WorldTV"

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:designsystem")
include(":core:designsystem-tv")
include(":core:designsystem-mobile")

include(":data:health")
include(":data:repository")
include(":data:sync")

include(":feature:catalog")
include(":feature:player")
include(":feature:radio")
include(":feature:favorites")
include(":feature:settings")
