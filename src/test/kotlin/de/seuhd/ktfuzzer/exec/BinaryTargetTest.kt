package de.seuhd.ktfuzzer.exec

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryTargetTest {
    @Test
    fun `expected exit codes are normal runs`(@TempDir dir: Path) {
        for (code in TargetConfig.DEFAULT_EXPECTED_EXIT_CODES) {
            val target = BinaryTarget(script(dir, "expected-$code", "exit $code"), RUN_TIMEOUT_MILLIS)

            assertEquals(ExecResult.Expected(code), target.run(""))
        }
    }

    @Test
    fun `any code outside the expected set is a crash that preserves output`(@TempDir dir: Path) {
        val target =
            BinaryTarget(
                script(
                    dir,
                    "crash-with-output",
                    """
                    printf 'target stdout'
                    printf 'target stderr' >&2
                    exit ${Signal.SIGSEGV.exitCode}
                    """.trimIndent()
                ),
                RUN_TIMEOUT_MILLIS
            )

        assertEquals(ExecResult.Crash(Signal.SIGSEGV.exitCode, "target stdout", "target stderr"), target.run(""))
    }

    @Test
    fun `the expected set is configurable`(@TempDir dir: Path) {
        val acceptsTwo =
            BinaryTarget(
                script(dir, "custom-expected", "exit 2"),
                RUN_TIMEOUT_MILLIS,
                expectedExitCodes = setOf(0, 1, 2)
            )
        val rejectsOne =
            BinaryTarget(
                script(dir, "custom-crash", "exit 1"),
                RUN_TIMEOUT_MILLIS,
                expectedExitCodes = setOf(0)
            )

        assertEquals(ExecResult.Expected(2), acceptsTwo.run(""))
        assertEquals(ExecResult.Crash(1), rejectsOne.run(""))
    }

    private fun script(dir: Path, name: String, body: String): Path {
        assumeFalse(isWindows(), "temporary shell scripts are Unix-only")
        val path = dir.resolve(name)
        Files.writeString(path, "#!/bin/sh\n$body\n")
        check(path.toFile().setExecutable(true)) { "could not make $path executable" }
        return path
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

    private companion object {
        const val RUN_TIMEOUT_MILLIS = 1_000L
    }
}
