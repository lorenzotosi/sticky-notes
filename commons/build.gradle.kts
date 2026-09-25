// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinx.serialization)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    source.setFrom("src/commonMain/kotlin", "src/commonTest/kotlin", "src/jvmMain/kotlin", "src/jsMain/kotlin")
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    jvm()
    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", "detekt")
}

tasks.register("browserDomainTest") {
    description = "Runs the domain tests in a headless browser environment."
    group = "verification"
    dependsOn("jsBrowserTest")
}

abstract class GenerateNpmPackageJson : DefaultTask() {
    @get:OutputFile
    abstract val packageJsonFile: RegularFileProperty

    @TaskAction
    fun generate() {
        packageJsonFile.get().asFile.writeText(
            """
            {
              "name": "sticky-notes-domain",
              "version": "1.0.0",
              "type": "module",
              "main": "./sticky-notes-commons.mjs",
              "module": "./sticky-notes-commons.mjs",
              "types": "./sticky-notes-commons.d.ts",
              "exports": {
                ".": {
                  "types": "./sticky-notes-commons.d.ts",
                  "import": "./sticky-notes-commons.mjs",
                  "default": "./sticky-notes-commons.mjs"
                }
              }
            }
            """.trimIndent() + "\n",
        )
    }
}

val generateNpmPackageJson =
    tasks.register<GenerateNpmPackageJson>("generateNpmPackageJson") {
        group = "build"
        description = "Genera il package.json per il modulo npm sticky-notes-domain"
        packageJsonFile.set(layout.buildDirectory.file("generated/npm/package.json"))
    }

val exportJsPackage =
    tasks.register<Sync>("exportJsPackage") {
        group = "build"
        description = "Esporta il pacchetto JS/ESM del dominio Kotlin per il frontend"
        dependsOn("jsProductionLibraryCompileSync", generateNpmPackageJson)

        from(tasks.named("jsProductionLibraryCompileSync"))
        from(generateNpmPackageJson.flatMap { it.packageJsonFile })
        into(layout.buildDirectory.dir("npm/sticky-notes-domain"))
    }
