plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

// When :sdk is included from the publisher test app, root gradle.properties may differ;
// keep version in sync via -Pbidscube.version=… or env BidscubeVersion.
version =
    (findProperty("bidscube.version") as String?)
        ?: System.getenv("BidscubeVersion")
        ?: "1.2.3"

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

                    withXml {
                        val root = asNode()
                        val existing = root.get("packaging")
                        if (existing == null) {
                            root.appendNode("packaging", "aar")
                        } else {
                            val nodeList = existing as groovy.util.NodeList
                            if (nodeList.isEmpty()) {
                                root.appendNode("packaging", "aar")
                            } else {
                                (nodeList[0] as groovy.util.Node).setValue("aar")
                            }
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                name = "dist"
                url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
            }
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

            val pomFile = layout.buildDirectory.file("publications/release/pom-default.xml").get().asFile
            if (!pomFile.exists()) {
                throw GradleException("Generated POM not found: ${pomFile.path}")
            }
            val pomText = pomFile.readText()
            if (!pomText.contains("<packaging>aar</packaging>")) {
                throw GradleException("POM must declare <packaging>aar</packaging>; check ${pomFile.path}")
            }
        }
    }

    tasks.matching { it.name.startsWith("publish", ignoreCase = true) }.configureEach {
        dependsOn(validateReleasePublication)
    }
}
