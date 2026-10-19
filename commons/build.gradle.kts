// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinx.serialization)
    jacoco
}

dokka {
    dokkaPublications.html {
        moduleName.set("Sticky Notes domain")
    }
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

val jvmTestTask = tasks.named<Test>("jvmTest")

val filteredCommonsClasses =
    fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main")) {
        exclude(
            "**/*\$\$serializer.class",
            "**/*\$serializer.class",
            "**/*Serializer*.class",
        )
    }

val jvmJacocoReport =
    tasks.register<JacocoReport>("jvmJacocoReport") {
        group = "verification"
        description = "Generates JaCoCo XML and HTML coverage reports for the JVM domain target."
        dependsOn(jvmTestTask)

        executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
        classDirectories.setFrom(filteredCommonsClasses)
        sourceDirectories.setFrom(
            files(
                "src/commonMain/kotlin",
                "src/jvmMain/kotlin",
            ),
        )

        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

val jvmJacocoCoverageVerification =
    tasks.register<JacocoCoverageVerification>("jvmJacocoCoverageVerification") {
        group = "verification"
        description = "Verifies minimum line coverage threshold on the shared domain JVM classes."
        dependsOn(jvmJacocoReport)

        executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
        classDirectories.setFrom(filteredCommonsClasses)
        sourceDirectories.setFrom(
            files(
                "src/commonMain/kotlin",
                "src/jvmMain/kotlin",
            ),
        )

        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

abstract class VerifyDomainBoundaries : DefaultTask() {
    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val buildScriptFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val forbiddenImportRegex =
            Regex("""^\s*import\s+(java\.|javax\.|jdk\.|org\.springframework\.|org\.mongodb\.)""")
        val violations = mutableListOf<String>()

        sourceFiles.files.forEach { sourceFile ->
            sourceFile.readLines().forEachIndexed { index, line ->
                if (forbiddenImportRegex.containsMatchIn(line)) {
                    violations.add("${sourceFile.name}:${index + 1}: forbidden import '$line'")
                }
            }
        }

        val forbiddenBuildDependencyRegex =
            Regex("""(org\.springframework|org\.mongodb|spring-boot|mongodb-driver)""")
        buildScriptFile.get().asFile.readLines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("//") &&
                !trimmed.startsWith("Regex") &&
                forbiddenBuildDependencyRegex.containsMatchIn(trimmed)
            ) {
                violations.add("build.gradle.kts:${index + 1}: forbidden infrastructure reference '$trimmed'")
            }
        }

        check(violations.isEmpty()) {
            "Domain boundary violations detected:\n${violations.joinToString("\n")}"
        }
    }
}

val verifyDomainBoundaries =
    tasks.register<VerifyDomainBoundaries>("verifyDomainBoundaries") {
        group = "verification"
        description = "Verifies that commons does not depend on infrastructure frameworks or import JVM APIs in commonMain."
        sourceFiles.from(
            fileTree("src/commonMain") {
                include("**/*.kt")
            },
        )
        buildScriptFile.set(layout.projectDirectory.file("build.gradle.kts"))
    }

tasks.named("check") {
    dependsOn(
        "spotlessCheck",
        "detekt",
        verifyDomainBoundaries,
        jvmJacocoReport,
        jvmJacocoCoverageVerification,
    )
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
              "types": "./sticky-notes-commons.d.mts",
              "exports": {
                ".": {
                  "types": "./sticky-notes-commons.d.mts",
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
        description = "Generate package.json for npm module sticky-notes-domain"
        packageJsonFile.set(layout.buildDirectory.file("generated/npm/package.json"))
    }

val exportJsPackage =
    tasks.register<Sync>("exportJsPackage") {
        group = "build"
        description = "Exports Kotlin domain JS/ESM package for the frontend"
        dependsOn("jsProductionLibraryCompileSync", generateNpmPackageJson)

        from(tasks.named("jsProductionLibraryCompileSync"))
        from(generateNpmPackageJson.flatMap { it.packageJsonFile })
        into(layout.buildDirectory.dir("npm/sticky-notes-domain"))
    }
