// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    jvm()
    js {
        browser()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
