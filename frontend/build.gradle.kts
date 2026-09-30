// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

val defaultNpmExecutable =
    if (System.getProperty("os.name").lowercase().contains("win")) {
        "npm.cmd"
    } else {
        "npm"
    }
val npm = providers.environmentVariable("NPM_EXECUTABLE").orElse(defaultNpmExecutable)

val exportJsPackageTask = evaluationDependsOn(":commons").tasks.named("exportJsPackage")
val domainPackageDir = project(":commons").layout.buildDirectory.dir("npm/sticky-notes-domain")

val frontendInstall =
    tasks.register<Exec>("frontendInstall") {
        group = "frontend"
        description = "Install npm dependencies from lockfile"
        dependsOn(exportJsPackageTask)
        workingDir = projectDir
        commandLine(npm.get(), "ci")
        inputs.files("package.json", "package-lock.json")
        inputs.dir(domainPackageDir)
        outputs.dir("node_modules")
    }

val frontendLint =
    tasks.register<Exec>("frontendLint") {
        group = "verification"
        description = "Execute lint"
        dependsOn(frontendInstall)
        workingDir = projectDir
        commandLine(npm.get(), "run", "lint")
        inputs.dir("src")
        inputs.file("eslint.config.js")
        inputs.file("package.json")
    }

val frontendTest =
    tasks.register<Exec>("frontendTest") {
        group = "verification"
        description = "Execute test suits"
        dependsOn(frontendInstall)
        workingDir = projectDir
        commandLine(npm.get(), "run", "test")
        inputs.dir("src")
        inputs.dir(domainPackageDir)
        inputs.file("package.json")
    }

val frontendCheck =
    tasks.register("frontendCheck") {
        group = "verification"
        description = "Aggregate frontend checks"
        dependsOn(frontendLint, frontendTest)
    }

val frontendFormat =
    tasks.register<Exec>("frontendFormat") {
        group = "build"
        description = "Formats frontend code with ESLint auto-fix"
        dependsOn(frontendInstall)
        workingDir = projectDir
        commandLine(npm.get(), "run", "lint:fix")
        inputs.dir("src")
        inputs.file("eslint.config.js")
        inputs.file("package.json")
    }

tasks.register<Exec>("frontendBuild") {
    group = "build"
    description = "Compile production assets w Vite"
    dependsOn(frontendInstall, frontendCheck)
    workingDir = projectDir
    commandLine(npm.get(), "run", "build")

    inputs.dir("src")
    inputs.dir(domainPackageDir)
    inputs.file("index.html")
    inputs.file("vite.config.js")
    inputs.file("package.json")
    inputs.file("package-lock.json")

    outputs.dir("dist")
}
