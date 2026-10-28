// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

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
            "integrationTestCompileClasspath",
            "integrationTestRuntimeClasspath",
        )

    configurations.configureEach {
        if (name in lockableConfigurations) {
            resolutionStrategy.activateDependencyLocking()
        }
    }
}

tasks.register<GradleBuild>("updateDependencyLocks") {
    group = "build setup"
    description = "Updates dependency lockfiles for every Gradle subproject."
    dir = projectDir
    startParameter =
        gradle.startParameter.newBuild().apply {
            isWriteDependencyLocks = true
        }
    tasks = subprojects.map { "${it.path}:dependencies" }
}

tasks.register<Sync>("documentation") {
    group = "documentation"
    description = "Builds the versioned documentation artifact."
    dependsOn(":commons:dokkaGeneratePublicationHtml")
    from("docs") {
        exclude(".DS_Store", "**/.DS_Store")
    }
    from(project(":commons").layout.buildDirectory.dir("dokka/html")) {
        into("api")
        exclude(".DS_Store", "**/.DS_Store")
    }
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
                    "**/coverage/**",
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

tasks.register<Exec>("testMongoUp") {
    group = "verification"
    description = "Creates and starts MongoDB for integration tests, waiting until it is healthy."
    workingDir = projectDir
    commandLine(
        "docker",
        "compose",
        "-f",
        "compose.test.yaml",
        "up",
        "--detach",
        "--wait",
        "--wait-timeout",
        "120",
        "mongo-test",
    )
}

tasks.register<Exec>("testMongoDown") {
    group = "verification"
    description = "Stops and removes the isolated Docker Compose test environment."
    workingDir = projectDir
    commandLine("docker", "compose", "-f", "compose.test.yaml", "down")
}

tasks.register("fullTest") {
    group = "verification"
    description = "Runs tests across commons, backend, and frontend."
    dependsOn(":commons:check", ":backend:test", ":backend:integrationTest", ":frontend:frontendTest")
}

val prepareCommit =
    tasks.register("prepareCommit") {
        group = "verification"
        description = "Formats code and verifies SPDX license headers across all projects."
        dependsOn("formatCode", "verifyLicense")
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

tasks.register("buildImages") {
    group = "build"
    description = "Builds the container images used by the local Compose stack."
    dependsOn(":backend:dockerImage", ":frontend:buildWebImage")
}

tasks.register("formatCode") {
    group = "build"
    description = "Formats Kotlin, Java, Gradle, and frontend code."
    dependsOn(
        "spotlessApply",
        ":commons:spotlessApply",
        ":backend:spotlessApply",
        ":frontend:frontendFormat",
    )
}

val defaultShExecutable =
    if (System.getProperty("os.name").lowercase().contains("win")) {
        val gitSh = file("C:/Program Files/Git/bin/sh.exe")
        if (gitSh.exists()) gitSh.absolutePath else "sh"
    } else {
        "sh"
    }
val shExecutable = providers.environmentVariable("SH_EXECUTABLE").orElse(defaultShExecutable)

val containerSmoke =
    tasks.register<Exec>("containerSmoke") {
        group = "verification"
        description = "Runs the end-to-end smoke and lifecycle check against an isolated Docker Compose stack."
        dependsOn("buildImages")
        workingDir = projectDir
        commandLine(shExecutable.get(), "scripts/smoke-test.sh")
    }

val backupRestoreVerify =
    tasks.register<Exec>("backupRestoreVerify") {
        group = "verification"
        description = "Verifies the backup/restore roundtrip against an isolated Docker Compose stack."
        dependsOn("buildImages")
        workingDir = projectDir
        commandLine(shExecutable.get(), "scripts/backup-restore-verify.sh")
    }

containerSmoke.configure {
    mustRunAfter(tasks.named("fullBuild"))
}

backupRestoreVerify.configure {
    mustRunAfter(tasks.named("fullBuild"), containerSmoke)
}

val frontendE2eTask = evaluationDependsOn(":frontend").tasks.named("frontendE2e")

frontendE2eTask.configure {
    mustRunAfter(tasks.named("fullBuild"), containerSmoke, backupRestoreVerify)
}

tasks.register("fullVerify") {
    group = "verification"
    description = "Runs the full build, MongoDB integration tests, container lifecycle checks, backup and restore, and browser E2E."
    dependsOn("fullBuild", containerSmoke, backupRestoreVerify, frontendE2eTask)
}
