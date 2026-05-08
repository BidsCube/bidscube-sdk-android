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

rootProject.name = "bidscube-sdk"
include(":sdk")

// Publisher test app (sibling repo folder): local `implementation(project(":sdk"))`, no Maven AAR.
include(":bidscube-testapp-android")
project(":bidscube-testapp-android").projectDir = file("../bidscube-testapp-android")
