import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("kapt") version "1.9.25"
    `maven-publish`
}

group = "io.github.viniciuskoiti"
version = "1.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/ViniciusKoiti/cbenef-integration")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    // =================== DEPENDÊNCIAS PRINCIPAIS ===================
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.apache.pdfbox:pdfbox:2.0.27")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

    compileOnly("org.springframework.boot:spring-boot-starter-cache")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")

    // Testes de integração
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:mockserver:1.19.3")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")

    // Coroutines Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// =================== CONFIGURAÇÃO DE TESTES ===================
tasks.withType<Test> {
    useJUnitPlatform()

    // Configuração específica para Kotest
    systemProperty("kotest.framework.classpath.scanning.config.disable", "false")

    // Para logs detalhados durante desenvolvimento
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// =================== PUBLISHING ===================
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ViniciusKoiti/cbenef-integration")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])

            pom {
                name.set("CBenef Integration Library")
                description.set("Biblioteca Kotlin/Spring Boot para integração com dados CBenef dos estados brasileiros")
                url.set("https://github.com/ViniciusKoiti/cbenef-integration")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("viniciuskoiti")
                        name.set("Vinícius Koiti")
                        email.set("viniciusnakahara@gmail.com")
                        url.set("https://github.com/ViniciusKoiti")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/ViniciusKoiti/cbenef-integration.git")
                    developerConnection.set("scm:git:ssh://github.com/ViniciusKoiti/cbenef-integration.git")
                    url.set("https://github.com/ViniciusKoiti/cbenef-integration")
                }
            }
        }
    }
}

tasks.jar {
    enabled = true
    archiveClassifier = ""
}

tasks.bootJar {
    enabled = false
}