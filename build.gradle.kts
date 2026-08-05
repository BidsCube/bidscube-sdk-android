
import java.util.Properties

plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.android.library") version "8.9.1" apply false
}

val bidscubeVersion: String =
    System.getenv("BidscubeVersion")?.takeIf { it.isNotBlank() }
        ?: (findProperty("bidscube.version") as String?)?.takeIf { it.isNotBlank() }
        ?: run {
            val versionFile = file("version.properties")
            if (versionFile.exists()) {
                Properties().apply { versionFile.inputStream().use { load(it) } }
                    .getProperty("bidscube.version")
            } else null
        }
        ?: "1.2.10"

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
