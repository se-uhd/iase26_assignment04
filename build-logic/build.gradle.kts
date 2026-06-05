plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.3.21")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.3.21")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.3")
}

gradlePlugin {
    plugins {
        create("fuzzerCommon") {
            id = "de.seuhd.fuzzer-common"
            implementationClass = "FuzzerCommonPlugin"
        }
    }
}
