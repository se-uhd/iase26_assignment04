package de.seuhd.ktfuzzer.engine

import de.seuhd.ktfuzzer.exec.ExecResult
import de.seuhd.ktfuzzer.exec.Target
import de.seuhd.ktfuzzer.mode.Fuzzer
import de.seuhd.ktfuzzer.report.CampaignStats
import java.io.IOException
import kotlin.random.Random

/**
 * Runs fuzzing inputs against a target until a stop condition is reached.
 * Records the results and crashes.
 *
 * @param fuzzer generates one input per execution.
 * @param target runs each input and returns its outcome.
 * @param stopPolicy decides when the campaign ends.
 * @param crashSink stores unique crashing inputs.
 * @param campaignStats holds the running counters.
 * @param random is injected so fuzzing runs can be reproduced.
 * @param clock is injected so time-based stop checks can be tested.
 */
internal class Harness(
    private val fuzzer: Fuzzer,
    private val target: Target,
    private val stopPolicy: StopPolicy,
    private val crashSink: CrashSink,
    private val campaignStats: CampaignStats,
    private val random: Random,
    private val clock: Clock
) {
    /** Runs until a stop condition is met, then returns why the campaign stopped. */
    fun run(): StopReason {
        while (true) {
            // `?.let` runs only for a non-null stop reason; `return` exits run(), not just the lambda.
            stopPolicy.getStopReason(campaignStats, clock)?.let { stopReason -> return stopReason }
            val input = fuzzer.fuzz(random)
            val result = target.run(input)
            campaignStats.record(result)
            if (result is ExecResult.Crash) {
                saveCrash(input, result)
            }
        }
    }

    /**
     * Stores this crashing input if it is new. The sink returns false for duplicates.
     * If writing fails, record that failure and keep fuzzing.
     */
    private fun saveCrash(input: String, crash: ExecResult.Crash) {
        try {
            if (crashSink.save(input, crash.exitCode, crash.stdout, crash.stderr)) {
                campaignStats.recordUniqueCrash(crash.exitCode)
            }
        } catch (_: IOException) {
            campaignStats.recordCrashWriteFailure()
        }
    }
}
