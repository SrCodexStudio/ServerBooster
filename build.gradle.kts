import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.srcodex"
version = "2.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Paper API 1.20.4 (Java 17 compatible, works on 1.21 at runtime)
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // Adventure API for component handling (Paper provides these natively since 1.16.5)
    compileOnly("net.kyori:adventure-api:4.15.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.15.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.15.0")

    // bStats
    implementation("org.bstats:bstats-bukkit:3.0.2")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("ServerBooster-${project.version}.jar")

        relocate("kotlin", "dev.srcodex.serverbooster.libs.kotlin")
        relocate("kotlinx", "dev.srcodex.serverbooster.libs.kotlinx")
        relocate("org.bstats", "dev.srcodex.serverbooster.libs.bstats")
        // Note: Adventure API is NOT relocated - Paper provides it natively

        minimize {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to project.name
            )
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
