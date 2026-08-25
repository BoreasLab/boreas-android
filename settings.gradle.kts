pluginManagement {
    // The version algebra, as an included build. It carries the release tag
    // scheme and the versionCode packing, both with unit tests that CI runs, so
    // that no part of either lives in a workflow file or a shell script.
    includeBuild("build-logic")

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

rootProject.name = "Boreas"
include(":app")
include(":domain")
