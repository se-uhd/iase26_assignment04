import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
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

            // A plugin class cannot use the generated `libs` accessor, so read the consuming
            // project's catalog (the root build auto-detects gradle/libs.versions.toml).
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", libs.findLibrary("kaml").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-core").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
                add("detektPlugins", libs.findLibrary("detekt-rules-ktlint-wrapper").get())
                add("testImplementation", libs.findLibrary("kotlin-test").get())
                add("testImplementation", libs.findLibrary("kotest-property").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-core").get())
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
                toolVersion.set(libs.findVersion("detekt").get().requiredVersion)
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
