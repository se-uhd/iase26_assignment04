package de.seuhd.ktfuzzer.report

import java.nio.file.Path

/**
 * Run-scoped values shared by console output and the JSON summary.
 *
 * @property modeLabel the fuzzing mode, lowercased.
 * @property randomSeed the seed every random choice is drawn from.
 * @property targetName the target binary's filename, or a fallback label.
 * @property crashDir the directory the crash files are written under.
 * @property exitCodeLabels human-readable names for specific expected exit codes (e.g. 0 -> accepted).
 */
internal data class RunMetadata(
    val modeLabel: String,
    val randomSeed: Long,
    val targetName: String,
    val crashDir: Path,
    val exitCodeLabels: Map<Int, String>
)
