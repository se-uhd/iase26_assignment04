package de.seuhd.ktfuzzer.exec

/**
 * Exit codes for target crashes that a common Unix signal produces (128 + the signal number).
 *
 * @property exitCode the process exit code.
 */
internal enum class Signal(val exitCode: Int) {
    SIGILL(132),
    SIGTRAP(133),
    SIGABRT(134),
    SIGFPE(136),
    SIGBUS(138),
    SIGSEGV(139);

    companion object {
        /** Returns the signal name for a known crash [exitCode], or `exit <code>` otherwise. */
        fun labelFor(exitCode: Int): String = entries.firstOrNull { it.exitCode == exitCode }?.name ?: "exit $exitCode"
    }
}
