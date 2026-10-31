// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import java.security.MessageDigest

plugins {
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

spotless {
    kotlinGradle {
        target("**/*.gradle.kts")
        // Generated scripts stay out of the check even when the Gradle user home
        // lives in the checkout, as `verifyLicense` already assumes.
        targetExclude("**/build/**", ".gradle/**", ".gradle-user-home/**")
        ktlint()
    }
}

/*
 * Release identity of every deliverable, owned by the root build. A release
 * build passes the released version through the `releaseVersion` property. A
 * local build is not a release, so it identifies the commit the artifact was
 * produced from instead of claiming a version that was never published.
 *
 * The revision lookup never stops the build. An environment without the Git
 * executable, such as the container that runs the integration tests, produces
 * an unknown revision instead of a configuration failure.
 */
val gitRevision: String =
    (
        try {
            providers
                .exec {
                    workingDir(rootDir)
                    commandLine("git", "rev-parse", "HEAD")
                    isIgnoreExitValue = true
                }.standardOutput
                .asText
                .getOrElse("")
                .trim()
        } catch (e: Exception) {
            ""
        }
    ).ifEmpty { "unknown" }

val releaseVersion: String =
    providers.gradleProperty("releaseVersion").getOrElse("0.0.0-dev+${gitRevision.take(12)}")

/*
 * A Docker tag does not accept '+', so the build exposes the version once in
 * its normalized form. Every tag reads imageVersion; every manifest and image
 * label reads releaseVersion.
 */
val imageVersion: String = releaseVersion.replace('+', '-')

extra["gitRevision"] = gitRevision
extra["releaseVersion"] = releaseVersion
extra["imageVersion"] = imageVersion

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

/*
 * The release bundle carries every deliverable a release publishes, in the
 * repository paths the Dockerfiles and the Compose file already use, so an
 * extracted bundle is a Docker build context: `docker compose up --build`
 * assembles the images from the binaries the bundle carries.
 *
 * The bundle holds no shell script, no source checkout, and no credential: the
 * stack file carries the stamped identity, and `.env` carries the runtime
 * variables only.
 */
val releaseBundleDir = layout.buildDirectory.dir("release-bundle")

val stageReleaseBundle =
    tasks.register<Sync>("stageReleaseBundle") {
        group = "build"
        description = "Stages the release deliverables in build/release-bundle with their repository paths."
        dependsOn(
            ":backend:bootJar",
            ":commons:jvmJar",
            ":commons:exportJsPackage",
            ":frontend:frontendBuild",
            "documentation",
        )
        from("backend/build/libs/backend.jar") {
            into("backend/build/libs")
        }
        from("backend/Dockerfile") {
            into("backend")
        }
        from("commons/build/libs/commons-jvm.jar") {
            into("commons/build/libs")
        }
        from(project(":commons").layout.buildDirectory.dir("npm/sticky-notes-domain")) {
            into("commons/build/npm/sticky-notes-domain")
        }
        from(listOf("frontend/Dockerfile", "frontend/nginx.conf")) {
            into("frontend")
        }
        from("frontend/dist") {
            into("frontend/dist")
        }
        from(layout.buildDirectory.dir("docs")) {
            into("build/docs")
        }
        from(".dockerignore", "LICENSE")
        from("release/README.md") {
            rename { "README.md" }
        }
        // The documentation bundle is generated, so a Finder artifact from a
        // local build must not reach the published bytes.
        exclude(".DS_Store", "**/.DS_Store")
        into(releaseBundleDir)
    }

/*
 * The stack file and the two records are written after the staging, so they
 * describe the bytes that are actually delivered. The version is the one value
 * the root build owns; it is stamped into the stack file, which the checksums
 * cover, so the editable `.env` cannot change the identity a bundle claims. The
 * image references repeat its Docker-safe form.
 *
 * The `.env` is editable configuration, so it stays out of the checksums.
 */
abstract class WriteReleaseBundleRecords : DefaultTask() {
    @get:Internal
    abstract val bundleDirectory: DirectoryProperty

    @get:Input
    abstract val bundleVersion: Property<String>

    @get:Input
    abstract val bundleRevision: Property<String>

    @get:Input
    abstract val bundleImageVersion: Property<String>

    // The template stays in the repository: the bundle ships the generated
    // `.env` only, and does not need a second copy of the same variables.
    @get:InputFile
    abstract val envTemplate: RegularFileProperty

    @get:InputFile
    abstract val composeTemplate: RegularFileProperty

    @get:OutputFile
    val envFile: Provider<RegularFile>
        get() = bundleDirectory.file(".env")

    @get:OutputFile
    val composeFile: Provider<RegularFile>
        get() = bundleDirectory.file("compose.yaml")

    @get:OutputFile
    val manifestFile: Provider<RegularFile>
        get() = bundleDirectory.file("manifest.json")

    @get:OutputFile
    val checksumFile: Provider<RegularFile>
        get() = bundleDirectory.file("SHA256SUMS")

    @TaskAction
    fun write() {
        val root = bundleDirectory.get().asFile
        val envOut = envFile.get().asFile
        val composeOut = composeFile.get().asFile
        val manifestOut = manifestFile.get().asFile
        val checksumOut = checksumFile.get().asFile
        val template = envTemplate.get().asFile
        val composeTem = composeTemplate.get().asFile

        composeOut.writeText(
            composeTem
                .readText()
                .replace("\${STICKY_NOTES_VERSION:-0.0.0-dev}", "\"${bundleVersion.get()}\"")
                .replace("\${STICKY_NOTES_REVISION:-unknown}", "\"${bundleRevision.get()}\"")
                .replace(
                    "\${API_IMAGE:-sticky-notes-backend:local}",
                    "\${API_IMAGE:-sticky-notes-backend:${bundleImageVersion.get()}}",
                )
                .replace(
                    "\${WEB_IMAGE:-sticky-notes-web:local}",
                    "\${WEB_IMAGE:-sticky-notes-web:${bundleImageVersion.get()}}",
                ),
        )

        val envLines =
            template.readLines().filterNot { line ->
                val key = line.substringBefore('=').trim()
                key in setOf("API_IMAGE", "WEB_IMAGE", "STICKY_NOTES_VERSION", "STICKY_NOTES_REVISION")
            }

        envOut.writeText(envLines.joinToString("\n", postfix = "\n"))

        fun digestOf(file: File): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

        val payload =
            root
                .walkTopDown()
                .filter { it.isFile && it != envOut && it != manifestOut && it != checksumOut }
                .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
                .toList()

        val baseImages =
            listOf("backend/Dockerfile", "frontend/Dockerfile", "compose.yaml")
                .flatMap { path ->
                    Regex("""([\w./:@-]+)@sha256:([0-9a-f]{64})""")
                        .findAll(root.resolve(path).readText())
                        .map { it.groupValues[1] to it.groupValues[2] }
                }.distinct()

        check(baseImages.size >= 3) {
            "Expected pinned @sha256 digests in backend/Dockerfile, frontend/Dockerfile, and compose.yaml, " +
                "but found only: $baseImages"
        }

        val notes =
            listOf(
                "backend/build/libs/backend.jar is the only executable artifact: " +
                    "it embeds the Commons domain and its runtime dependencies.",
                "commons/ carries the shared domain as a JVM library and a Kotlin/JS package for consumers; the stack does not need it.",
                ".env carries runtime configuration and is editable, so it is not part of the checksums.",
                "The images are built on the target machine from the pinned base images above by the delivered Dockerfiles.",
            )

        manifestOut.writeText(
            buildString {
                appendLine("{")
                appendLine("""  "name": "sticky-notes",""")
                appendLine("""  "version": "${json(bundleVersion.get())}",""")
                appendLine("""  "imageVersion": "${json(bundleImageVersion.get())}",""")
                appendLine("""  "revision": "${json(bundleRevision.get())}",""")
                appendLine("""  "source": "https://github.com/lorenzotosi/sticky-notes",""")
                appendLine("""  "requirements": {""")
                appendLine("""    "dockerCompose": "2.19 or later",""")
                appendLine("""    "java": "not required to run the delivery stack"""")
                appendLine("  },")
                appendLine("""  "baseImages": [""")
                appendLine(
                    baseImages.joinToString(",\n") {
                        """    {"reference": "${json(it.first)}", "digest": "sha256:${it.second}"}"""
                    },
                )
                appendLine("  ],")
                appendLine("""  "notes": [""")
                appendLine(notes.joinToString(",\n") { """    "${json(it)}"""" })
                appendLine("  ],")
                appendLine("""  "files": [""")
                appendLine(
                    payload.joinToString(",\n") {
                        val path = it.relativeTo(root).invariantSeparatorsPath
                        """    {"path": "${json(path)}", "bytes": ${it.length()}, "sha256": "${digestOf(it)}"}"""
                    },
                )
                appendLine("  ]")
                appendLine("}")
            },
        )

        val checksummed =
            (
                payload.map { it.relativeTo(root).invariantSeparatorsPath to digestOf(it) } +
                    ("manifest.json" to digestOf(manifestOut))
            ).sortedBy { it.first }
        checksumOut.writeText(checksummed.joinToString("\n", postfix = "\n") { "${it.second}  ${it.first}" })
    }
}

val writeReleaseBundleRecords =
    tasks.register<WriteReleaseBundleRecords>("writeReleaseBundleRecords") {
        group = "build"
        description = "Writes the build identity, the manifest, and the checksum record of the release bundle."
        dependsOn(stageReleaseBundle)
        bundleDirectory.set(releaseBundleDir)
        bundleVersion.set(releaseVersion)
        bundleRevision.set(gitRevision)
        bundleImageVersion.set(imageVersion)
        composeTemplate.set(layout.projectDirectory.file("compose.yaml"))
        envTemplate.set(layout.projectDirectory.file(".env.example"))
    }

val releaseBundle =
    tasks.register("releaseBundle") {
        group = "build"
        description = "Assembles the versioned release bundle in build/release-bundle."
        dependsOn(stageReleaseBundle, writeReleaseBundleRecords)
    }

val packageReleaseZip =
    tasks.register<Zip>("packageReleaseZip") {
        group = "build"
        description = "Packages the release bundle into a reproducible ZIP archive."
        dependsOn(releaseBundle)
        from(releaseBundleDir)
        archiveFileName.set("sticky-notes-deliverables.zip")
        destinationDirectory.set(layout.projectDirectory)
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
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
        releaseBundle,
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

val startLocal =
    tasks.register<Exec>("startLocal") {
        group = "local stack"
        description = "Builds the delivery stack images and starts the local Compose stack once every service reports healthy."
        dependsOn("buildImages")
        workingDir = projectDir
        // shortcut: scripts/start-local.sh checks the Docker engine, so a missing engine is reported after the images are built.
        commandLine(shExecutable.get(), "scripts/start-local.sh")
    }

val containerSmoke =
    tasks.register<Exec>("containerSmoke") {
        group = "verification"
        description = "Runs the end-to-end smoke and lifecycle check against an isolated Docker Compose stack."
        dependsOn("buildImages", releaseBundle)
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
