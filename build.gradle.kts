plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("maven-publish")
    id("signing")
}

group = "com.orbitz.consul"
version = "1.5.4-SNAPSHOT"
description = "Consul Client for Java"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

// Configure annotation processing
tasks.withType<JavaCompile> {
    // Enable annotation processing
    options.annotationProcessorGeneratedSourcesDirectory = file("${buildDir}/generated/sources/annotationProcessor/java/${name}")
}

repositories {
    mavenCentral()
}

// Define a resolvable test configuration
configurations {
    create("testImplementationResolvable") {
        extendsFrom(configurations.testImplementation.get())
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

dependencies {
    // Direct dependency for Immutables annotation processor
    annotationProcessor("org.immutables:value:2.10.1")
    compileOnly("org.immutables:value:2.10.1")

    // Add missing dependencies
    api("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
    api("com.squareup.okio:okio:2.10.0")
    api("com.squareup.okio:okio-jvm:2.10.0")

    // Use version catalog for dependency management
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.jackson)
    implementation(libs.okhttp)
    implementation(libs.guava)
    implementation(libs.findbugs.jsr305)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.guava)
    implementation(libs.commons.lang3)
    implementation(libs.slf4j.api)

    testImplementation(libs.logback.classic)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junitparams)
    testImplementation(libs.commons.codec)
    testImplementation(libs.retrofit.mock)
    testImplementation(libs.testcontainers)
    testCompileOnly("org.immutables:value:2.10.1")
    testAnnotationProcessor("org.immutables:value:2.10.1")
}

// Add integration test source set and configure generated sources
sourceSets {
    main {
        java {
            srcDir("${buildDir}/generated/sources/annotationProcessor/java/main")
        }
    }
    test {
        java {
            srcDir("${buildDir}/generated/sources/annotationProcessor/java/test")
        }
    }
    create("itest") {
        java {
            srcDir("src/itest/java")
            srcDir("${buildDir}/generated/sources/annotationProcessor/java/itest")
        }
        resources {
            srcDir("src/itest/resources")
        }
        compileClasspath += sourceSets.main.get().output + configurations.testImplementation.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.testImplementation.get()
    }
}

// Add dependencies for integration tests
dependencies {
    "itestImplementation"(sourceSets.main.get().output)
    "itestImplementation"(sourceSets.test.get().output)
    // Use specific dependencies instead of configurations
    "itestImplementation"(libs.junit)
    "itestImplementation"(libs.mockito.core)
    "itestImplementation"(libs.junitparams)
    "itestImplementation"(libs.commons.codec)
    "itestImplementation"(libs.retrofit.mock)
    "itestImplementation"(libs.testcontainers)
    "itestImplementation"(libs.logback.classic)
    "itestCompileOnly"("org.immutables:value:2.10.1")
    "itestAnnotationProcessor"("org.immutables:value:2.10.1")
}

// Create integration test task
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["itest"].output.classesDirs
    classpath = sourceSets["itest"].runtimeClasspath
}

// Create shaded jar
tasks.register<Jar>("shadedJar") {
    archiveClassifier.set("shaded")
    from(sourceSets.main.get().output)

    // Include dependencies in the jar
    from({
        configurations.runtimeClasspath.get()
            .filter { !it.name.contains("slf4j-api") && !it.name.contains("immutables") }
            .map { if (it.isDirectory) it else zipTree(it) }
    })

    // Relocate packages (similar to maven-shade-plugin)
    // Note: This is a simplified approach. For proper relocation, 
    // consider using the Shadow plugin instead.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Configure publishing
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifact(tasks["shadedJar"])

            pom {
                name.set("consul-client")
                description.set("Consul Client for Java")
                url.set("https://github.com/OrbitzWorldwide/consul-client")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("rickfast")
                        name.set("Rick Fast")
                        email.set("rick.t.fast@gmail.com")
                    }
                }

                scm {
                    url.set("scm:git@github.com:OrbitzWorldwide/consul-client.git")
                    connection.set("scm:git@github.com:OrbitzWorldwide/consul-client.git")
                    developerConnection.set("scm:git@github.com:OrbitzWorldwide/consul-client.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "ossrh"
            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

// Configure signing
signing {
    setRequired({
        !version.toString().endsWith("SNAPSHOT") && gradle.taskGraph.hasTask("publish")
    })
    sign(publishing.publications["mavenJava"])
}

// Configure javadoc
tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    isFailOnError = false
}
