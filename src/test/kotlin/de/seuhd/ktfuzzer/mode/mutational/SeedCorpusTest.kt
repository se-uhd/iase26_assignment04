package de.seuhd.ktfuzzer.mode.mutational

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SeedCorpusTest {
    @Test
    fun `load reads the toml seeds in name order, skipping invalid and non-toml files`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("b.toml"), "b")
        Files.writeString(dir.resolve("a.toml"), "a")
        Files.writeString(dir.resolve("invalid_x.toml"), "x")
        Files.writeString(dir.resolve("notes.md"), "ignored")
        assertEquals(listOf("a", "b"), SeedCorpus.load(dir))
    }

    @Test
    fun `load returns nothing when the directory is absent`(@TempDir dir: Path) {
        assertEquals(emptyList(), SeedCorpus.load(dir.resolve("missing")))
    }

    @Test
    fun `loadFile reads one seed file as a single-element list`(@TempDir dir: Path) {
        val file = dir.resolve("seed.toml")
        Files.writeString(file, "one")
        assertEquals(listOf("one"), SeedCorpus.loadFile(file))
    }
}
