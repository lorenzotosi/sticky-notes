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
