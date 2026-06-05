package de.seuhd.ktfuzzer.report

import de.seuhd.ktfuzzer.exec.ExecResult

/**
 * Counts execution outcomes and crash results for one fuzzing campaign.
 *
 * @property startNanos monotonic campaign start time used for elapsed time and throughput.
 */
internal class CampaignStats(val startNanos: Long) {
    /** Total executions so far. */
    var executions: Long = 0
        private set

    /** Executions whose exit code fell outside the expected set. */
    var crashes: Long = 0
        private set

    /** Crashes that were new when saved (a content hash not seen before). */
    var uniqueCrashes: Long = 0
        private set

    /** Crashing inputs that could not be saved because the crash sink failed. */
    var crashWriteFailures: Long = 0
        private set

    /** Executions that did not finish within the per-run timeout. */
    var timeouts: Long = 0
        private set

    /** Executions that could not be started (e.g., the binary was missing). */
    var errors: Long = 0
        private set

    private val expectedExitCounts = LinkedHashMap<Int, Long>()

    /** Non-crash runs per exit code. */
    val expectedCountsByExitCode: Map<Int, Long> get() = expectedExitCounts

    private val uniqueCrashExitCounts = HashMap<Int, Long>()

    /** Unique crashes per exit code. */
    val uniqueCrashCountsByExitCode: Map<Int, Long> get() = uniqueCrashExitCounts

    /** Records one execution outcome, updating the matching count. */
    fun record(result: ExecResult) {
        executions++
        when (result) {
            is ExecResult.Expected -> expectedExitCounts.merge(result.exitCode, 1L, Long::plus)
            is ExecResult.Crash -> crashes++
            ExecResult.Timeout -> timeouts++
            is ExecResult.Error -> errors++
        }
    }

    /** Records that a crash with [exitCode] was new (i.e., saved to its own folder). */
    fun recordUniqueCrash(exitCode: Int) {
        uniqueCrashes++
        uniqueCrashExitCounts.merge(exitCode, 1L, Long::plus)
    }

    /** Records that the crash sink failed while saving a crashing input. */
    fun recordCrashWriteFailure() {
        crashWriteFailures++
    }
}
