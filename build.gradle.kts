import net.researchgate.release.ReleaseExtension

val ktorVersion: String by project

plugins {
    kotlin("multiplatform") version "1.7.0"
    id("maven-publish")
    id("net.researchgate.release") version "3.0.2"
}

group = "com.bscharm"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api("io.ktor:ktor-server-websockets:$ktorVersion")
                api("com.expediagroup:graphql-kotlin-server:6.4.1")

                implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-server-auth:$ktorVersion")
                implementation("io.ktor:ktor-server:$ktorVersion")
                implementation("org.slf4j:slf4j-api:[1.7,)")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                compileOnly("io.ktor:ktor-server:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-client-websockets:$ktorVersion")
                implementation("io.ktor:ktor-server-test-host:$ktorVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:[5.6,)")
                implementation("org.assertj:assertj-core:3.23.1")
                implementation("org.jetbrains.kotlin:kotlin-test:1.7.0")
                implementation("org.junit.jupiter:junit-jupiter-params:[5.6,)")
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/bscharm/ktor-server-graphql")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

configure<ReleaseExtension> {
    ignoredSnapshotDependencies.set(listOf("net.researchgate:gradle-release"))
    with(git) {
        requireBranch.set("")
    }

    with(svn) {
        username.set(System.getenv("GITHUB_ACTOR"))
        password.set(System.getenv("GITHUB_TOKEN"))
    }
}

tasks {
    afterReleaseBuild {
        dependsOn(":publish")
    }
}
