package de.seuhd.ktfuzzer.report

import de.seuhd.ktfuzzer.engine.StopReason
import de.seuhd.ktfuzzer.exec.ExecResult
import de.seuhd.ktfuzzer.exec.Signal
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class ConsoleReporterTest {
    private val crashDir = Paths.get("output", "crashes")

    private fun capture(block: (PrintStream) -> Unit): String {
        val buffer = ByteArrayOutputStream()
        block(PrintStream(buffer))
        return buffer.toString()
    }

    private fun statsOf(startNanos: Long = 0L, build: CampaignStats.() -> Unit): CampaignStats =
        CampaignStats(startNanos).apply(build)

    @Test
    fun `the console renderers print for a clean run and a crash run`() {
        val metadata =
            RunMetadata(
                "random",
                randomSeed = 42L,
                targetName = "toml_parser",
                crashDir = crashDir,
                exitCodeLabels = mapOf(0 to "accepted", 1 to "rejected")
            )
        val cleanStats = statsOf { record(ExecResult.Expected(0)) }
        val crashStats =
            statsOf {
                record(ExecResult.Crash(Signal.SIGSEGV.exitCode))
                recordUniqueCrash(Signal.SIGSEGV.exitCode)
                recordCrashWriteFailure()
            }

        val outputs =
            listOf(
                capture {
                    ConsoleReporter.renderStart(
                        it,
                        metadata,
                        maxExecutions = 10_000,
                        timeLimitMillis = null,
                        stopOnCrash = true
                    )
                },
                capture {
                    ConsoleReporter.renderSummary(it, metadata, cleanStats, StopReason.MAX_EXECUTIONS, nowNanos = 1L)
                },
                capture {
                    ConsoleReporter.renderSummary(it, metadata, crashStats, StopReason.FIRST_CRASH, nowNanos = 1L)
                }
            )

        assertTrue(outputs.all { it.isNotBlank() }, "each renderer should write something")
        assertTrue(outputs.any { "not saved" in it }, "write failures should be reported")
    }
}
