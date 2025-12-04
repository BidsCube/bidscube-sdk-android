import org.gradle.api.tasks.bundling.Jar

plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.bidscube.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    val media3Version = "1.4.1"

    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    compileOnly("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("com.google.ads.interactivemedia.v3:interactivemedia:3.33.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    // Image loading
    implementation("com.github.bumptech.glide:glide:4.15.1")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk"
                version = System.getenv("BidscubeVersion") ?: "1.2.0"

//                artifact(layout.buildDirectory.file("outputs/aar/sdk-release.aar")) {
//                    extension = "aar"
//                }

                artifact("$buildDir/outputs/aar/sdk-release.aar") {
                    extension = "aar"
                }

                artifact(tasks.named("sourcesJar"))
                artifact(tasks.named("javadocJar"))

                from(components["release"])

                pom {
                    name.set("Bidscube SDK")
                    description.set("The official Bidscube SDK for Android advertising platform")
                    url.set("https://github.com/BidsCube/bidscube-sdk")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/BidsCube/bidscube-sdk/blob/main/LICENSE")
                        }
                    }

                    developers {
                        developer {
                            id.set("bidscube-team")
                            name.set("Bidscube Team")
                            email.set("dev@bidscube.com")
                            organization.set("Bidscube")
                            organizationUrl.set("https://bidscube.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/BidsCube/bidscube-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/BidsCube/bidscube-sdk.git")
                        url.set("https://github.com/BidsCube/bidscube-sdk")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "central"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.findProperty("mavenCentralUsername") as String? ?: ""
                    password = project.findProperty("mavenCentralPassword") as String? ?: ""
                }
            }
        }
    }
}
signing {
    useGpgCmd()
}


afterEvaluate {
    val releasePub = publishing.publications.findByName("release")
    if (releasePub != null) {
        signing.sign(releasePub)
    } else {
        publishing.publications.withType(MavenPublication::class.java).forEach {
            signing.sign(it)
        }
    }
}

// Validation task: ensure the release publication contains expected artifacts before publish
afterEvaluate {
    val validateReleasePublication by tasks.registering {
        dependsOn("assembleRelease")
        // ensure our source/javadoc jar tasks run
        dependsOn(tasks.named("sourcesJar"))
        dependsOn(tasks.named("javadocJar"))
        doLast {
            val pub = publishing.publications.findByName("release") as? MavenPublication
                ?: throw GradleException("No 'release' publication found")

            val missing = mutableListOf<String>()

            // Check presence of an AAR artifact in the publication
            // Prefer the AAR produced under build/outputs/aar
            val aarCandidate = file("$buildDir/outputs/aar/sdk-release.aar").takeIf { it.exists() }
                ?: fileTree("$buildDir/outputs/aar").matching { include("*.aar") }.files.firstOrNull()

            if (aarCandidate == null) {
                missing += "AAR not found in build/outputs/aar (expected sdk-release.aar or any .aar there)"
            } else {
                println("Found AAR to publish: ${aarCandidate.absolutePath}")
            }

            // Locate jars in build/libs (main jar, sources and javadoc) — this is where Gradle places published jars
            val libsDir = file("$buildDir/libs")
            val mainJar = libsDir.listFiles()?.firstOrNull { it.extension == "jar" && !it.name.contains("sources") && !it.name.contains("javadoc") }
            val sourcesJarFileFromLibs = libsDir.listFiles()?.firstOrNull { it.name.contains("sources") && it.extension == "jar" }
            val javadocJarFileFromLibs = libsDir.listFiles()?.firstOrNull { it.name.contains("javadoc") && it.extension == "jar" }

            if (sourcesJarFileFromLibs == null) {
                missing += "sources JAR not found under $buildDir/libs (looked for *sources*.jar)"
            } else {
                println("Found sources jar: ${sourcesJarFileFromLibs.absolutePath}")
            }
            if (javadocJarFileFromLibs == null) {
                missing += "javadoc JAR not found under $buildDir/libs (looked for *javadoc*.jar)"
            } else {
                println("Found javadoc jar: ${javadocJarFileFromLibs.absolutePath}")
            }

            // Also accept previously-found AGP outputs (fallback) — we've done earlier fallbacks; if they exist, good.

            // If the publication references artifact files explicitly, warn only if both the publication file is missing and the corresponding built artifact is missing
            pub.artifacts.forEach { art ->
                val f = art.file
                val cls = art.classifier ?: "<no classifier>"
                val ext = art.extension ?: "<no ext>"
                if (f != null && f.exists()) {
                    println("Publication artifact exists: classifier=$cls ext=$ext file=${f.absolutePath}")
                } else {
                    // map classifier/ext to expected built output
                    val expected = when (cls) {
                        "sources" -> sourcesJarFileFromLibs
                        "javadoc" -> javadocJarFileFromLibs
                        else -> aarCandidate ?: mainJar
                    }
                    if (expected == null || !expected.exists()) {
                        val filePath = f?.path ?: "<publication-file-missing>"
                        missing += "publication artifact missing: classifier=$cls ext=$ext file=$filePath"
                    } else {
                        println("Publication artifact ${cls}/${ext} resolved to built output: ${expected.absolutePath}")
                    }
                }
            }

            if (missing.isNotEmpty()) {
                throw GradleException("Publication validation failed:\n\t" + missing.joinToString("\n\t"))
            }

            println("Publication 'release' validated: all artifacts present.")
        }
    }

    // Make publish tasks depend on validation
    tasks.matching { it.name.startsWith("publish", ignoreCase = true) }.configureEach {
        dependsOn(validateReleasePublication)
    }
}
