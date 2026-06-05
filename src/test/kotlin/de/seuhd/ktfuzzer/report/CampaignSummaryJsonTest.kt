package de.seuhd.ktfuzzer.report

import de.seuhd.ktfuzzer.engine.StopReason
import de.seuhd.ktfuzzer.exec.ExecResult
import de.seuhd.ktfuzzer.exec.Signal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class CampaignSummaryJsonTest {
    private val crashDir = Paths.get("output", "crashes")

    private fun statsOf(startNanos: Long = 0L, build: CampaignStats.() -> Unit): CampaignStats =
        CampaignStats(startNanos).apply(build)

    @Test
    fun `json summary exposes the stable machine-readable fields`() {
        val stats =
            statsOf {
                record(ExecResult.Expected(0))
                record(ExecResult.Expected(1))
                record(ExecResult.Crash(Signal.SIGSEGV.exitCode))
                record(ExecResult.Timeout)
                record(ExecResult.Error("spawn failed"))
                recordUniqueCrash(Signal.SIGSEGV.exitCode)
                recordUniqueCrash(Signal.SIGSEGV.exitCode)
                recordUniqueCrash(Signal.SIGABRT.exitCode)
            }

        val json =
            CampaignSummaryJson.render(
                RunMetadata(
                    "random",
                    randomSeed = 42L,
                    targetName = "a\"b\\c",
                    crashDir = crashDir,
                    exitCodeLabels = mapOf(0 to "accepted", 1 to "rejected")
                ),
                stats = stats,
                stopReason = StopReason.FIRST_CRASH,
                nowNanos = 500_000_000L
            )

        assertEquals(1, json.lineSequence().count(), "campaign-summary JSON should be one line")
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(
            setOf(
                "mode", "seed", "target", "stop", "executions", "elapsedMs", "executionsPerSecond",
                "expected", "crashed", "uniqueCrashes", "crashWriteFailures", "timeouts", "errors",
                "crashDir", "crashExitCodes"
            ),
            root.keys
        )
        assertEquals("a\"b\\c", root.getValue("target").jsonPrimitive.content)
        assertEquals(StopReason.FIRST_CRASH.code, root.getValue("stop").jsonPrimitive.content)
        assertEquals(0, root.getValue("crashWriteFailures").jsonPrimitive.long)

        val expected = root.getValue("expected").jsonObject
        assertEquals(1, expected.getValue("accepted").jsonPrimitive.long)
        assertEquals(1, expected.getValue("rejected").jsonPrimitive.long)

        val crashExitCodes = root.getValue("crashExitCodes").jsonObject
        assertEquals(2, crashExitCodes.getValue("SIGSEGV").jsonPrimitive.long)
        assertEquals(1, crashExitCodes.getValue("SIGABRT").jsonPrimitive.long)
    }
}
