package de.seuhd.ktfuzzer.engine

import de.seuhd.ktfuzzer.TestFixtures
import de.seuhd.ktfuzzer.exec.ExecResult
import de.seuhd.ktfuzzer.exec.Target
import de.seuhd.ktfuzzer.mode.Fuzzer
import de.seuhd.ktfuzzer.mode.random.RandomFuzzer
import de.seuhd.ktfuzzer.report.CampaignStats
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Generic harness-loop behavior with a fake target: crash de-duplication and run reproducibility. */
class HarnessTest {
    private class RecordingSink : CrashSink {
        val crashCodes = linkedSetOf<Int>()

        override fun save(input: String, exitCode: Int, stdout: String, stderr: String): Boolean =
            crashCodes.add(exitCode)
    }

    private class FailingSink : CrashSink {
        override fun save(input: String, exitCode: Int, stdout: String, stderr: String): Boolean =
            throw IOException("cannot write crash")
    }

    private fun run(fuzzer: Fuzzer, target: Target, maxExecutions: Long, sink: CrashSink): CampaignStats {
        val stats = CampaignStats(0L)
        Harness(
            fuzzer = fuzzer,
            target = target,
            stopPolicy = StopPolicy(maxExecutions, null, stopOnCrash = false),
            crashSink = sink,
            campaignStats = stats,
            random = Random(TestFixtures.SEED),
            clock = Clock { 0L }
        ).run()
        return stats
    }

    @Test
    fun `crashes are deduplicated and written once per unique input`(@TempDir dir: Path) {
        val crashDir = dir.resolve("crashes")
        val constantInput = Fuzzer { "the same input\n" }
        val alwaysCrash = Target { ExecResult.Crash(CRASH_CODE) }
        val stats = run(constantInput, alwaysCrash, maxExecutions = 50, sink = FileCrashSink(crashDir))
        assertEquals(1, stats.uniqueCrashes, "the same crashing input should be unique once")
        val bucket = crashDir.resolve("exit$CRASH_CODE")
        assertEquals(1, Files.list(bucket).use { it.toList() }.size, "one crash file in the exit bucket")
    }

    @Test
    fun `crash write failures are counted and do not stop the campaign`() {
        val constantInput = Fuzzer { "not saved\n" }
        val alwaysCrash = Target { ExecResult.Crash(CRASH_CODE) }
        val stats = run(constantInput, alwaysCrash, maxExecutions = 3, sink = FailingSink())
        assertEquals(3, stats.crashes)
        assertEquals(0, stats.uniqueCrashes)
        assertEquals(3, stats.crashWriteFailures)
    }

    @Test
    fun `the same seed produces an identical run`() {
        // The target crashes on longer inputs, so the run is a deterministic mix of crashes and clean
        // runs driven only by the seed.
        val target = Target { if (it.length > SHORT) ExecResult.Crash(CRASH_CODE) else ExecResult.Expected(0) }
        val a = RecordingSink()
        val b = RecordingSink()
        val alphabet = TestFixtures.ALPHABET.toList()
        val statsA = run(RandomFuzzer(alphabet), target, maxExecutions = RUNS, sink = a)
        val statsB = run(RandomFuzzer(alphabet), target, maxExecutions = RUNS, sink = b)
        assertEquals(a.crashCodes, b.crashCodes)
        assertEquals(statsA.crashes, statsB.crashes)
    }

    private companion object {
        const val CRASH_CODE = 139
        const val SHORT = 4
        const val RUNS = 2_000L
    }
}
