// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

/*
 * The build compiles with the JDK 21 toolchain. When the machine does not
 * provide it, Gradle downloads it from the Foojay Disco catalogue, so a new
 * machine needs no manual JDK installation.
 */
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sticky-notes"
include(":commons", ":backend", ":frontend")
