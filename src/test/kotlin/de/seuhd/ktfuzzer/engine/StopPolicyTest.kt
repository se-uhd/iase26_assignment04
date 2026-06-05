package de.seuhd.ktfuzzer.engine

import de.seuhd.ktfuzzer.exec.ExecResult
import de.seuhd.ktfuzzer.report.CampaignStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StopPolicyTest {
    private val frozen = Clock { 0L }

    private fun stats(executions: Int = 0, crashes: Int = 0): CampaignStats = CampaignStats(0L).apply {
        repeat(executions) { record(ExecResult.Expected(0)) }
        repeat(crashes) { record(ExecResult.Crash(134)) }
    }

    @Test
    fun `an unbounded policy never stops the run`() {
        val policy = StopPolicy(maxExecutions = null, timeLimitMillis = null, stopOnCrash = false)
        assertNull(policy.getStopReason(stats(executions = 1000), frozen))
    }

    @Test
    fun `max-executions stops once the cap is reached`() {
        val policy = StopPolicy(maxExecutions = 10, timeLimitMillis = null, stopOnCrash = false)
        assertNull(policy.getStopReason(stats(executions = 9), frozen))
        assertEquals(StopReason.MAX_EXECUTIONS, policy.getStopReason(stats(executions = 10), frozen))
    }

    @Test
    fun `stop-on-crash stops at the first crash and takes precedence`() {
        val policy = StopPolicy(maxExecutions = 10, timeLimitMillis = null, stopOnCrash = true)
        assertNull(policy.getStopReason(stats(executions = 5), frozen))
        assertEquals(StopReason.FIRST_CRASH, policy.getStopReason(stats(executions = 5, crashes = 1), frozen))
    }

    @Test
    fun `time-limit stops once the wall-clock limit elapses`() {
        val policy = StopPolicy(maxExecutions = null, timeLimitMillis = 100, stopOnCrash = false)
        val started = stats(executions = 1) // RunStats starts at nano 0
        assertNull(policy.getStopReason(started, Clock { 50_000_000L })) // 50 ms < 100 ms
        // 100 ms reached
        assertEquals(StopReason.TIME_LIMIT, policy.getStopReason(started, Clock { 100_000_000L }))
    }

    @Test
    fun `max-executions takes precedence over time-limit when both bounds are met`() {
        val policy = StopPolicy(maxExecutions = 10, timeLimitMillis = 100, stopOnCrash = false)
        // Both fire at once: 10 executions reached and 100 ms elapsed. The execution bound wins.
        assertEquals(
            StopReason.MAX_EXECUTIONS,
            policy.getStopReason(stats(executions = 10), Clock { 100_000_000L })
        )
    }
}
