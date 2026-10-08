// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

plugins {
    // application
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
        palantirJavaFormat("2.98.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val integrationTestSourceSet =
    sourceSets.create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())

configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    // common
    implementation(project(":commons"))
    // jackson
    implementation(platform(libs.jackson.bom))
    // spring boot
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.webmvc)
    implementation(libs.spring.boot.mongodb)
    implementation(libs.spring.boot.actuator)
    // mongodb driver
    implementation(platform(libs.mongodb.driver.bom))
    // tests
    testImplementation(libs.spring.boot.test)

    constraints { // specific versions to avoid vulnerability in spring bom
        implementation(libs.logback.classic)
        implementation(libs.logback.core)
        implementation(libs.tomcat.core)
        implementation(libs.tomcat.el)
        implementation(libs.tomcat.websocket)
    }
    // testImplementation(libs.junit.jupiter)
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

val integrationTest =
    tasks.register<Test>("integrationTest") {
        group = "verification"
        description = "Runs persistence tests against MongoDB."
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        dependsOn(":testMongoUp")
        finalizedBy(":testMongoDown")
        useJUnitPlatform()
        shouldRunAfter(tasks.test)
    }

tasks.named("check") {
    dependsOn("spotlessCheck", integrationTest)
}
