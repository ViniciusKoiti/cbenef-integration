plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("kapt") version "1.9.25"
    `maven-publish`
}

group = "io.github.viniciuskoiti"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")  // Para @Min, @Max

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.apache.pdfbox:pdfbox:2.0.27")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    compileOnly("org.springframework.boot:spring-boot-starter-cache")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("CBenef Integration Library")
                description.set("Biblioteca para integração com dados CBenef dos estados brasileiros")
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