package de.seuhd.ktfuzzer.engine

/**
 * Source of time in nanoseconds, used for elapsed time, time limits, and executions-per-second reporting.
 */
internal fun interface Clock {
    /** Returns the current value of a monotonic nanosecond timer. */
    fun nanoTime(): Long

    companion object {
        /** System monotonic clock used by production runs. */
        val SYSTEM: Clock = Clock { System.nanoTime() }
    }
}
