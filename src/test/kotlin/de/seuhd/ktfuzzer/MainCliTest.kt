package de.seuhd.ktfuzzer

import de.seuhd.ktfuzzer.exec.ExecResult
import de.seuhd.ktfuzzer.exec.Signal
import de.seuhd.ktfuzzer.exec.Target
import de.seuhd.ktfuzzer.mode.Fuzzer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the CLI end to end through [bootstrap] with fake factories and captured output streams, so
 * these tests check the wiring (exit codes, which stream output goes to, file effects, and config
 * error handling), not message wording. Flag parsing is covered by [CliArgsTest].
 */
class MainCliTest {
    private val okTarget = TargetFactory { _, _ -> Target { ExecResult.Expected(0) } }
    private val crashTarget = TargetFactory { _, _ -> Target { ExecResult.Crash(Signal.SIGSEGV.exitCode) } }
    private val constFuzzer = FuzzerFactory { _, _ -> Fuzzer { "x = 1\n" } }

    private class Streams {
        val stdoutBuffer = ByteArrayOutputStream()
        val stderrBuffer = ByteArrayOutputStream()
        val stdout = PrintStream(stdoutBuffer)
        val stderr = PrintStream(stderrBuffer)
    }

    private fun run(
        args: List<String>,
        target: TargetFactory = okTarget,
        fuzzer: FuzzerFactory = constFuzzer,
        checkBinary: Boolean = false
    ): Pair<Int, Streams> {
        val s = Streams()
        val code =
            bootstrap(
                args,
                env = Environment(s.stdout, s.stderr),
                targetFactory = target,
                fuzzerFactory = fuzzer,
                checkBinary = checkBinary
            )
        return code to s
    }

    /** Writes [yaml] to a target config under [dir] and returns the `--target` arguments for it. */
    private fun targetArgs(dir: Path, yaml: String): List<String> {
        val path = dir.resolve("target.yaml")
        Files.writeString(path, yaml)
        return listOf("--target", path.toString())
    }

    /** A schema-valid target config whose files do not have to exist unless the test asks for that. */
    private fun validTargetYaml(
        dir: Path,
        binary: Path = dir.resolve("target-binary"),
        grammar: Path = dir.resolve("grammar.ebnf"),
        seeds: Path = dir.resolve("seeds")
    ): String =
        """
        name: t
        binaries:
          linux: $binary
          mac: $binary
          windows: $binary
        grammar: $grammar
        seeds: $seeds
        alphabet: abc
        """.trimIndent()

    @Test
    fun `--help returns zero and prints to stdout`() {
        val (code, s) = run(listOf("--help"))
        assertEquals(0, code)
        assertTrue(s.stdoutBuffer.size() > 0, "help should be written to stdout")
    }

    @Test
    fun `a parse error returns the usage code and reports to stderr`() {
        val (code, s) = run(listOf("--definitely-not-a-flag"))
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the error should be written to stderr")
    }

    @Test
    fun `a clean run returns zero`(@TempDir dir: Path) {
        val (code, _) = run(listOf("--mode", "random", "--max-executions", "100", "--output-dir", dir.toString()))
        assertEquals(0, code)
    }

    @Test
    fun `finding a crash still returns zero by default, and writes the crash`(@TempDir dir: Path) {
        val (code, _) = run(listOf("--max-executions", "10", "--output-dir", dir.toString()), target = crashTarget)
        assertEquals(0, code, "a successful fuzzing run returns 0 even when it finds crashes")
        val crashes = dir.resolve("crashes")
        assertTrue(
            Files.isDirectory(crashes) && Files.list(crashes).use { it.count() } > 0,
            "a crash artifact should be written"
        )
    }

    @Test
    fun `--fail-on-crash returns the crash code when a crash is found`(@TempDir dir: Path) {
        val (code, _) =
            run(
                listOf("--fail-on-crash", "--max-executions", "10", "--output-dir", dir.toString()),
                target = crashTarget
            )
        assertEquals(1, code)
    }

    @Test
    fun `text mode writes the banner and result to stdout only`(@TempDir dir: Path) {
        val (code, s) = run(listOf("--max-executions", "1", "--output-dir", dir.toString()))
        assertEquals(0, code)
        assertEquals("", s.stderrBuffer.toString(), "successful text mode should not write to stderr")
        assertTrue(s.stdoutBuffer.toString().lineSequence().count() >= 3, "stdout should contain banner and result")
    }

    @Test
    fun `a run writes a parseable campaign-summary json to the output dir`(@TempDir dir: Path) {
        val (code, _) = run(listOf("--max-executions", "5", "--output-dir", dir.toString()), target = crashTarget)
        assertEquals(0, code)
        val summary = dir.resolve("campaign-summary.json")
        assertTrue(Files.exists(summary), "campaign-summary.json should be written to the output dir")
        assertTrue(
            Json.parseToJsonElement(Files.readString(summary)).jsonObject.isNotEmpty(),
            "campaign-summary.json should be a JSON object"
        )
    }

    @Test
    fun `a missing target config is a usage error reported to stderr`(@TempDir dir: Path) {
        val (code, s) = run(listOf("--target", dir.resolve("absent.yaml").toString()))
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the missing-config error should be written to stderr")
    }

    @Test
    fun `a configured binary that does not exist is a usage error`(@TempDir dir: Path) {
        val missing = dir.resolve("nope")
        val args = targetArgs(dir, validTargetYaml(dir, binary = missing))
        val (code, s) = run(args, checkBinary = true)
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the missing-binary error should be written to stderr")
    }

    @Test
    fun `a target config missing required fields is a usage error`(@TempDir dir: Path) {
        val (code, s) = run(targetArgs(dir, "name: t\n"), checkBinary = true)
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the invalid-config error should be written to stderr")
    }

    @Test
    fun `grammar mode with an unreadable grammar is a usage error`(@TempDir dir: Path) {
        val (code, s) =
            run(
                targetArgs(dir, validTargetYaml(dir, grammar = dir.resolve("missing.ebnf"))) +
                    listOf("--mode", "grammar")
            )
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the unreadable-grammar error should be written to stderr")
        assertEquals(0, s.stdoutBuffer.size(), "usage errors should not write a normal result")
    }

    @Test
    fun `mutational mode with unreadable seeds is a usage error`(@TempDir dir: Path) {
        val (code, s) =
            run(
                targetArgs(dir, validTargetYaml(dir, seeds = dir.resolve("missing-seeds"))) +
                    listOf("--mode", "mutational")
            )
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the unreadable-seeds error should be written to stderr")
    }

    @Test
    fun `a directory as the target config is a usage error, not a stack trace`(@TempDir dir: Path) {
        val (code, s) = run(listOf("--target", dir.toString()))
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue(s.stderrBuffer.size() > 0, "the config error should be written to stderr")
    }

    @Test
    fun `a fuzzer setup failure is a usage error, not a stack trace`(@TempDir dir: Path) {
        val failing = FuzzerFactory { _, _ -> throw IllegalArgumentException("no usable seeds in seeds") }
        val (code, s) = run(listOf("--output-dir", dir.toString()), fuzzer = failing)
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue("no usable seeds" in s.stderrBuffer.toString(), "the setup error should be written to stderr")
    }

    @Test
    fun `a binary without the executable bit is a usage error`(@TempDir dir: Path) {
        assumeFalse(
            System.getProperty("os.name").lowercase().contains("windows"),
            "the executable bit is Unix-only"
        )
        val binary = dir.resolve("target-binary")
        Files.writeString(binary, "#!/bin/sh\nexit 0\n")
        val args = targetArgs(dir, validTargetYaml(dir, binary = binary))
        val (code, s) = run(args, checkBinary = true)
        assertEquals(CliResult.USAGE_ERROR, code)
        assertTrue("not executable" in s.stderrBuffer.toString(), "the error should name the missing exec bit")
    }

    @Test
    fun `a target that never starts exits with the start-failure code and stops early`(@TempDir dir: Path) {
        val brokenTarget = TargetFactory { _, _ -> Target { ExecResult.Error("spawn failed") } }
        val (code, s) =
            run(listOf("--max-executions", "1000", "--output-dir", dir.toString()), target = brokenTarget)
        assertEquals(3, code, "a campaign that never ran the target must not exit 0")
        assertTrue("failed to start" in s.stderrBuffer.toString(), "the abort reason should be on stderr")
    }

    @Test
    fun `a second run into the same output dir warns about leftover crashes`(@TempDir dir: Path) {
        val args = listOf("--max-executions", "5", "--output-dir", dir.toString())
        val (_, first) = run(args, target = crashTarget)
        assertEquals("", first.stderrBuffer.toString(), "the first run sees an empty crash dir")
        val (_, second) = run(args, target = crashTarget)
        assertTrue("warning:" in second.stderrBuffer.toString(), "leftover crash folders should be flagged")
    }
}
