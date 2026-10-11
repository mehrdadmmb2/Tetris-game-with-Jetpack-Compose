pluginManagement {
    repositories {
        maven("https://maven.myket.ir")
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
        maven("https://maven.myket.ir")
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
//        maven { url = uri("https://repo.cafebazaar.ir/repository/maven-public/") }
    }
}
rootProject.name = "NeonTetris"
include(":app")
