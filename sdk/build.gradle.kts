plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.bidscube.sdk"
    compileSdk = 35

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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    compileOnly("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation(libs.ump)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.interactivemedia)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.cardview)
    implementation(libs.material)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk"
                version = System.getenv("BidscubeVersion") ?: "1.0.1"

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
                        developerConnection.set("scm:git:ssh://github.com:BidsCube/bidscube-sdk.git")
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

    signing {
        sign(publishing.publications["release"])
        setRequired {
            gradle.taskGraph.hasTask("publish") && !gradle.taskGraph.hasTask("publishToMavenLocal")
        }
    }
}