plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
    id("signing")
}

// CI / headless: GnuPG signatory reads signing.gnupg.passphrase (not GPG_PASSPHRASE / signing.password by default).
val gpgPassFromEnv = System.getenv("GPG_PASSPHRASE")?.trim()?.takeUnless { it.isEmpty() }
val gpgPassFromProps = (findProperty("signing.password") as String?)?.trim()?.takeUnless { it.isEmpty() }
(gpgPassFromEnv ?: gpgPassFromProps)?.let { pass ->
    extra["signing.gnupg.passphrase"] = pass
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
    implementation("com.google.ads.interactivemedia.v3:interactivemedia:3.37.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
}

signing {
    useGpgCmd()
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk"
                version = project.version.toString()

                from(components["release"])

                pom {
                    name.set("Bidscube SDK")
                    description.set(
                        "The official Bidscube SDK for Android. " +
                            "Apps must enable core library desugaring when using this artifact " +
                            "(required by Google IMA / AndroidX Media3 integration paths; see AndroidX release notes for interactivemedia)."
                    )
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
                // Legacy s01.oss.sonatype.org OSSRH is EOL; use Central Portal compatibility staging API.
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.findProperty("mavenCentralUsername") as String? ?: ""
                    password = project.findProperty("mavenCentralPassword") as String? ?: ""
                }
            }
        }
    }

    val releasePub = publishing.publications.findByName("release")
    if (releasePub != null) {
        signing.sign(releasePub)
    } else {
        publishing.publications.withType(MavenPublication::class.java).forEach {
            signing.sign(it)
        }
    }

    val validateReleasePublication by tasks.registering {
        dependsOn("assembleRelease")
        dependsOn("releaseSourcesJar")
        dependsOn("javaDocReleaseJar")
        dependsOn(tasks.named("generatePomFileForReleasePublication"))
        doLast {
            val pub = publishing.publications.findByName("release") as? MavenPublication
                ?: throw GradleException("No 'release' publication found")

            val aarArtifacts = pub.artifacts.filter { it.extension == "aar" }
            if (aarArtifacts.size != 1) {
                throw GradleException(
                    "Publication must contain exactly one AAR (Android library), found ${aarArtifacts.size}. " +
                        "Artifacts: " + pub.artifacts.joinToString { "${it.classifier ?: "main"}:${it.extension}" }
                )
            }

            val aarFile = aarArtifacts.single().file
            if (aarFile == null || !aarFile.exists()) {
                throw GradleException("Published AAR file is missing: ${aarFile?.path}")
            }

            val jars = pub.artifacts.filter { it.extension == "jar" }
            val classifiers = jars.mapNotNull { it.classifier }.toSet()
            if ("sources" !in classifiers) {
                throw GradleException("Publication must include a sources classifier jar; have: $classifiers")
            }
            if ("javadoc" !in classifiers) {
                throw GradleException("Publication must include a javadoc classifier jar; have: $classifiers")
            }
        }
    }

    tasks.matching { it.name.startsWith("publish", ignoreCase = true) }.configureEach {
        dependsOn(validateReleasePublication)
    }
}
