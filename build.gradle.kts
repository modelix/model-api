
plugins {
    `maven-publish`
    val kotlinVersion: String by System.getProperties()
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0" apply false
    id("com.palantir.git-version") version "0.15.0"
}

group = "org.modelix"
description = "API to access models stored in Modelix"

version = computeVersion()
println("Version: $version")

fun computeVersion(): Any {
    val versionFile = file("version.txt")
    val gitVersion: groovy.lang.Closure<String> by extra
    var version = if (versionFile.exists()) versionFile.readText().trim() else gitVersion()
    if (!versionFile.exists() && !"true".equals(project.findProperty("ciBuild"))) {
        version = "$version-SNAPSHOT"
    }
    return version
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

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "maven-publish")
    version = rootProject.version
    group = rootProject.group
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
}

