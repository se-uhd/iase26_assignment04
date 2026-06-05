package de.seuhd.ktfuzzer.report

import de.seuhd.ktfuzzer.engine.StopReason
import de.seuhd.ktfuzzer.exec.Signal
import java.io.PrintStream
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Builds the human-readable start banner and end summary written to stdout by successful CLI runs.
 * Both are a title line followed by an aligned `  key   value` block.
 */
internal object ConsoleReporter {
    // Above this elapsed time the headline shows seconds with one decimal instead of whole milliseconds.
    private const val SECONDS_THRESHOLD_MILLIS = 10_000L

    /** The run configuration as an aligned block, printed before the run starts. */
    fun renderStart(
        output: PrintStream,
        metadata: RunMetadata,
        maxExecutions: Long?,
        timeLimitMillis: Long?,
        stopOnCrash: Boolean
    ) {
        output.println("kt-fuzzer")
        printBlock(
            output,
            listOf(
                "mode" to metadata.modeLabel,
                "seed" to metadata.randomSeed.toString(),
                "target" to metadata.targetName,
                "max-executions" to (maxExecutions?.toString() ?: "unbounded"),
                "time-limit" to (timeLimitMillis?.let { "${it}ms" } ?: "(none)"),
                "stop-on-crash" to (if (stopOnCrash) "yes" else "no"),
                "crash-dir" to crashDirLabel(metadata)
            )
        )
    }

    /**
     * End-of-run report: a CRASH FOUND / NO CRASH headline, then an aligned block with the stop
     * reason, throughput, the per-verdict counts, the crashes-by-exit-code breakdown, and where
     * crashes were saved.
     */
    fun renderSummary(
        output: PrintStream,
        metadata: RunMetadata,
        stats: CampaignStats,
        stopReason: StopReason,
        nowNanos: Long
    ) {
        val elapsed = (nowNanos - stats.startNanos).nanoseconds
        val throughput = "${stats.executions} (${formatElapsed(elapsed)}, ${perSecond(stats.executions, elapsed)}/s)"
        val expectedCodes = (metadata.exitCodeLabels.keys + stats.expectedCountsByExitCode.keys).toSortedSet()
        val crashed = if (stats.crashes > 0) "${stats.crashes} (${stats.uniqueCrashes} unique)" else "0"
        val rows = buildList {
            add("stopped at" to stopReason.summaryText)
            add("executions" to throughput)
            expectedCodes.forEach { code ->
                add((metadata.exitCodeLabels[code] ?: "exit$code") to "${stats.expectedCountsByExitCode[code] ?: 0}")
            }
            add("crashed" to crashed)
            add("timeouts" to stats.timeouts.toString())
            add("errors" to stats.errors.toString())
            if (stats.crashes > 0 && stats.uniqueCrashCountsByExitCode.isNotEmpty()) {
                crashExitRows(stats).forEachIndexed { index, row ->
                    add((if (index == 0) "exit codes" else "") to row)
                }
            }
            if (stats.uniqueCrashes > 0) add("saved to" to crashDirLabel(metadata))
            if (stats.crashWriteFailures > 0) add("not saved" to stats.crashWriteFailures.toString())
        }
        output.println()
        output.println(headline(stats.crashes))
        printBlock(output, rows)
    }

    /** Prints [rows] as an aligned `  key   value` block, padding each key to the widest one. */
    private fun printBlock(output: PrintStream, rows: List<Pair<String, String>>) {
        val width = rows.maxOf { it.first.length }
        rows.forEach { (key, value) -> output.println("  ${key.padEnd(width)}  $value") }
    }

    /** The headline: a single `CRASH FOUND`, `<n> CRASHES FOUND` for several, or `NO CRASHES FOUND`. */
    private fun headline(crashes: Long): String = when {
        crashes == 0L -> "NO CRASHES FOUND"
        crashes == 1L -> "CRASH FOUND"
        else -> "$crashes CRASHES FOUND"
    }

    /** The crash directory with a trailing separator, so it reads as a directory. */
    private fun crashDirLabel(metadata: RunMetadata): String =
        "${metadata.crashDir}${metadata.crashDir.fileSystem.separator}"

    /** Unique crash counts as one "<label> <n>" entry per exit code, highest count first. */
    private fun crashExitRows(stats: CampaignStats): List<String> = stats.uniqueCrashCountsByExitCode.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
        .map { "${Signal.labelFor(it.key)} ${it.value}" }

    /** Executions per second over [elapsed], or 0 before any time has elapsed. */
    private fun perSecond(executions: Long, elapsed: Duration): Long {
        val seconds = elapsed.toDouble(DurationUnit.SECONDS)
        return if (seconds > 0) (executions / seconds).toLong() else 0L
    }

    /** Formats [elapsed] as seconds with one decimal once it reaches [SECONDS_THRESHOLD_MILLIS], else milliseconds. */
    private fun formatElapsed(elapsed: Duration): String =
        if (elapsed.inWholeMilliseconds >= SECONDS_THRESHOLD_MILLIS) {
            String.format(Locale.ROOT, "%.1fs", elapsed.toDouble(DurationUnit.SECONDS))
        } else {
            "${elapsed.inWholeMilliseconds}ms"
        }
}
