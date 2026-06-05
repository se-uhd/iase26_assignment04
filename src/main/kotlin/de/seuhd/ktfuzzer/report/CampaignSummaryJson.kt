package de.seuhd.ktfuzzer.report

import de.seuhd.ktfuzzer.engine.StopReason
import de.seuhd.ktfuzzer.exec.Signal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/** Renders the machine-readable campaign summary saved as `campaign-summary.json`. */
internal object CampaignSummaryJson {
    /** The campaign summary as a single-line JSON object. */
    fun render(metadata: RunMetadata, stats: CampaignStats, stopReason: StopReason, nowNanos: Long): String {
        val elapsed = (nowNanos - stats.startNanos).nanoseconds
        return buildJsonObject {
            put("mode", metadata.modeLabel)
            put("seed", metadata.randomSeed)
            put("target", metadata.targetName)
            put("stop", stopReason.code)
            put("executions", stats.executions)
            put("elapsedMs", elapsed.inWholeMilliseconds)
            put("executionsPerSecond", perSecond(stats.executions, elapsed))
            put("expected", expectedCounts(metadata, stats))
            put("crashed", stats.crashes)
            put("uniqueCrashes", stats.uniqueCrashes)
            put("crashWriteFailures", stats.crashWriteFailures)
            put("timeouts", stats.timeouts)
            put("errors", stats.errors)
            put("crashDir", metadata.crashDir.toString())
            put("crashExitCodes", crashCounts(stats))
        }.toString()
    }

    /** Expected-run counts keyed by exit-code label (from config, else `exit<code>`), in exit-code order. */
    private fun expectedCounts(metadata: RunMetadata, stats: CampaignStats): JsonObject {
        val codes = (metadata.exitCodeLabels.keys + stats.expectedCountsByExitCode.keys).toSortedSet()
        return buildJsonObject {
            codes.forEach { put(metadata.exitCodeLabels[it] ?: "exit$it", stats.expectedCountsByExitCode[it] ?: 0L) }
        }
    }

    /** Unique-crash counts keyed by signal name or `exit<code>`, highest count first. */
    private fun crashCounts(stats: CampaignStats): JsonObject = buildJsonObject {
        stats.uniqueCrashCountsByExitCode.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Long>> { it.value }.thenBy { it.key })
            .forEach { put(Signal.labelFor(it.key), it.value) }
    }

    /** Executions per second over [elapsed], or 0 before any time has elapsed. */
    private fun perSecond(executions: Long, elapsed: Duration): Long {
        val seconds = elapsed.toDouble(DurationUnit.SECONDS)
        return if (seconds > 0) (executions / seconds).toLong() else 0L
    }
}
