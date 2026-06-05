package de.seuhd.ktfuzzer

import de.seuhd.ktfuzzer.mode.Mode
import de.seuhd.ktfuzzer.mode.grammar.RecursionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliArgsTest {
    private fun ok(vararg args: String): FuzzerConfig {
        val outcome = parseArgs(args.toList())
        assertTrue(outcome is CliResult.Ok, "expected Ok, got $outcome")
        return outcome.config
    }

    private fun errorCode(vararg args: String): Int {
        val outcome = parseArgs(args.toList())
        assertTrue(outcome is CliResult.Error, "expected Error, got $outcome")
        return outcome.code
    }

    @Test
    fun `no arguments yields the default config`() {
        assertEquals(FuzzerConfig(), ok())
    }

    @Test
    fun `--target sets the config path`() {
        assertEquals("targets/c/target.yaml", ok("--target", "targets/c/target.yaml").targetPath.toString())
    }

    @Test
    fun `each mode value parses`() {
        assertEquals(Mode.RANDOM, ok("--mode", "random").mode)
        assertEquals(Mode.MUTATIONAL, ok("--mode", "mutational").mode)
        assertEquals(Mode.GRAMMAR, ok("--mode", "grammar").mode)
    }

    @Test
    fun `length flags are parsed`() {
        val config = ok("--min-length", "3", "--max-length", "9")
        assertEquals(3, config.minLength)
        assertEquals(9, config.maxLength)
    }

    @Test
    fun `negative lengths and an inverted range are usage errors`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--min-length", "-1"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--max-length", "-1"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--min-length", "10", "--max-length", "5"))
    }

    @Test
    fun `numeric flags are parsed`() {
        val config =
            ok(
                "--max-executions", "5",
                "--time-limit", "100",
                "--run-timeout", "200",
                "--random-seed", "7",
                "--grammar-recursive-depth", "9",
                "--grammar-min-nonterminals", "2",
                "--grammar-max-nonterminals", "11"
            )
        assertEquals(5L, config.maxExecutions)
        assertEquals(100L, config.timeLimitMillis)
        assertEquals(200L, config.runTimeoutMillis)
        assertEquals(7L, config.randomSeed)
        assertEquals(9, config.grammarRecursiveDepth)
        assertEquals(2, config.grammarMinNonTerminals)
        assertEquals(11, config.grammarMaxNonTerminals)
    }

    @Test
    fun `the output directory is parsed`() {
        assertEquals("results", ok("--output-dir", "results").outputDir.toString())
    }

    @Test
    fun `grammar recursion strategy parses`() {
        assertEquals(RecursionStrategy.BROAD, ok("--grammar-recursion-strategy", "broad").grammarRecursionStrategy)
        assertEquals(RecursionStrategy.LINEAR, ok("--grammar-recursion-strategy", "linear").grammarRecursionStrategy)
        assertEquals(CliResult.USAGE_ERROR, errorCode("--grammar-recursion-strategy", "spiral"))
    }

    @Test
    fun `max-executions of zero means unbounded`() {
        assertNull(ok("--max-executions", "0").maxExecutions)
    }

    @Test
    fun `negative max-executions is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--max-executions", "-1"))
    }

    @Test
    fun `boolean flags are parsed`() {
        val config = ok("--stop-on-crash", "--fail-on-crash")
        assertTrue(config.stopOnCrash)
        assertTrue(config.failOnCrash)
    }

    @Test
    fun `an unknown mode is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--mode", "telepathy"))
    }

    @Test
    fun `non-numeric values for numeric flags are usage errors`() {
        for (flag in listOf(
            "--max-executions",
            "--time-limit",
            "--run-timeout",
            "--random-seed",
            "--grammar-recursive-depth",
            "--grammar-min-nonterminals",
            "--grammar-max-nonterminals"
        )) {
            assertEquals(CliResult.USAGE_ERROR, errorCode(flag, "lots"), "expected an error for $flag")
        }
    }

    @Test
    fun `grammar numeric controls validate their ranges`() {
        val maxDepth = FuzzerConfig.MAX_GRAMMAR_RECURSIVE_DEPTH

        assertEquals(CliResult.USAGE_ERROR, errorCode("--grammar-recursive-depth", "-1"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--grammar-recursive-depth", (maxDepth + 1).toString()))
        assertEquals(maxDepth, ok("--grammar-recursive-depth", maxDepth.toString()).grammarRecursiveDepth)
        assertEquals(CliResult.USAGE_ERROR, errorCode("--grammar-min-nonterminals", "-1"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--grammar-max-nonterminals", "-1"))
        assertEquals(
            CliResult.USAGE_ERROR,
            errorCode("--grammar-min-nonterminals", "11", "--grammar-max-nonterminals", "10")
        )
    }

    @Test
    fun `a non-positive run timeout is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--run-timeout", "0"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--run-timeout", "-1"))
        assertEquals(5L, ok("--run-timeout", "5").runTimeoutMillis)
    }

    @Test
    fun `a negative time limit is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--time-limit", "-1"))
        assertEquals(0L, ok("--time-limit", "0").timeLimitMillis)
    }

    @Test
    fun `a flag missing its value is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--mode"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--max-executions"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--target"))
    }

    @Test
    fun `an unknown flag is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("--bogus"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("--verbose"))
        assertEquals(CliResult.USAGE_ERROR, errorCode("-v"))
    }

    @Test
    fun `a positional argument is a usage error`() {
        assertEquals(CliResult.USAGE_ERROR, errorCode("some/binary"))
    }

    @Test
    fun `help is recognized in both forms`() {
        assertEquals(CliResult.Help, parseArgs(listOf("--help")))
        assertEquals(CliResult.Help, parseArgs(listOf("-h")))
    }
}
