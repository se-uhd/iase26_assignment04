package de.seuhd.ktfuzzer.exec

/** A program under test; production uses [BinaryTarget], tests pass a fake. */
internal fun interface Target {
    /** Runs the target once on [input] and returns the outcome. */
    fun run(input: String): ExecResult
}
