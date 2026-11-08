plugins {
    id("moby.java-conventions")
    id("org.springframework.boot")
    jacoco
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    implementation(platform(lib("spring-boot-bom")))
    implementation(platform(lib("spring-cloud-bom")))
    implementation(platform(lib("spring-modulith-bom")))
    annotationProcessor(platform(lib("spring-boot-bom")))
    testImplementation(platform(lib("spring-boot-bom")))
    testImplementation(platform(lib("spring-modulith-bom")))

    implementation(project(":libs:common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(lib("archunit"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
}

/** Порог покрытия строк — ниже 80 % сборка красная. */
val coverageVerification = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(coverageVerification)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "app.jar"
}

tasks.named<Jar>("jar") {
    enabled = false
}
