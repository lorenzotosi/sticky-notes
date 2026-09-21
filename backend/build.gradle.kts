// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

plugins {
    application
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
}

dependencies {
    implementation(project(":commons"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("stickynotes.Main")
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.httpserver")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.httpserver"))
}

tasks.test {
    useJUnitPlatform()
}
