package de.seuhd.ktfuzzer

/** The result of parsing the command line. */
internal sealed interface CliResult {
    companion object {
        /** Exit code for usage errors. */
        const val USAGE_ERROR = 2
    }

    data object Help : CliResult

    /**
     * Parsing failed; the CLI should print [message] and exit with [code].
     *
     * @property message the usage-error message to print.
     * @property code the process exit code to return.
     */
    data class Error(val message: String, val code: Int) : CliResult

    /**
     * Parsing succeeded.
     *
     * @property config the parsed run configuration.
     */
    data class Ok(val config: FuzzerConfig) : CliResult
}
