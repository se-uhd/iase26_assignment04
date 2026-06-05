package de.seuhd.ktfuzzer.mode.mutational

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** Loads the seed corpus for the mutational fuzzer from the target's seed directory. */
internal object SeedCorpus {
    /**
     * Reads the `*.toml` seeds from [dir], sorted by name and skipping files whose name starts with
     * `invalid` (those already fail to parse). Returns an empty list when [dir] is not a directory or
     * holds no usable seeds.
     */
    fun load(dir: Path): List<String> = if (Files.isDirectory(dir)) {
        Files.newDirectoryStream(dir, "*.toml").use { stream ->
            stream
                .sortedBy { it.name }
                .filterNot { it.name.startsWith("invalid") }
                .map { Files.readString(it) }
        }
    } else {
        emptyList()
    }

    /** Reads a single seed file and returns its contents as a one-element list. */
    fun loadFile(file: Path): List<String> = listOf(Files.readString(file))
}
