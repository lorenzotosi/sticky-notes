// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

plugins {
    // application
    java
    alias(libs.plugins.spotless)
    alias(libs.plugins.spring.boot)
    jacoco
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
}

/*
 * A release build passes the released version through the `releaseVersion`
 * property. A local build is not a release, so it identifies the commit the
 * artifact was produced from instead of claiming a version that was never
 * published.
 */
val gitRevisionOutput: String =
    providers
        .exec {
            workingDir(rootDir)
            commandLine("git", "rev-parse", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput
        .asText
        .getOrElse("")
        .trim()

val gitRevision: String = gitRevisionOutput.ifEmpty { "unknown" }

val releaseVersion: String =
    providers.gradleProperty("releaseVersion").getOrElse("0.0.0-dev+${gitRevision.take(12)}")

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

tasks.bootJar {
    // The archive name is fixed, so the delivery pipeline, the documentation and
    // the container build always find the same artifact whatever version the
    // build carries.
    archiveFileName.set("backend.jar")
    manifest {
        attributes(
            "Implementation-Title" to "Sticky Notes backend",
            "Implementation-Version" to releaseVersion,
            "Implementation-Revision" to gitRevision,
        )
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

val filteredMainClasses =
    sourceSets.main.get().output.asFileTree.matching {
        exclude("stickynotes/Main.class")
    }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(filteredMainClasses)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(filteredMainClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
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
    dependsOn(
        "spotlessCheck",
        tasks.jacocoTestReport,
        tasks.jacocoTestCoverageVerification,
        integrationTest,
    )
}

/*
 * The runtime image is assembled from the JAR produced by the build, so the
 * image carries the application only: no Gradle runtime, no sources, no build
 * cache. The version and the commit are passed as build arguments and stored as
 * image labels, so a candidate image identifies the source it was produced from.
 */
val backendImageTag = "sticky-notes-backend:${releaseVersion.replace('+', '-')}"

val dockerImage =
    tasks.register<Exec>("dockerImage") {
        group = "build"
        description = "Builds the non-root backend runtime image $backendImageTag from the verified JAR."
        dependsOn(tasks.bootJar)
        workingDir(rootDir)
        commandLine(
            "docker",
            "build",
            "--file",
            "backend/Dockerfile",
            "--tag",
            backendImageTag,
            "--build-arg",
            "VERSION=$releaseVersion",
            "--build-arg",
            "REVISION=$gitRevision",
            ".",
        )
    }
