pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// detekt-rules ships only in the reference repo, not the generated student repo.
if (file("detekt-rules").isDirectory) {
    includeBuild("detekt-rules")
}

rootProject.name = "iase26-assignment04"
