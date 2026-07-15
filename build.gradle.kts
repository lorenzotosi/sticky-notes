// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

plugins {
    base
    kotlin("multiplatform") version "2.2.20" apply false
}

tasks.register("fullTest") {
    group = "verification"
    description = "Runs tests for Kotlin, Java, and Vue."
    dependsOn(":commons:allTests", ":backend:test", ":frontend:frontendTest")
}

tasks.register("fullBuild") {
    group = "build"
    description = "Builds every deliverable from one Gradle entry point."
    dependsOn("fullTest", "documentation", ":commons:build", ":backend:build", ":frontend:frontendBuild")
}

tasks.register<Sync>("documentation") {
    group = "documentation"
    description = "Builds the versioned documentation artifact."
    from("docs")
    into(layout.buildDirectory.dir("docs"))
}

tasks.register("verifyLicense") {
    group = "verification"
    description = "Checks SPDX headers in source and configuration files."
    val checkedFiles = fileTree(projectDir) {
        include("**/*.kt", "**/*.java", "**/*.kts", "**/*.js", "**/*.vue", "**/*.md", "**/*.yml", "**/*.yaml", "**/*.dockerignore", "Dockerfile*")
        exclude("**/build/**", "**/node_modules/**", "**/dist/**")
    }
    inputs.files(checkedFiles)
    doLast {
        val missing = checkedFiles.files.filterNot { it.readText().contains("SPDX-License-Identifier: MIT") }
        check(missing.isEmpty()) { "Files without SPDX header:\n${missing.joinToString("\n")}" }
    }
}

tasks.named("check") {
    dependsOn("verifyLicense", "fullTest")
}
