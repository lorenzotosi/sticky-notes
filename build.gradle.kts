// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

plugins {
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

spotless {
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", ".gradle/**")
        ktlint()
    }
}

subprojects {
    val lockableConfigurations =
        setOf(
            "compileClasspath",
            "runtimeClasspath",
            "testCompileClasspath",
            "testRuntimeClasspath",
            "jvmCompileClasspath",
            "jvmRuntimeClasspath",
            "jvmTestCompileClasspath",
            "jvmTestRuntimeClasspath",
        )

    configurations.configureEach {
        if (name in lockableConfigurations) {
            resolutionStrategy.activateDependencyLocking()
        }
    }
}

tasks.register<Sync>("documentation") {
    group = "documentation"
    description = "Builds the versioned documentation artifact."
    from("docs")
    into(layout.buildDirectory.dir("docs"))
}

val verifyLicense =
    tasks.register("verifyLicense") {
        group = "verification"
        description = "Checks SPDX headers in source and configuration files."
        val checkedFiles =
            fileTree(projectDir) {
                include(
                    "**/*.kt",
                    "**/*.java",
                    "**/*.kts",
                    "**/*.js",
                    "**/*.vue",
                    "**/*.css",
                    "**/*.sh",
                    "**/*.md",
                    "**/*.yml",
                    "**/*.yaml",
                    "**/*.dockerignore",
                    "Dockerfile*",
                )
                exclude(
                    ".gradle/**",
                    ".gradle-user-home/**",
                    ".kotlin/**",
                    "**/build/**",
                    "**/node_modules/**",
                    "**/dist/**",
                    "gradlew*",
                    "**/gradle-wrapper.properties",
                )
            }
        inputs.files(checkedFiles)
        doLast {
            val missing = checkedFiles.files.filterNot { it.readText().contains("SPDX-License-Identifier: MIT") }
            check(missing.isEmpty()) { "Files without SPDX header:\n${missing.joinToString("\n")}" }
        }
    }

tasks.register("fullTest") {
    group = "verification"
    description = "Runs tests across commons, backend, and frontend."
    dependsOn(":commons:check", ":backend:test", ":frontend:frontendTest")
}

tasks.named("check") {
    dependsOn(
        "spotlessCheck",
        verifyLicense,
        ":commons:check",
        ":backend:check",
        ":frontend:frontendCheck",
    )
}

tasks.register("fullBuild") {
    group = "build"
    description = "Builds and verifies every deliverable from a single Gradle entry point."
    dependsOn(
        "check",
        "documentation",
        ":commons:build",
        ":backend:build",
        ":frontend:frontendBuild",
    )
}
