import java.net.URI

val gitRemoteUrl: String = try {
    val process = ProcessBuilder("git", "config", "--get", "remote.origin.url").start()
    var url = process.inputStream.bufferedReader().readText().trim()
    if (url.startsWith("git@")) {
        url = url.replace(":", "/").replace("git@", "https://")
    }
    if (url.endsWith(".git")) {
        url = url.removeSuffix(".git")
    }
    if (url.isEmpty()) "https://github.com/aczeccssa/SoulLogger" else url
} catch (e: Exception) {
    "https://github.com/aczeccssa/SoulLogger"
}

val gitBranch: String = try {
    val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD").start()
    val branch = process.inputStream.bufferedReader().readText().trim()
    if (branch.isEmpty() || branch == "HEAD") {
        val commitProcess = ProcessBuilder("git", "rev-parse", "HEAD").start()
        val commit = commitProcess.inputStream.bufferedReader().readText().trim()
        if (commit.isEmpty()) {
            System.getenv("GITHUB_REF_NAME") ?: System.getenv("GITHUB_SHA") ?: "main"
        } else {
            commit
        }
    } else {
        branch
    }
} catch (e: Exception) {
    System.getenv("GITHUB_REF_NAME") ?: System.getenv("GITHUB_SHA") ?: "main"
}

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.2.0" apply false
    id("org.jetbrains.dokka") version "2.1.0"
}

// Kotlin version = 2.2.21

group = "com.lestere.opensource"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.0")
    implementation("org.jetbrains.kotlinx:dataframe:0.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Logging infrastructure
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Configuration
    implementation("com.typesafe:config:1.4.3")

    // Metrics (optional)
    implementation("io.micrometer:micrometer-core:1.12.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}

dokka {
    dokkaPublications.html {
        moduleName.set("SoulLogger")
        moduleVersion.set(project.version.toString())
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        failOnWarning.set(false)
        suppressObviousFunctions.set(true)
        offlineMode.set(false)
        includes.from("README.md")
    }

    dokkaSourceSets.main {
        documentedVisibilities.set(setOf(
            org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public,
            org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Protected,
            org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Internal
        ))
        reportUndocumented.set(false)
        skipEmptyPackages.set(true)
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URI("$gitRemoteUrl/blob/$gitBranch/core/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}