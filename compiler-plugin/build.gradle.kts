import java.util.*
import upload.*

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("java")
    signing
    `maven-publish`
    id("com.jfrog.bintray")
}

version = Versions.project
description =
    "Kotlin compiler plugin that can generate a blocking bridge for calling suspend functions from Java with minimal effort"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType(JavaCompile::class.java) {
    options.encoding = "UTF8"
}

kotlin {
    sourceSets.all {
        target.compilations.all {
            kotlinOptions {
                freeCompilerArgs = freeCompilerArgs + "-Xjvm-default=enable"
                jvmTarget = "1.8"
            }
        }
        languageSettings.apply {
            enableLanguageFeature("InlineClasses")
            progressiveMode = true

            useExperimentalAnnotation("kotlin.Experimental")
            useExperimentalAnnotation("kotlin.RequiresOptIn")

            useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
            useExperimentalAnnotation("kotlin.experimental.ExperimentalTypeInference")
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
        }
    }

    sourceSets {
        getByName("test") {
            languageSettings.apply {
                //languageVersion = "1.4"
            }
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(project(":runtime"))
    implementation(project(":runtime"))

    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")

    kapt("com.google.auto.service:auto-service:1.0-rc6")
    compileOnly("com.google.auto.service:auto-service-annotations:1.0-rc6")

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.6")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))

    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.9")
    testImplementation("org.assertj:assertj-core:3.11.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.2.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.2.0")
}

tasks {
    "test"(Test::class) {
        useJUnitPlatform()
    }
}

// region PUBLISHING

tasks.register("ensureBintrayAvailable") {
    doLast {
        if (!Bintray.isBintrayAvailable(project)) {
            error("bintray isn't available. ")
        }
    }
}

if (Bintray.isBintrayAvailable(project)) {
    bintray {
        val keyProps = Properties()
        val keyFile = file("../keys.properties")
        if (keyFile.exists()) keyFile.inputStream().use { keyProps.load(it) }
        if (keyFile.exists()) keyFile.inputStream().use { keyProps.load(it) }

        user = Bintray.getUser(project)
        key = Bintray.getKey(project)
        setPublications("mavenJava")
        setConfigurations("archives")

        pkg.apply {
            repo = "kotlin-jvm-blocking-bridge"
            name = "kotlin-jvm-blocking-bridge"
            setLicenses("Apache-2.0")
            publicDownloadNumbers = true
            vcsUrl = "https://github.com/him188/kotlin-jvm-blocking-bridge"
        }
    }

    @Suppress("DEPRECATION")
    val sourcesJar by tasks.registering(Jar::class) {
        classifier = "sources"
        from(sourceSets.main.get().allSource)
    }

    publishing {
        /*
        repositories {
            maven {
                // change to point to your repo, e.g. http://my.org/repo
                url = uri("$buildDir/repo")
            }
        }*/
        publications {
            register("mavenJava", MavenPublication::class) {
                from(components["java"])

                groupId = rootProject.group.toString()
                artifactId = "kotlin-jvm-blocking-bridge"
                version = version

                pom.withXml {
                    val root = asNode()
                    root.appendNode("description", description)
                    root.appendNode("name", project.name)
                    root.appendNode("url", "https://github.com/him188/kotlin-jvm-blocking-bridge")
                    root.children().last()
                }

                artifact(sourcesJar.get())
            }
        }
    }
} else println("bintray isn't available. NO PUBLICATIONS WILL BE SET")


signing {
    setRequired(provider { gradle.taskGraph.hasTask("publish") })
    sign(publishing.publications)
}

// endregion