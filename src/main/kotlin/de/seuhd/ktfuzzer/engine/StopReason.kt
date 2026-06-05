package de.seuhd.ktfuzzer.engine

/**
 * End state of a completed fuzzing campaign.
 *
 * @property code value written to JSON `stop`
 * @property summaryText short reason shown in the console summary's "stopped at" row.
 */
internal enum class StopReason(val code: String, val summaryText: String) {
    MAX_EXECUTIONS("max-executions", "max executions"),
    TIME_LIMIT("time-limit", "time limit"),
    FIRST_CRASH("first-crash", "first crash")
}
