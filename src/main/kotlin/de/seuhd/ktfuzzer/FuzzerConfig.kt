package de.seuhd.ktfuzzer

import de.seuhd.ktfuzzer.mode.Mode
import de.seuhd.ktfuzzer.mode.grammar.RecursionStrategy
import de.seuhd.ktfuzzer.mode.random.RandomFuzzer
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Run settings, filled in by the CLI parser. The target itself (binary, grammar, seeds, alphabet,
 * expected exit codes) is read from the target config at [targetPath]; the fields here select the
 * mode and the campaign's limits: lengths, execution and time budgets, timeouts, the seed, and the
 * output directory.
 */
internal data class FuzzerConfig(
    /** YAML file describing the target; everything target-specific is read from it. */
    val targetPath: Path = Paths.get("targets/toml/target.yaml"),
    /** Which generation strategy to run. */
    val mode: Mode = Mode.RANDOM,
    /** Minimum generated length in random mode. */
    val minLength: Int = RandomFuzzer.DEFAULT_MIN_LENGTH,
    /** Maximum generated length in random mode. */
    val maxLength: Int = RandomFuzzer.DEFAULT_MAX_LENGTH,
    /** Stop after this many executions, or null to run unbounded. */
    val maxExecutions: Long? = DEFAULT_MAX_EXECUTIONS,
    /** Stop after this many milliseconds of wall-clock time, or null for no time limit. */
    val timeLimitMillis: Long? = null,
    /** Stop at the first crash. */
    val stopOnCrash: Boolean = false,
    /** Exit non-zero if any crash was found (for a CI gate). */
    val failOnCrash: Boolean = false,
    /** Requested recursion depth for grammar mode (0 = generic expansion). */
    val grammarRecursiveDepth: Int = DEFAULT_GRAMMAR_RECURSIVE_DEPTH,
    /** Grammar-mode max-cost grow budget. */
    val grammarMinNonTerminals: Int = DEFAULT_GRAMMAR_MIN_NON_TERMINALS,
    /** Grammar-mode random-expansion budget. */
    val grammarMaxNonTerminals: Int = DEFAULT_GRAMMAR_MAX_NON_TERMINALS,
    /** How grammar mode grows recursive branches. */
    val grammarRecursionStrategy: RecursionStrategy = RecursionStrategy.BROAD,
    /** Crashing inputs are written under `outputDir/crashes/`. */
    val outputDir: Path = Paths.get("output"),
    /** Per-run timeout in milliseconds; a run that exceeds it is killed and counted as a timeout. */
    val runTimeoutMillis: Long = DEFAULT_RUN_TIMEOUT_MILLIS,
    /** Seed for every random choice, so a run is reproducible. */
    val randomSeed: Long = DEFAULT_RANDOM_SEED
) {
    companion object {
        const val DEFAULT_MAX_EXECUTIONS = 10_000L
        const val DEFAULT_GRAMMAR_RECURSIVE_DEPTH = 0
        const val MAX_GRAMMAR_RECURSIVE_DEPTH = 50_000
        const val DEFAULT_GRAMMAR_MIN_NON_TERMINALS = 0
        const val DEFAULT_GRAMMAR_MAX_NON_TERMINALS = 10
        const val DEFAULT_RUN_TIMEOUT_MILLIS = 15_000L

        /** Fixed default seed (0xA5E26) so a default run is reproducible. */
        const val DEFAULT_RANDOM_SEED = 0xA5E26L
    }
}
