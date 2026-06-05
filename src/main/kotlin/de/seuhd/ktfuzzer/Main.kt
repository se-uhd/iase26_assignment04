package de.seuhd.ktfuzzer

import de.seuhd.ktfuzzer.engine.Clock
import de.seuhd.ktfuzzer.engine.FileCrashSink
import de.seuhd.ktfuzzer.engine.Harness
import de.seuhd.ktfuzzer.engine.StopPolicy
import de.seuhd.ktfuzzer.exec.BinaryTarget
import de.seuhd.ktfuzzer.exec.Target
import de.seuhd.ktfuzzer.exec.TargetConfig
import de.seuhd.ktfuzzer.mode.Fuzzer
import de.seuhd.ktfuzzer.mode.Mode
import de.seuhd.ktfuzzer.mode.grammar.GrammarFuzzer
import de.seuhd.ktfuzzer.mode.grammar.GrammarLoader
import de.seuhd.ktfuzzer.mode.mutational.MutationalFuzzer
import de.seuhd.ktfuzzer.mode.mutational.SeedCorpus
import de.seuhd.ktfuzzer.mode.random.RandomFuzzer
import de.seuhd.ktfuzzer.report.CampaignStats
import de.seuhd.ktfuzzer.report.CampaignSummaryJson
import de.seuhd.ktfuzzer.report.ConsoleReporter
import de.seuhd.ktfuzzer.report.RunMetadata
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * CLI entry point: runs [bootstrap] and exits the process with its non-zero exit code.
 *
 * @param rawArgs the raw command-line arguments.
 */
fun main(rawArgs: Array<String>) {
    val code = bootstrap(rawArgs.toList())
    if (code != 0) exitProcess(code)
}

/** Supplies the [Target] for a run; tests inject a fake. */
internal fun interface TargetFactory {
    /** Builds the target from the run config and the target description. */
    fun build(config: FuzzerConfig, targetConfig: TargetConfig): Target
}

/** Supplies the [Fuzzer] for the chosen mode; tests inject a fake. */
internal fun interface FuzzerFactory {
    /** Builds the fuzzer for the chosen mode from the run config and the target description. */
    fun build(config: FuzzerConfig, targetConfig: TargetConfig): Fuzzer
}

/** Defaults and constants used by [bootstrap]. */
private object BootstrapDefaults {
    val targetFactory =
        TargetFactory { config, targetConfig ->
            val binary = defaultBinary(targetConfig) ?: error("no target binary for this OS")
            BinaryTarget(binary, config.runTimeoutMillis, targetConfig.expectedExitCodes)
        }

    val fuzzerFactory =
        FuzzerFactory { config, targetConfig ->
            when (config.mode) {
                Mode.RANDOM -> RandomFuzzer(alphabet(targetConfig), config.minLength, config.maxLength)

                Mode.MUTATIONAL -> MutationalFuzzer(loadSeeds(targetConfig), alphabet(targetConfig))

                Mode.GRAMMAR ->
                    GrammarFuzzer(
                        grammar = GrammarLoader.load(grammarPath(targetConfig)),
                        recursiveDepth = config.grammarRecursiveDepth,
                        minNonTerminals = config.grammarMinNonTerminals,
                        maxNonTerminals = config.grammarMaxNonTerminals,
                        recursionStrategy = config.grammarRecursionStrategy
                    )
            }
        }

    const val SUMMARY_FILE = "campaign-summary.json"
    const val STACK_BYTES = 64L * 1024 * 1024
    const val CRASH_FOUND = 1
}

/**
 * The streams and clock [bootstrap] uses; overridden in tests to capture output and freeze time.
 *
 * @param stdout where successful human-readable output is written.
 * @param stderr where usage errors and file-write warnings are written.
 * @param clock the time source for elapsed-time and timeout checks.
 */
internal class Environment(
    val stdout: PrintStream = System.out,
    val stderr: PrintStream = System.err,
    val clock: Clock = Clock.SYSTEM
)

/**
 * Parses the arguments, loads the target config, builds the harness, runs the campaign, and prints
 * the report. Returns 0 on success or a non-zero exit code on a usage error or, with
 * `--fail-on-crash`, a discovered crash. When [checkBinary] is false the binary need not exist.
 */
@Suppress("ReturnCount")
internal fun bootstrap(
    rawArgs: List<String>,
    env: Environment = Environment(),
    targetFactory: TargetFactory = BootstrapDefaults.targetFactory,
    fuzzerFactory: FuzzerFactory = BootstrapDefaults.fuzzerFactory,
    checkBinary: Boolean = true
): Int {
    val config =
        when (val outcome = parseArgs(rawArgs)) {
            CliResult.Help -> {
                printHelp(env.stdout)
                return 0
            }

            is CliResult.Error -> {
                env.stderr.println(outcome.message)
                return outcome.code
            }

            is CliResult.Ok -> outcome.config
        }

    val targetConfig = TargetConfig.load(config.targetPath).getOrElse {
        env.stderr.println(it.message)
        return CliResult.USAGE_ERROR
    }

    val binary = defaultBinary(targetConfig)
    if (checkBinary && (binary == null || !Files.isRegularFile(binary))) {
        env.stderr.println(
            if (binary == null) {
                "no target binary configured for this OS in ${config.targetPath}"
            } else {
                "target binary not found: $binary"
            }
        )
        return CliResult.USAGE_ERROR
    }
    validateModePaths(config, targetConfig, env.stderr)?.let { return it }

    val stats = CampaignStats(env.clock.nanoTime())
    val crashDir = config.outputDir.resolve("crashes")
    val targetName = binary?.fileName?.toString() ?: targetConfig.name
    val metadata =
        RunMetadata(config.mode.cliValue, config.randomSeed, targetName, crashDir, targetConfig.exitCodeLabels)
    val harness =
        Harness(
            fuzzer = fuzzerFactory.build(config, targetConfig),
            target = targetFactory.build(config, targetConfig),
            stopPolicy = StopPolicy(config.maxExecutions, config.timeLimitMillis, config.stopOnCrash),
            crashSink = FileCrashSink(crashDir),
            campaignStats = stats,
            random = Random(config.randomSeed),
            clock = env.clock
        )

    ConsoleReporter.renderStart(env.stdout, metadata, config.maxExecutions, config.timeLimitMillis, config.stopOnCrash)
    val reason = runOnLargeStack { harness.run() }
    val now = env.clock.nanoTime()
    val json = CampaignSummaryJson.render(metadata, stats, reason, now)
    try {
        Files.createDirectories(config.outputDir)
        Files.writeString(config.outputDir.resolve(BootstrapDefaults.SUMMARY_FILE), json + "\n")
    } catch (e: IOException) {
        env.stderr.println(
            "warning: could not write ${BootstrapDefaults.SUMMARY_FILE} to ${config.outputDir}: ${e.message}"
        )
    }
    ConsoleReporter.renderSummary(env.stdout, metadata, stats, reason, now)
    return if (config.failOnCrash && stats.crashes > 0) BootstrapDefaults.CRASH_FOUND else 0
}

/** The binary the target config lists for the current OS, or null. The only `os.name` read. */
private fun defaultBinary(targetConfig: TargetConfig): Path? = targetConfig.binaryFor(System.getProperty("os.name"))

/** The target's alphabet for random and mutational generation. */
private fun alphabet(targetConfig: TargetConfig): List<Char> = targetConfig.alphabet.toList()

/** The target's grammar path. */
private fun grammarPath(targetConfig: TargetConfig): Path = Path.of(targetConfig.grammar)

/** Loads the mutational seeds from the target's seed directory (or file). */
private fun loadSeeds(targetConfig: TargetConfig): List<String> {
    val path = Path.of(targetConfig.seeds)
    return if (Files.isDirectory(path)) SeedCorpus.load(path) else SeedCorpus.loadFile(path)
}

/**
 * Validates the path the chosen mode needs: grammar mode requires a readable grammar file, and
 * mutational mode requires a readable seed file or directory. On the first failure it prints why to
 * [stderr] and returns [CliResult.USAGE_ERROR]; otherwise null.
 */
@Suppress("ReturnCount")
private fun validateModePaths(config: FuzzerConfig, targetConfig: TargetConfig, stderr: PrintStream): Int? {
    if (config.mode == Mode.GRAMMAR) {
        val grammar = Path.of(targetConfig.grammar)
        if (!isReadableFile(grammar)) return usageError(stderr, "grammar must be a readable file: $grammar")
    }
    if (config.mode == Mode.MUTATIONAL) {
        val seeds = Path.of(targetConfig.seeds)
        if (!isReadableFileOrDirectory(seeds)) {
            return usageError(stderr, "seeds must be a readable file or directory: $seeds")
        }
    }
    return null
}

/** Prints [message] to [stderr] and returns the usage exit code. */
private fun usageError(stderr: PrintStream, message: String): Int {
    stderr.println(message)
    return CliResult.USAGE_ERROR
}

/** True when [path] is a regular file that can be read. */
private fun isReadableFile(path: Path): Boolean = Files.isRegularFile(path) && Files.isReadable(path)

/** True when [path] is a readable regular file or directory. */
private fun isReadableFileOrDirectory(path: Path): Boolean =
    Files.isReadable(path) && (Files.isRegularFile(path) || Files.isDirectory(path))

/**
 * Runs [block] on a larger stack. Grammar derivation recurses once per nesting level, which can
 * overflow the default thread stack at deep nesting.
 */
private fun <T> runOnLargeStack(block: () -> T): T {
    var result: Result<T>? = null
    val thread = Thread(null, { result = runCatching(block) }, "kt-fuzzer-harness", BootstrapDefaults.STACK_BYTES)
    thread.start()
    thread.join()
    return result!!.getOrThrow()
}
