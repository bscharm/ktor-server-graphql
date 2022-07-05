plugins {
    kotlin("multiplatform") version "1.7.0"
    id("maven-publish")
    id("net.researchgate.release") version "3.0.0"
}

group = "com.arrivehealth"

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
                api("com.expediagroup:graphql-kotlin-server:[6.0.0-alpha.0,)")
                api("io.ktor:ktor-server-websockets:[2.0.0,)")
                implementation("io.ktor:ktor-server:[2.0.0,)")
                implementation("io.ktor:ktor-server-content-negotiation-jvm:[2.0.0,)")
                implementation("io.ktor:ktor-serialization-jackson-jvm:[2.0.0,)")
                implementation("org.slf4j:slf4j-api:[1.7,)")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                compileOnly("io.ktor:ktor-server:[2.0.0,)")

                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:[2.13,)")
                implementation("io.ktor:ktor-client-content-negotiation:[2.0.0,)")
                implementation("io.ktor:ktor-client-websockets:[2.0.0,)")
                implementation("io.ktor:ktor-server-test-host:[2.0.0,)")
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
            url = uri("https://maven.pkg.github.com/rxrevu/ktor-server-graphql")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

release {
    git {
        requireBranch.set("main")
    }
    svn {
        username.set(System.getenv("GITHUB_ACTOR"))
        password.set(System.getenv("GITHUB_TOKEN"))
    }
}

tasks {
    afterReleaseBuild {
        dependsOn(":publish")
    }
}
