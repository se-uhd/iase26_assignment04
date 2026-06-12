plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.jvm.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("fuzzerCommon") {
            id = "de.seuhd.fuzzer-common"
            implementationClass = "FuzzerCommonPlugin"
        }
    }
}
