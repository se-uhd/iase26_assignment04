package de.seuhd.ktfuzzer.engine

import de.seuhd.ktfuzzer.report.CampaignStats
import java.util.concurrent.TimeUnit

/**
 * Campaign stop policy and its precedence.
 *
 * The policy checks first crash, then execution limit, then time limit. A null limit is ignored.
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
        stopOnCrash && stats.crashes > 0 -> StopReason.FIRST_CRASH
        (maxExecutions != null) && (stats.executions >= maxExecutions) -> StopReason.MAX_EXECUTIONS
        (timeLimitMillis != null) && (elapsedTimeMillis(stats, clock) >= timeLimitMillis) -> StopReason.TIME_LIMIT
        else -> null
    }

    /** Returns elapsed campaign time in milliseconds. */
    private fun elapsedTimeMillis(stats: CampaignStats, clock: Clock): Long =
        TimeUnit.NANOSECONDS.toMillis(clock.nanoTime() - stats.startNanos)
}
