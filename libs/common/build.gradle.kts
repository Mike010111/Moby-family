plugins {
    id("moby.java-conventions")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("com.fasterxml.jackson.core:jackson-annotations")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
