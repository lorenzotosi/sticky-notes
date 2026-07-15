// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sticky-notes"
include(":commons", ":backend", ":frontend")
