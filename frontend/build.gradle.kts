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
        description = "Installa le dipendenze npm con risoluzione deterministica da lockfile"
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
        description = "Esegue il linting statico del frontend"
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
        description = "Esegue la suite di test unitari frontend"
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
        description = "Aggrega i controlli di verifica del frontend"
        dependsOn(frontendLint, frontendTest)
    }

tasks.register<Exec>("frontendBuild") {
    group = "build"
    description = "Compila gli asset di produzione tramite Vite"
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
