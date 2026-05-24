
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.android.library") version "8.9.1" apply false
}

val bidscubeVersion: String =
    System.getenv("BidscubeVersion")
        ?: (findProperty("bidscube.version") as String?)
        ?: "1.2.5"

subprojects {
    group = "com.bidscube"
    version = bidscubeVersion
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
