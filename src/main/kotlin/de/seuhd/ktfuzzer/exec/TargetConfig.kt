package de.seuhd.ktfuzzer.exec

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KProperty

/**
 * Configuration for a fuzz target loaded from a YAML file.
 *
 * @property name human-readable target name shown in the run summary
 * @property binaries OS-specific target binaries
 * @property grammar EBNF grammar used in grammar mode
 * @property seeds seed corpus directory used in mutational mode
 * @property alphabet characters used for random and mutational input generation
 * @property expectedExitCodes exit codes treated as non-crashing results
 * @property exitCodeLabels human-readable names for specific exit codes, shown in the run summary
 */
@Serializable
internal data class TargetConfig(
    val name: String,
    val binaries: BinaryPaths,
    val grammar: String,
    val seeds: String,
    val alphabet: String,
    val expectedExitCodes: Set<Int> = DEFAULT_EXPECTED_EXIT_CODES,
    val exitCodeLabels: Map<Int, String> = emptyMap()
) {
    init {
        require(name.isNotBlank()) { getValidationMessage(::name) }
        require(grammar.isNotBlank()) { getValidationMessage(::grammar) }
        require(seeds.isNotBlank()) { getValidationMessage(::seeds) }
        require(alphabet.isNotEmpty()) { getValidationMessage(::alphabet) }
        require(expectedExitCodes.isNotEmpty()) { getValidationMessage(::expectedExitCodes) }
    }

    /**
     * OS-specific paths to target binaries.
     *
     * @property linux path to the Linux binary.
     * @property mac path to the macOS binary.
     * @property windows path to the Windows binary.
     */
    @Serializable
    internal data class BinaryPaths(val linux: String, val mac: String, val windows: String) {
        init {
            val group = TargetConfig::binaries.name
            require(linux.isNotBlank()) { getValidationMessage(::linux, group) }
            require(mac.isNotBlank()) { getValidationMessage(::mac, group) }
            require(windows.isNotBlank()) { getValidationMessage(::windows, group) }
        }
    }

    /** The configured binary path for JVM `os.name` [jvmOsName], or null when it is unknown. */
    fun binaryFor(jvmOsName: String): Path? {
        val osName = jvmOsName.lowercase()
        val binary =
            when {
                osName.contains("mac") || osName.contains("darwin") -> binaries.mac
                osName.contains("win") -> binaries.windows
                osName.contains("linux") -> binaries.linux
                else -> return null
            }
        return Path.of(binary)
    }

    companion object {
        /** Exit codes that count as a normal run when the YAML omits `expectedExitCodes`. */
        val DEFAULT_EXPECTED_EXIT_CODES: Set<Int> = setOf(0, 1)

        /**
         * Loads and parses the YAML target config at [path]. Returns [Result.success] with the parsed
         * config, or [Result.failure] with a one-line message if the file cannot be read or its
         * contents are not valid YAML.
         */
        fun load(path: Path): Result<TargetConfig> {
            if (!Files.isReadable(path)) {
                return Result.failure(IllegalArgumentException("target config not found: $path"))
            }
            return try {
                Result.success(Yaml.default.decodeFromString(serializer(), Files.readString(path)))
            } catch (e: SerializationException) {
                Result.failure(IllegalArgumentException("invalid target config $path: ${e.message}"))
            } catch (e: IllegalArgumentException) {
                Result.failure(IllegalArgumentException("invalid target config $path: ${e.message}"))
            }
        }
    }
}

/**
 * The "target config requires ..." validation message for [property]. The field name comes from the
 * property, so renaming the property changes the message too. [group] prefixes a nested field, e.g., `binaries`.
 */
private fun getValidationMessage(property: KProperty<*>, group: String = ""): String {
    val prefix = if (group.isEmpty()) "" else "$group."
    return "target config requires non-blank '$prefix${property.name}'"
}
