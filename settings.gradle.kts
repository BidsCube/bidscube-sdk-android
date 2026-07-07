// Optional local publish/signing credentials (never commit — see gradle.properties.example).
file("gradle.secrets.properties").takeIf { it.exists() }?.inputStream()?.use { stream ->
    java.util.Properties().apply { load(stream) }.forEach { (key, value) ->
        extra[key.toString()] = value.toString()
    }
}

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

// Publisher test app (sibling repo folder): optional — CI / clean checkout without sibling still builds :sdk.
val testAppDir = file("../bidscube-testapp-android")
if (testAppDir.exists()) {
    include(":bidscube-testapp-android")
    project(":bidscube-testapp-android").projectDir = testAppDir
}
