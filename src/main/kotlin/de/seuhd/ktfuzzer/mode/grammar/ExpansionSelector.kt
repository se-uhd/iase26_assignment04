package de.seuhd.ktfuzzer.mode.grammar

import kotlin.random.Random

/**
 * Cost- and strategy-based selection: which recursive nodes can grow, which pending node to expand
 * next, and how to pick an expression by cost. Holds [cost] and the recursion strategy.
 *
 * @param cost the construct costs used to rank expansions.
 * @param recursionStrategy how recursive branches grow: BROAD siblings or one LINEAR chain.
 */
internal class ExpansionSelector(private val cost: ExpansionCost, private val recursionStrategy: RecursionStrategy) {
    /** True if expanding [expression] on [path] would re-enter a symbol already on the path. */
    fun canGrowRecursively(expression: Expression, path: List<String>): Boolean =
        cost.maxCost(expression, path.toSet()) == ExpansionCost.INFINITY

    /** Picks the pending node to expand next: a recursive phase defers to the recursion strategy, else uniform. */
    fun chooseNodeToExpand(candidates: List<DerivationNode>, random: Random, preferRecursive: Boolean): DerivationNode =
        if (preferRecursive) {
            recursionStrategy.choose(candidates, random)
        } else {
            candidates[random.nextInt(candidates.size)]
        }

    /** Picks the [items] entry with the highest (or lowest) [costOf], breaking ties uniformly. */
    fun <T> chooseByCost(items: List<T>, random: Random, maximize: Boolean, costOf: (T) -> Int): T {
        val scored = items.map { it to costOf(it) }
        val target = if (maximize) scored.maxOf { it.second } else scored.minOf { it.second }
        val tied = scored.filter { it.second == target }
        return tied[random.nextInt(tied.size)].first
    }
}
