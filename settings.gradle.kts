pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "moby-family"

include(
    "libs:common",
    "services:gateway",
    "services:payment",
    "services:family",
    "services:rewards",
    "services:notification",
)
