plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "3.2.0"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.lestere.opensource"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // SoulLogger - reference local core module
    implementation(project(":core"))

    // Kotlinx datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    // Ktor server
    implementation("io.ktor:ktor-server-core:3.2.0")
    implementation("io.ktor:ktor-server-netty:3.2.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.0")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.2.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("com.lestere.opensource.soullogger.sample.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}
