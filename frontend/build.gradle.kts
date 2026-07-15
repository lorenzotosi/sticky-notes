// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

val npm = providers.environmentVariable("NPM_EXECUTABLE").orElse("npm")

tasks.register<Exec>("frontendInstall") {
    group = "frontend"
    workingDir = projectDir
    commandLine(npm.get(), "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

tasks.register<Exec>("frontendTest") {
    group = "verification"
    dependsOn("frontendInstall", "frontendLint")
    workingDir = projectDir
    commandLine(npm.get(), "run", "test")
}

tasks.register<Exec>("frontendLint") {
    group = "verification"
    dependsOn("frontendInstall")
    workingDir = projectDir
    commandLine(npm.get(), "run", "lint")
}

tasks.register<Exec>("frontendBuild") {
    group = "build"
    dependsOn("frontendTest")
    workingDir = projectDir
    commandLine(npm.get(), "run", "build")
    inputs.dir("src")
    inputs.file("package.json")
    outputs.dir("dist")
}
