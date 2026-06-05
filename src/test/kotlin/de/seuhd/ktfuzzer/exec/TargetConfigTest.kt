package de.seuhd.ktfuzzer.exec

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetConfigTest {
    private fun load(dir: Path, yaml: String): Result<TargetConfig> {
        val path = dir.resolve("target.yaml")
        Files.writeString(path, yaml)
        return TargetConfig.load(path)
    }

    @Test
    fun `a full config parses every field`(@TempDir dir: Path) {
        val config =
            load(
                dir,
                """
                name: toml_parser
                binaries:
                  linux: bin/linux
                  mac: bin/mac
                  windows: bin/win
                grammar: g.ebnf
                seeds: seeds
                alphabet: "ab\n"
                expectedExitCodes: [0, 1, 2]
                """.trimIndent()
            ).getOrThrow()
        assertEquals("toml_parser", config.name)
        assertEquals("g.ebnf", config.grammar)
        assertEquals("seeds", config.seeds)
        assertEquals("ab\n", config.alphabet)
        assertEquals(setOf(0, 1, 2), config.expectedExitCodes)
    }

    @Test
    fun `expected exit codes default to accepted parser exits`(@TempDir dir: Path) {
        val config = load(dir, validYaml()).getOrThrow()

        assertEquals(setOf(0, 1), config.expectedExitCodes)
    }

    @Test
    fun `target fields are required`(@TempDir dir: Path) {
        val missingName =
            """
            binaries:
              linux: bin/linux
              mac: bin/mac
              windows: bin/win
            grammar: g.ebnf
            seeds: seeds
            alphabet: abc
            """.trimIndent()
        val missingBinaries =
            """
            name: t
            grammar: g.ebnf
            seeds: seeds
            alphabet: abc
            """.trimIndent()
        val missingGrammar =
            """
            name: t
            binaries:
              linux: bin/linux
              mac: bin/mac
              windows: bin/win
            seeds: seeds
            alphabet: abc
            """.trimIndent()
        val missingSeeds =
            """
            name: t
            binaries:
              linux: bin/linux
              mac: bin/mac
              windows: bin/win
            grammar: g.ebnf
            alphabet: abc
            """.trimIndent()
        val missingAlphabet =
            """
            name: t
            binaries:
              linux: bin/linux
              mac: bin/mac
              windows: bin/win
            grammar: g.ebnf
            seeds: seeds
            """.trimIndent()

        for (yaml in listOf(missingName, missingBinaries, missingGrammar, missingSeeds, missingAlphabet)) {
            assertTrue(load(dir, yaml).isFailure)
        }
    }

    @Test
    fun `each binary path is required`(@TempDir dir: Path) {
        val missingWindows =
            """
            name: t
            binaries:
              linux: bin/linux
              mac: bin/mac
            grammar: g.ebnf
            seeds: seeds
            alphabet: abc
            """.trimIndent()

        assertTrue(load(dir, missingWindows).isFailure)
    }

    @Test
    fun `blank required values are rejected`(@TempDir dir: Path) {
        assertTrue(load(dir, validYaml(alphabet = "")).isFailure)
        assertTrue(load(dir, validYaml(linux = " ")).isFailure)
    }

    @Test
    fun `binaryFor maps JVM OS names to configured binaries`(@TempDir dir: Path) {
        val config = load(dir, validYaml()).getOrThrow()

        assertEquals(Path.of("bin/linux"), config.binaryFor("Linux"))
        assertEquals(Path.of("bin/mac"), config.binaryFor("Mac OS X"))
        assertEquals(Path.of("bin/mac"), config.binaryFor("Darwin"))
        assertEquals(Path.of("bin/win"), config.binaryFor("Windows 11"))
        assertNull(config.binaryFor("Plan 9"))
    }

    @Test
    fun `a missing config file is a failure, not an exception`(@TempDir dir: Path) {
        assertTrue(TargetConfig.load(dir.resolve("absent.yaml")).isFailure)
    }

    @Test
    fun `malformed YAML is a failure, not an exception`(@TempDir dir: Path) {
        assertTrue(load(dir, "expectedExitCodes: not-a-list\n").isFailure)
    }

    private fun validYaml(
        linux: String = "bin/linux",
        mac: String = "bin/mac",
        windows: String = "bin/win",
        alphabet: String = "abc"
    ): String =
        """
        name: t
        binaries:
          linux: $linux
          mac: $mac
          windows: $windows
        grammar: g.ebnf
        seeds: seeds
        alphabet: "$alphabet"
        """.trimIndent()
}
