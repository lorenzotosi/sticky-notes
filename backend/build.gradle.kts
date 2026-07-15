// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi

plugins {
    application
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation(project(":commons"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
