package de.seuhd.ktfuzzer.exec

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs one target process per input and maps its exit code to an [ExecResult].
 *
 * Separate daemon threads read `stdout` and `stderr` while the process runs. A child process can
 * block when a `Process` pipe buffer fills.
 *
 * @param binary the target executable.
 * @param runTimeoutMillis timeout for one run. Processes that exceed it are killed and reported as
 *   [ExecResult.Timeout].
 * @param expectedExitCodes exit codes that produce [ExecResult.Expected]; all others produce
 *   [ExecResult.Crash].
 */
internal class BinaryTarget(
    private val binary: Path,
    private val runTimeoutMillis: Long,
    private val expectedExitCodes: Set<Int> = TargetConfig.DEFAULT_EXPECTED_EXIT_CODES
) : Target {
    /**
     * Runs the target once.
     *
     * @param input text written to stdin as UTF-8.
     * @return [ExecResult.Error] if the process cannot start, [ExecResult.Timeout] if it runs too
     *   long, [ExecResult.Expected] for an expected exit code, or [ExecResult.Crash] otherwise.
     */
    override fun run(input: String): ExecResult {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val process =
            try {
                ProcessBuilder(binary.toString()).start()
            } catch (e: IOException) {
                // ProcessBuilder failed before creating a child process (e.g., missing binary, not executable); report error message.
                return ExecResult.Error(e.message ?: e.toString())
            }

        // If the child exits before reading all input, stdin closes. Such a writing failure should not
        // replace the child's exit code result.
        startDaemonIoThread {
            process.outputStream.use { it.write(bytes) }
        }

        // A child blocks once it fills the OS pipe buffer, so read stdout and stderr while it runs.
        val stdout = ByteArrayOutputStream()
        val stdoutThread =
            startDaemonIoThread {
                process.inputStream.use { it.copyTo(stdout) }
            }
        val stderr = ByteArrayOutputStream()
        val stderrThread =
            startDaemonIoThread {
                process.errorStream.use { it.copyTo(stderr) }
            }

        val finished = process.waitFor(runTimeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            // Destroy descendants too; inherited stdout/stderr handles might keep readers from reaching EOF.
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            process.waitFor(CLEANUP_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            return ExecResult.Timeout
        }

        // Do not wait forever if a reader thread stays blocked on a process stream.
        stdoutThread.join(CLEANUP_WAIT_MILLIS)
        stderrThread.join(CLEANUP_WAIT_MILLIS)

        val exitCode = process.exitValue()
        return if (exitCode in expectedExitCodes) {
            ExecResult.Expected(exitCode)
        } else {
            // Attach captured output only for exit codes outside the configured allowed set.
            ExecResult.Crash(exitCode, stdout.toString(Charsets.UTF_8), stderr.toString(Charsets.UTF_8))
        }
    }

    /** Starts a daemon thread for one process stream operation. */
    private fun startDaemonIoThread(operation: () -> Unit): Thread = Thread {
        try {
            operation()
        } catch (_: IOException) {
            // The child process can close a pipe while this helper is using it.
        }
    }.apply {
        isDaemon = true // This helper must not keep the JVM alive if process I/O gets stuck.
        start()
    }

    private companion object {
        // After the target exits or is killed, wait at most 1s for process termination and stream
        // reader threads to finish.
        const val CLEANUP_WAIT_MILLIS = 1_000L
    }
}
