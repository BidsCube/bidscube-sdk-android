import org.gradle.api.GradleException
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension

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

    flavorDimensions += "vastIma"
    productFlavors {
        create("withIma") {
            dimension = "vastIma"
            isDefault = true
        }
        create("noIma") {
            dimension = "vastIma"
        }
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
        singleVariant("withImaRelease") {
            withSourcesJar()
            withJavadocJar()
        }
        singleVariant("noImaRelease") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    compileOnly("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    "withImaImplementation"("com.google.ads.interactivemedia.v3:interactivemedia:3.37.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk"
                version = project.version.toString()
                from(components["withImaRelease"])
                pom {
                    name.set("Bidscube SDK (full / VAST-IMA)")
                    description.set(
                        "Bidscube SDK for Android with Google IMA (VAST). " +
                            "For a smaller AAR without the IMA dependency, use artifactId bidscube-sdk-lite. " +
                            "Call SDKConfig.Builder.videoAdsEnabled(true) for VAST; default is false for minimal integrations."
                    )
                    url.set("https://github.com/BidsCube/bidscube-sdk")
                    appendCommonPom(this)
                }
            }
            create<MavenPublication>("lite") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk-lite"
                version = project.version.toString()
                from(components["noImaRelease"])
                pom {
                    name.set("Bidscube SDK (lite, no IMA in graph)")
                    description.set(
                        "Bidscube SDK for Android without Google IMA. Smaller DEX/dependency footprint. " +
                            "VAST/IMA not included — video APIs fail unless you add the full bidscube-sdk or Google IMA yourself."
                    )
                    url.set("https://github.com/BidsCube/bidscube-sdk")
                    appendCommonPom(this)
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
        dependsOn("assembleWithImaRelease", "assembleNoImaRelease")
        dependsOn("generatePomFileForReleasePublication", "generatePomFileForLitePublication")
        doLast {
            val publishingExt = project.extensions.getByType(PublishingExtension::class.java)
            fun validate(pubName: String, artId: String) {
                val pub = publishingExt.publications.getByName(pubName) as MavenPublication
                val aarArtifacts = pub.artifacts.filter { it.extension == "aar" }
                if (aarArtifacts.size != 1) {
                    throw GradleException(
                        "Publication $pubName must contain exactly one AAR, found ${aarArtifacts.size}. " +
                            pub.artifacts.joinToString { "${it.classifier ?: "main"}:${it.extension}" }
                    )
                }
                val aarFile = aarArtifacts.single().file
                if (aarFile == null || !aarFile.exists()) {
                    throw GradleException("Published AAR file is missing: ${aarFile?.path} ($artId)")
                }
            }
            validate("release", "bidscube-sdk")
            validate("lite", "bidscube-sdk-lite")
        }
    }

    tasks.matching { it.name.startsWith("publish", ignoreCase = true) }.configureEach {
        dependsOn(validateReleasePublication)
    }
}

fun appendCommonPom(pom: org.gradle.api.publish.maven.MavenPom) {
    pom.licenses {
        license {
            name.set("MIT License")
            url.set("https://github.com/BidsCube/bidscube-sdk/blob/main/LICENSE")
        }
    }
    pom.developers {
        developer {
            id.set("bidscube-team")
            name.set("Bidscube Team")
            email.set("dev@bidscube.com")
            organization.set("Bidscube")
            organizationUrl.set("https://bidscube.com")
        }
    }
    pom.scm {
        connection.set("scm:git:git://github.com/BidsCube/bidscube-sdk.git")
        developerConnection.set("scm:git:ssh://github.com/BidsCube/bidscube-sdk.git")
        url.set("https://github.com/BidsCube/bidscube-sdk")
    }
    pom.withXml {
        val root = asNode() as groovy.util.Node
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
