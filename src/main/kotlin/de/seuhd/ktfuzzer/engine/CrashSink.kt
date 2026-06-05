package de.seuhd.ktfuzzer.engine

/**
 * Functional interface for a crash sink that stores the inputs that crashed a target, so each distinct
 * crash can be reproduced. The harness calls [save] for every input whose run exited with a code
 * outside the target's expected set.
 */
internal fun interface CrashSink {
    /**
     * Records [input], the string that crashed the target, with its [exitCode] and the [stdout] and
     * [stderr] the target produced on that run. Returns true if this crash is new, or false if one
     * with the same exit code and input was already stored. Storage failures are reported by throwing
     * [java.io.IOException].
     */
    fun save(input: String, exitCode: Int, stdout: String, stderr: String): Boolean
}
