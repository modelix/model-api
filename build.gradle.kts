
plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.multiplatform") version "1.6.21"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
    id("com.palantir.git-version") version "0.15.0"
}

configurations {
    ktlint
}

group = "org.modelix"
description = "API to access models stored in Modelix"

version = computeVersion()
println("Version: $version")

fun computeVersion(): Any {
    val versionFile = file("version.txt")
    val gitVersion: groovy.lang.Closure<String> by extra
    var version = if (versionFile.exists()) versionFile.readText().trim() else gitVersion()
    if (!"true".equals(project.findProperty("ciBuild"))) {
        version = "$version-SNAPSHOT"
    }
    return version
}

repositories {
    mavenCentral()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "11"
    }
}

ktlint {
    disabledRules.add("no-wildcard-imports")
    outputToConsole.set(true)
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

kotlin {
    jvm()
    js(BOTH) {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10000"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging:2.1.21")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

fun getGithubCredentials(): Pair<String, String>? {
    if (project.hasProperty("gpr.user") && project.hasProperty("gpr.key")) {
        return (project.findProperty("gpr.user")?.toString() ?: "") to (project.findProperty("gpr.key")?.toString() ?: "")
    }

    if (System.getenv("GITHUB_ACTOR") != null && System.getenv("GITHUB_TOKEN") != null) {
        return System.getenv("GITHUB_ACTOR") to System.getenv("GITHUB_TOKEN")
    }

    return null
}

publishing {
    repositories {
        if (project.hasProperty("artifacts.itemis.cloud.user")) {
            maven {
                name = "itemisNexus3"
                url = if (version.toString().contains("SNAPSHOT"))
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-snapshots/")
                else
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-releases/")
                credentials {
                    username = project.findProperty("artifacts.itemis.cloud.user").toString()
                    password = project.findProperty("artifacts.itemis.cloud.pw").toString()
                }
            }
        }
        val githubCredentials = getGithubCredentials()
        if (githubCredentials != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/modelix/model-api")
                credentials {
                    username = githubCredentials.first
                    password = githubCredentials.second
                }
            }
        }
    }
}
