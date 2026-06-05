package de.seuhd.ktfuzzer

import de.seuhd.ktfuzzer.mode.Mode
import de.seuhd.ktfuzzer.mode.grammar.RecursionStrategy
import java.io.PrintStream
import java.nio.file.Paths

/**
 * One command-line flag, defined once and used for both parsing and the help text.
 *
 * @property names the flag and its aliases (e.g. `--help`, `-h`).
 * @property valueLabel placeholder for the flag's value in help, or null for a boolean flag.
 * @property help one-line description shown in the help text.
 * @property defaultText default shown in help, or null when there is none.
 * @property apply takes the config, the flag name as typed, and its value (null for a boolean flag),
 *   and returns the updated config or a usage error.
 */
private class Flag(
    val names: List<String>,
    val valueLabel: String?,
    val help: String,
    val defaultText: String?,
    val apply: (FuzzerConfig, String, String?) -> CliResult
)

/** A usage error for a flag given without its required value. */
private fun missing(flag: String) = CliResult.Error("flag $flag needs a value", CliResult.USAGE_ERROR)

/** A usage error for a flag whose value is not a number. */
private fun badNumber(flag: String) = CliResult.Error("flag $flag expects a number", CliResult.USAGE_ERROR)

/** A usage error carrying [message]. */
private fun usage(message: String) = CliResult.Error(message, CliResult.USAGE_ERROR)

/** Parses [value] as a non-negative Int and applies [set], or returns the matching usage error. */
private inline fun nonNegativeInt(
    config: FuzzerConfig,
    flag: String,
    value: String?,
    set: (FuzzerConfig, Int) -> FuzzerConfig
): CliResult {
    val n = value?.toIntOrNull() ?: return badNumber(flag)
    if (n < 0) return usage("flag $flag must not be negative")
    return CliResult.Ok(set(config, n))
}

/** Parses [value] as a non-negative Long and applies [set], or returns the matching usage error. */
private inline fun nonNegativeLong(
    config: FuzzerConfig,
    flag: String,
    value: String?,
    set: (FuzzerConfig, Long) -> FuzzerConfig
): CliResult {
    val n = value?.toLongOrNull() ?: return badNumber(flag)
    if (n < 0) return usage("flag $flag must not be negative")
    return CliResult.Ok(set(config, n))
}

/** Static CLI parser data: default values and the flag table, in help-text order. */
private object CliSpec {
    val defaults = FuzzerConfig()

    val flags: List<Flag> = listOf(
        Flag(listOf("--mode"), "MODE", Mode.choices, defaults.mode.cliValue) { c, _, v ->
            Mode.fromString(v!!)?.let { CliResult.Ok(c.copy(mode = it)) }
                ?: usage("unknown mode '$v' (use ${Mode.choices})")
        },
        Flag(
            listOf("--target"),
            "PATH",
            "YAML config describing the target: binaries, grammar, seeds, alphabet, expected exit codes",
            defaults.targetPath.toString()
        ) { c, _, v ->
            CliResult.Ok(c.copy(targetPath = Paths.get(v!!)))
        },
        Flag(
            listOf("--min-length"),
            "N",
            "random-mode minimum input length",
            defaults.minLength.toString()
        ) { c, f, v ->
            nonNegativeInt(c, f, v) { cc, n -> cc.copy(minLength = n) }
        },
        Flag(
            listOf("--max-length"),
            "N",
            "random-mode maximum input length",
            defaults.maxLength.toString()
        ) { c, f, v ->
            nonNegativeInt(c, f, v) { cc, n -> cc.copy(maxLength = n) }
        },
        Flag(
            listOf("--max-executions"),
            "N",
            "stop after N executions (0 = unbounded)",
            defaults.maxExecutions.toString()
        ) { c, f, v ->
            nonNegativeLong(c, f, v) { cc, n -> cc.copy(maxExecutions = if (n <= 0) null else n) }
        },
        Flag(listOf("--time-limit"), "MS", "stop after this many milliseconds of wall-clock time", null) { c, f, v ->
            nonNegativeLong(c, f, v) { cc, n -> cc.copy(timeLimitMillis = n) }
        },
        Flag(listOf("--stop-on-crash"), null, "stop at the first crash", null) { c, _, _ ->
            CliResult.Ok(c.copy(stopOnCrash = true))
        },
        Flag(listOf("--fail-on-crash"), null, "exit non-zero if any crash was found (for a CI gate)", null) { c, _, _ ->
            CliResult.Ok(c.copy(failOnCrash = true))
        },
        Flag(
            listOf("--grammar-recursive-depth"),
            "N",
            "grammar-mode recursion depth, 0 = generic expansion",
            "${defaults.grammarRecursiveDepth}, max ${FuzzerConfig.MAX_GRAMMAR_RECURSIVE_DEPTH}"
        ) { c, f, v ->
            val maxDepth = FuzzerConfig.MAX_GRAMMAR_RECURSIVE_DEPTH
            val n = v!!.toIntOrNull()
            when {
                n == null -> badNumber(f)
                n !in 0..maxDepth -> usage("flag $f out of range (0..$maxDepth)")
                else -> CliResult.Ok(c.copy(grammarRecursiveDepth = n))
            }
        },
        Flag(
            listOf("--grammar-min-nonterminals"),
            "N",
            "grammar-mode max-cost grow budget",
            defaults.grammarMinNonTerminals.toString()
        ) { c, f, v ->
            nonNegativeInt(c, f, v) { cc, n -> cc.copy(grammarMinNonTerminals = n) }
        },
        Flag(
            listOf("--grammar-max-nonterminals"),
            "N",
            "grammar-mode random expansion budget",
            defaults.grammarMaxNonTerminals.toString()
        ) { c, f, v ->
            nonNegativeInt(c, f, v) { cc, n -> cc.copy(grammarMaxNonTerminals = n) }
        },
        Flag(
            listOf("--grammar-recursion-strategy"),
            "S",
            "grammar-mode recursion strategy: ${RecursionStrategy.choices}",
            defaults.grammarRecursionStrategy.cliValue
        ) { c, _, v ->
            RecursionStrategy.fromString(v!!)?.let { CliResult.Ok(c.copy(grammarRecursionStrategy = it)) }
                ?: usage("unknown grammar recursion strategy '$v' (use ${RecursionStrategy.choices})")
        },
        Flag(listOf("--output-dir"), "DIR", "crashes go to DIR/crashes/", defaults.outputDir.toString()) { c, _, v ->
            CliResult.Ok(c.copy(outputDir = Paths.get(v!!)))
        },
        Flag(
            listOf("--run-timeout"),
            "MS",
            "per-run timeout in milliseconds",
            defaults.runTimeoutMillis.toString()
        ) { c, f, v ->
            val n = v!!.toLongOrNull()
            // A non-positive limit makes waitFor return at once, force-killing every run as a timeout.
            when {
                n == null -> badNumber(f)
                n <= 0 -> usage("flag $f must be positive")
                else -> CliResult.Ok(c.copy(runTimeoutMillis = n))
            }
        },
        Flag(listOf("--random-seed"), "LONG", "seed for random choices", defaults.randomSeed.toString()) { c, f, v ->
            v!!.toLongOrNull()?.let { CliResult.Ok(c.copy(randomSeed = it)) } ?: badNumber(f)
        },
        Flag(listOf("--help", "-h"), null, "show this help", null) { _, _, _ -> CliResult.Help }
    )
}

/** Parses the command line into a [FuzzerConfig] via [CliSpec.flags]; any other argument is a usage error. */
internal fun parseArgs(args: List<String>): CliResult {
    var config = FuzzerConfig()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        val flag = CliSpec.flags.firstOrNull { arg in it.names }
            ?: return usage(if (arg.startsWith("-")) "unknown flag '$arg'" else "unexpected argument '$arg'")
        val value: String?
        if (flag.valueLabel != null) {
            value = args.getOrNull(i + 1) ?: return missing(arg)
            i++
        } else {
            value = null
        }
        when (val outcome = flag.apply(config, arg, value)) {
            is CliResult.Ok -> config = outcome.config
            else -> return outcome
        }
        i++
    }
    return validate(config)
}

/** Cross-flag checks that can only run once every flag is parsed. */
private fun validate(config: FuzzerConfig): CliResult {
    if (config.minLength > config.maxLength) {
        return usage("--min-length (${config.minLength}) must not exceed --max-length (${config.maxLength})")
    }
    if (config.grammarMinNonTerminals > config.grammarMaxNonTerminals) {
        return usage(
            "--grammar-min-nonterminals (${config.grammarMinNonTerminals}) must not exceed " +
                "--grammar-max-nonterminals (${config.grammarMaxNonTerminals})"
        )
    }
    return CliResult.Ok(config)
}

/** The left column of a help line: the flag's name(s) and value placeholder. */
private fun usageColumn(flag: Flag): String = flag.names.joinToString(", ") + (flag.valueLabel?.let { " $it" } ?: "")

/** Prints the usage text, generated from [CliSpec.flags]. */
internal fun printHelp(stream: PrintStream) {
    val width = CliSpec.flags.maxOf { usageColumn(it).length }
    val flagLines = CliSpec.flags.joinToString("\n        ") { flag ->
        val default = flag.defaultText?.let { " (default: $it)" } ?: ""
        "  ${usageColumn(flag).padEnd(width)}  ${flag.help}$default"
    }
    stream.println(
        """
        Usage: kt-fuzzer [flags]

        Fuzzes the target described by --target: it feeds generated input to the target on stdin and
        treats any exit code outside the target's expected set, or a kill by a signal, as a crash.
        Crashing inputs are saved under <output-dir>/crashes/.

        $flagLines

        Successful runs write the banner and summary to stdout. The machine-readable campaign
        summary is always written to <output-dir>/campaign-summary.json. Usage errors and
        file-write warnings go to stderr.
        """.trimIndent()
    )
}
