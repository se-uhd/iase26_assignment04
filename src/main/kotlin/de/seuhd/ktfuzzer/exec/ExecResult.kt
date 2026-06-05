package de.seuhd.ktfuzzer.exec

/** The outcome of running the target once on one input. */
internal sealed interface ExecResult {
    /**
     * The target ran and returned an exit code in the expected set (no crash).
     *
     * @property exitCode the code the target returned.
     */
    data class Expected(val exitCode: Int) : ExecResult

    /**
     * The target crashed: killed by a signal, or returned an exit code outside the expected set.
     *
     * @property exitCode the crash exit code (128 + signal when killed by a signal).
     * @property stdout what the target wrote to stdout before it crashed.
     * @property stderr what the target wrote to stderr before it crashed (often the abort message).
     */
    data class Crash(val exitCode: Int, val stdout: String = "", val stderr: String = "") : ExecResult

    /** The process did not finish within the timeout; it was force-killed. */
    data object Timeout : ExecResult

    /**
     * The process could not be spawned (binary missing, not executable, ...).
     *
     * @property message why the process could not be started.
     */
    data class Error(val message: String) : ExecResult
}
