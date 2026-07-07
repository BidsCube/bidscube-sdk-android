import org.gradle.api.GradleException
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension
import java.util.Properties

plugins {
    id("com.android.library")
    id("maven-publish")
}

version =
    System.getenv("BidscubeVersion")?.takeIf { it.isNotBlank() }
        ?: (findProperty("bidscube.version") as String?)?.takeIf { it.isNotBlank() }
        ?: run {
            val versionFile = rootProject.file("version.properties")
            if (versionFile.exists()) {
                Properties().apply { versionFile.inputStream().use { load(it) } }
                    .getProperty("bidscube.version")
            } else null
        }
        ?: "1.2.6"

android {
    namespace = "com.bidscube.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    flavorDimensions += "videoMode"
    productFlavors {
        create("liteNoVideo") {
            dimension = "videoMode"
        }
        create("fullVideo") {
            dimension = "videoMode"
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
        isCoreLibraryDesugaringEnabled = false
    }

    publishing {
        multipleVariants("liteNoVideoRelease") {
            includeBuildTypeValues("release")
            includeFlavorDimensionAndValues("videoMode", "liteNoVideo")
            withSourcesJar()
            withJavadocJar()
        }
        multipleVariants("fullVideoRelease") {
            includeBuildTypeValues("release")
            includeFlavorDimensionAndValues("videoMode", "fullVideo")
            withSourcesJar()
            withJavadocJar()
        }
    }
}

val media3Version = "1.4.1"

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    implementation("com.google.android.ump:user-messaging-platform:2.2.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")

    "fullVideoImplementation"("androidx.media3:media3-common:$media3Version")
    "fullVideoImplementation"("androidx.media3:media3-exoplayer:$media3Version")
    "fullVideoImplementation"("androidx.media3:media3-ui:$media3Version")
    "fullVideoImplementation"("com.google.ads.interactivemedia.v3:interactivemedia:3.37.0")
}

fun org.gradle.api.Project.validateBidscubePublication(pubName: String, expectedArtifactId: String) {
    val publishing = extensions.getByType<PublishingExtension>()
    val pub =
        publishing.publications.findByName(pubName) as? MavenPublication
            ?: throw GradleException("No '$pubName' publication found")

    val aarArtifacts = pub.artifacts.filter { it.extension == "aar" }
    if (aarArtifacts.size != 1) {
        throw GradleException(
            "Publication $pubName must contain exactly one AAR, found ${aarArtifacts.size}. " +
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
        throw GradleException("Publication $pubName must include a sources classifier jar; have: $classifiers")
    }
    if ("javadoc" !in classifiers) {
        throw GradleException("Publication $pubName must include a javadoc classifier jar; have: $classifiers")
    }

    val pomFile = layout.buildDirectory.file("publications/$pubName/pom-default.xml").get().asFile
    if (!pomFile.exists()) {
        throw GradleException("Generated POM not found: ${pomFile.path}")
    }
    val pomText = pomFile.readText()
    if (!pomText.contains("<packaging>aar</packaging>")) {
        throw GradleException("POM must declare <packaging>aar</packaging>; check ${pomFile.path}")
    }
    if (!pomText.contains("<artifactId>$expectedArtifactId</artifactId>")) {
        throw GradleException("POM artifactId must be $expectedArtifactId; check ${pomFile.path}")
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("bidscubeSdkLiteNoVideo") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk-lite-no-video"
                version = project.version.toString()
                from(components["liteNoVideoRelease"])
                pom {
                    name.set("Bidscube SDK (lite, no video)")
                    description.set(
                        "Bidscube Android SDK without Google IMA / Media3 video stack. " +
                            "Video placements resolve to a no-op; apps do not need core library desugaring for this artifact."
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
            create<MavenPublication>("bidscubeSdkFullVideo") {
                groupId = "com.bidscube"
                artifactId = "bidscube-sdk-full-video"
                version = project.version.toString()
                from(components["fullVideoRelease"])
                pom {
                    name.set("Bidscube SDK (full video)")
                    description.set(
                        "Bidscube Android SDK with Google IMA and AndroidX Media3 for VAST video. " +
                            "Apps should enable core library desugaring when integrating this artifact " +
                            "if required by transitive AndroidX / IMA dependencies."
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

    val validateLiteNoVideoPublication by tasks.registering {
        dependsOn("assembleLiteNoVideoRelease")
        dependsOn("sourceLiteNoVideoReleaseJar")
        dependsOn("javaDocLiteNoVideoReleaseJar")
        dependsOn(tasks.named("generatePomFileForBidscubeSdkLiteNoVideoPublication"))
        doLast {
            validateBidscubePublication("bidscubeSdkLiteNoVideo", "bidscube-sdk-lite-no-video")
        }
    }

    val validateFullVideoPublication by tasks.registering {
        dependsOn("assembleFullVideoRelease")
        dependsOn("sourceFullVideoReleaseJar")
        dependsOn("javaDocFullVideoReleaseJar")
        dependsOn(tasks.named("generatePomFileForBidscubeSdkFullVideoPublication"))
        doLast {
            validateBidscubePublication("bidscubeSdkFullVideo", "bidscube-sdk-full-video")
        }
    }

    tasks.matching { it.name.startsWith("publishBidscubeSdkLiteNoVideoPublication", ignoreCase = true) }.configureEach {
        dependsOn(validateLiteNoVideoPublication)
    }
    tasks.matching { it.name.startsWith("publishBidscubeSdkFullVideoPublication", ignoreCase = true) }.configureEach {
        dependsOn(validateFullVideoPublication)
    }
}
