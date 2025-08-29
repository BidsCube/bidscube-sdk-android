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

// For Gradle 6.7.1 compatibility, repositories are defined in build.gradle.kts files
// instead of using dependencyResolutionManagement

rootProject.name = "sdk"
include(":app")
include(":sdk")
