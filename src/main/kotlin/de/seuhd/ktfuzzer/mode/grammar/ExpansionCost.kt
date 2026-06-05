package de.seuhd.ktfuzzer.mode.grammar

/**
 * Computes the expansion cost of a grammar, in the style of The Fuzzing Book's `symbol_cost` /
 * `expansion_cost` (see <https://www.fuzzingbook.org/html/GrammarFuzzer.html>). Both costs treat
 * re-entering a symbol that is already being expanded as [INFINITY], because that path has no
 * finite terminating derivation. They differ in how they resolve the choices an expansion leaves
 * open:
 *
 *  - [minCost] is the cheapest terminating cost: optionals absent, repetitions empty, the shortest
 *    alternative of a choice. The grammar fuzzer uses it to close a derivation.
 *  - [maxCost] is the maximum cost: optionals present, repetitions taken once, the longest
 *    alternative of a choice. It is [INFINITY] exactly when the expansion can keep growing by
 *    re-entering a symbol on the expansion path, so the grammar fuzzer uses it to grow a derivation.
 *    In The Fuzzing Book this is `expansion_cost` resolved with `max`, here adapted to an EBNF tree
 *    that is expanded directly rather than converted to BNF.
 *
 * A construct counts as recursive only because expanding it would re-enter a symbol already on the
 * expansion path.
 *
 * @param grammar the grammar whose construct costs are computed and cached.
 */
internal class ExpansionCost(private val grammar: Grammar) {
    /** Cache key for a non-terminal's cost: its name plus the non-terminals whose expansion encloses it. */
    private data class SymbolKey(val name: String, val onExpansionPath: Set<String>)

    /** Cache key for an expression's cost: the expression plus the non-terminals whose expansion encloses it. */
    private data class ExpressionKey(val expression: Expression, val onExpansionPath: Set<String>)

    private val symbolCostCache = mutableMapOf<SymbolKey, Int>()
    private val minCostCache = mutableMapOf<ExpressionKey, Int>()
    private val maxCostCache = mutableMapOf<ExpressionKey, Int>()

    // Reject a grammar in which some non-terminal has no terminating derivation.
    init {
        val unreachable = grammar.productions.keys.filter { symbolCost(it) == INFINITY }
        require(unreachable.isEmpty()) { "malformed grammar: no terminating derivation for $unreachable" }
    }

    /**
     * Cheapest terminating cost of expanding non-terminal [name], or [INFINITY] if [name] is on the
     * [onExpansionPath] (it is already being expanded, so continuing through it is recursion).
     */
    private fun symbolCost(name: String, onExpansionPath: Set<String> = emptySet()): Int {
        if (name in onExpansionPath) return INFINITY
        val key = SymbolKey(name, onExpansionPath)
        return symbolCostCache.getOrPut(key) { minCost(grammar.productions.getValue(name), onExpansionPath + name) }
    }

    /** Cheapest terminating cost of [expression]; [INFINITY] if it can only continue by recursion. */
    fun minCost(expression: Expression, onExpansionPath: Set<String> = emptySet()): Int =
        minCostCache.getOrPut(ExpressionKey(expression, onExpansionPath)) {
            when (expression) {
                is Expression.Terminal -> expression.text.length

                is Expression.NonTerminal -> symbolCost(expression.name, onExpansionPath)

                is Expression.Group -> minCost(expression.body, onExpansionPath)

                // cheapest form is absent
                is Expression.Optional -> 0

                // cheapest form is empty
                is Expression.Repetition -> 0

                is Expression.Choice -> expression.alternatives.minOf { minCost(it, onExpansionPath) }

                is Expression.Sequence -> sumOrInfinity(expression.parts) { minCost(it, onExpansionPath) }
            }
        }

    /**
     * Maximum cost of [expression], taking the growing branch at every choice point: optionals
     * present, repetitions taken once, the longest alternative. [INFINITY] exactly when [expression]
     * can grow without a bound, that is, when expanding it would re-enter a symbol on the
     * [onExpansionPath].
     */
    fun maxCost(expression: Expression, onExpansionPath: Set<String> = emptySet()): Int =
        maxCostCache.getOrPut(ExpressionKey(expression, onExpansionPath)) {
            when (expression) {
                is Expression.Terminal -> expression.text.length

                is Expression.NonTerminal -> symbolGrowthCost(expression.name, onExpansionPath)

                is Expression.Group -> maxCost(expression.body, onExpansionPath)

                // costliest form is present
                is Expression.Optional -> maxCost(expression.body, onExpansionPath)

                // costliest form takes one instance
                is Expression.Repetition -> maxCost(expression.body, onExpansionPath)

                is Expression.Choice -> expression.alternatives.maxOf { maxCost(it, onExpansionPath) }

                is Expression.Sequence -> sumOrInfinity(expression.parts) { maxCost(it, onExpansionPath) }
            }
        }

    /** Maximum cost of expanding non-terminal [name], or [INFINITY] if [name] is already on the [onExpansionPath]. */
    private fun symbolGrowthCost(name: String, onExpansionPath: Set<String>): Int =
        if (name in onExpansionPath) INFINITY else maxCost(grammar.productions.getValue(name), onExpansionPath + name)

    /** Sums the cost of each part, returning [INFINITY] if any part costs [INFINITY]. */
    private inline fun sumOrInfinity(parts: List<Expression>, costOf: (Expression) -> Int): Int = parts.fold(
        0
    ) { acc, part ->
        val partCost = costOf(part)
        if (acc == INFINITY || partCost == INFINITY) INFINITY else acc + partCost
    }

    companion object {
        /**
         * The cost of an expansion that can only continue by recursing (no terminating derivation). Adding
         * a finite cost to it would overflow, so [sumOrInfinity] returns it unchanged instead.
         */
        const val INFINITY: Int = Int.MAX_VALUE
    }
}
