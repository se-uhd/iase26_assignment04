package de.seuhd.ktfuzzer.mode.grammar

import kotlin.random.Random

/**
 * How recursive-depth generation handles sibling recursive branches.
 *
 * @property cliValue value accepted by `--grammar-recursion-strategy`.
 */
internal enum class RecursionStrategy(val cliValue: String) {
    /** Expand recursive branches uniformly, so several can grow in parallel. */
    BROAD("broad") {
        override fun choose(candidates: List<DerivationNode>, random: Random): DerivationNode =
            candidates[random.nextInt(candidates.size)]
    },

    /** Always expand the deepest pending node, so one branch grows at a time. */
    LINEAR("linear") {
        override fun choose(candidates: List<DerivationNode>, random: Random): DerivationNode {
            val deepest = candidates.maxOf { it.level }
            val deepestCandidates = candidates.filter { it.level == deepest }
            return deepestCandidates[random.nextInt(deepestCandidates.size)]
        }
    };

    /** Picks the next recursive node to expand from [candidates] under this strategy. */
    abstract fun choose(candidates: List<DerivationNode>, random: Random): DerivationNode

    companion object {
        /** Accepted `--grammar-recursion-strategy` values, formatted for usage errors and help text. */
        val choices: String = entries.joinToString(" | ") { it.cliValue }

        /** Parses a strategy name case-insensitively, returning null for an unknown value. */
        fun fromString(value: String): RecursionStrategy? = entries.firstOrNull { it.cliValue == value.lowercase() }
    }
}
