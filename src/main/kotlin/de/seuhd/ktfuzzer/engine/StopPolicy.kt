package de.seuhd.ktfuzzer.engine

import de.seuhd.ktfuzzer.report.CampaignStats
import java.util.concurrent.TimeUnit

/**
 * Campaign stop policy and its precedence.
 *
 * The policy checks repeated launch failures, then first crash, then execution limit, then time
 * limit. A null limit is ignored.
 *
 * @param maxExecutions maximum number of executions, or null for no limit
 * @param timeLimitMillis maximum wall-clock runtime in milliseconds, or null for no limit
 * @param stopOnCrash true to stop after the first crash
 */
internal class StopPolicy(
    private val maxExecutions: Long?,
    private val timeLimitMillis: Long?,
    private val stopOnCrash: Boolean
) {
    /** Returns why the campaign should stop now, or null to continue. */
    fun getStopReason(stats: CampaignStats, clock: Clock): StopReason? = when {
        stats.consecutiveErrors >= MAX_CONSECUTIVE_ERRORS -> StopReason.LAUNCH_FAILURES
        stopOnCrash && stats.crashes > 0 -> StopReason.FIRST_CRASH
        (maxExecutions != null) && (stats.executions >= maxExecutions) -> StopReason.MAX_EXECUTIONS
        (timeLimitMillis != null) && (elapsedTimeMillis(stats, clock) >= timeLimitMillis) -> StopReason.TIME_LIMIT
        else -> null
    }

    /** Returns elapsed campaign time in milliseconds. */
    private fun elapsedTimeMillis(stats: CampaignStats, clock: Clock): Long =
        TimeUnit.NANOSECONDS.toMillis(clock.nanoTime() - stats.startNanos)

    companion object {
        /**
         * Stop after this many launch failures in a row, so a target that never starts ends the
         * campaign before it spends the whole execution budget. Any started run resets the count.
         */
        const val MAX_CONSECUTIVE_ERRORS = 10L
    }
}
