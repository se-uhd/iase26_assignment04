import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.mavenCentral
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class FuzzerCommonPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("application")
            pluginManager.apply("dev.detekt")

            group = "de.seuhd"
            version = "1.0-SNAPSHOT"

            repositories {
                mavenCentral()
            }

            dependencies {
                add("implementation", "com.charleskorn.kaml:kaml:0.104.0")
                add("implementation", "org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
                add("implementation", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.3")
                add("testImplementation", "org.jetbrains.kotlin:kotlin-test:2.3.21")
                add("testImplementation", "io.kotest:kotest-property:6.1.11")
                add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }

            extensions.configure<KotlinJvmProjectExtension>("kotlin") {
                jvmToolchain(25)
            }

            extensions.configure<JavaApplication>("application") {
                mainClass.set("de.seuhd.ktfuzzer.MainKt")
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            tasks.named("run", JavaExec::class.java) {
                standardInput = System.`in`
            }

            extensions.configure<DetektExtension>("detekt") {
                toolVersion.set("2.0.0-alpha.3")
                source.setFrom("src/main/kotlin", "src/test/kotlin")
                buildUponDefaultConfig.set(true)
                autoCorrect.set(providers.gradleProperty("detektAutoCorrect").orNull == "true")
                config.setFrom(files("config/detekt/detekt.yml"))
            }

            tasks.named("detekt").configure { enabled = false }
            tasks.named("check").configure {
                dependsOn(
                    tasks.named("detektMainSourceSet"),
                    tasks.named("detektTestSourceSet")
                )
            }

            val staticAnalysisConfig = layout.projectDirectory.file("config/detekt/static-analysis.yml")
            if (staticAnalysisConfig.asFile.isFile) {
                val detektStaticAnalysis = tasks.register("detektStaticAnalysis", Detekt::class.java) {
                    group = "verification"
                    description = "Run Kotlin static analysis for the reference build."
                    setSource(files("src/main/kotlin", "src/test/kotlin"))
                    config.setFrom(files(staticAnalysisConfig))
                    buildUponDefaultConfig.set(true)
                    autoCorrect.set(false)
                    parallel.set(true)
                    include("**/*.kt")
                    exclude("**/build/**")
                }

                tasks.named("check").configure {
                    dependsOn(detektStaticAnalysis)
                }
            }
        }
    }
}
