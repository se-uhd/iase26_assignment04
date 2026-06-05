package de.seuhd.ktfuzzer.mode

import kotlin.random.Random

/**
 * The generation strategy to run.
 *
 * @property cliValue value accepted by `--mode` and printed in reports.
 */
internal enum class Mode(val cliValue: String) {
    RANDOM("random"),
    MUTATIONAL("mutational"),
    GRAMMAR("grammar");

    companion object {
        /** Accepted `--mode` values, formatted for usage errors and help text. */
        val choices: String = entries.joinToString(" | ") { it.cliValue }

        /** Parses a `--mode` value, or null if it is not a known mode. */
        fun fromString(value: String): Mode? = entries.firstOrNull { it.cliValue == value.lowercase() }
    }
}

/**
 * Produces inputs to run, one per call to [fuzz]. It is black-box (no coverage feedback) and draws
 * all randomness from the given [Random], so the same `--random-seed` reproduces the same run.
 */
internal fun interface Fuzzer {
    /** Produces the next input to run. */
    fun fuzz(random: Random): String
}
