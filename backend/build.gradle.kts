// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

plugins {
    //application
    java
    alias(libs.plugins.spotless)
    alias(libs.plugins.spring.boot)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    implementation(project(":commons"))
    testImplementation(platform(libs.junit.bom))
    implementation(platform(libs.jackson.bom))
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.mongodb.driver.bom))
    implementation(libs.spring.boot.webmvc)
    implementation(libs.spring.boot.mongodb)
    implementation(libs.spring.boot.actuator)
    testImplementation(libs.spring.boot.test)
    constraints { // specific versions to avoid vulnerability in spring bom
        implementation(libs.logback.classic)
        implementation(libs.logback.core)
        implementation(libs.tomcat.core)
        implementation(libs.tomcat.el)
        implementation(libs.tomcat.websocket)
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/*
application {
    mainClass.set("stickynotes.Main")
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.httpserver")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.httpserver"))
}
*/

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
