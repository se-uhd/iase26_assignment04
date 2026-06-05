package de.seuhd.ktfuzzer.mode.grammar

import de.seuhd.ktfuzzer.mode.Fuzzer
import kotlin.random.Random

/**
 * Generates inputs from an EBNF grammar, following The Fuzzing Book's three-phase grammar fuzzer
 * (<https://www.fuzzingbook.org/html/GrammarFuzzer.html>): grow with maximum-cost expansions,
 * expand randomly, then finish with minimum-cost expansions.
 *
 * It expands the EBNF tree directly. A derivation tree starts at the start symbol; expanding a node
 * replaces it with child nodes; rendering concatenates the terminal leaves. When
 * `--grammar-recursive-depth` is set, generation prefers nodes that re-enter a symbol already on
 * the path, which drives nesting to the requested depth.
 *
 * @param grammar the grammar to derive inputs from.
 * @param recursiveDepth target recursion depth, or 0 for generic three-phase expansion.
 * @param minNonTerminals max-cost growth budget (pending leaves) before random expansion.
 * @param maxNonTerminals random-expansion budget (pending leaves) before closing.
 * @param recursionStrategy how recursive branches grow: BROAD siblings or one LINEAR chain.
 */
internal class GrammarFuzzer(
    private val grammar: Grammar,
    private val recursiveDepth: Int = 0,
    private val minNonTerminals: Int = DEFAULT_MIN_NON_TERMINALS,
    private val maxNonTerminals: Int = DEFAULT_MAX_NON_TERMINALS,
    private val recursionStrategy: RecursionStrategy = RecursionStrategy.BROAD
) : Fuzzer {
    init {
        require(recursiveDepth >= 0) { "${::recursiveDepth.name} must not be negative" }
        require(minNonTerminals >= 0) { "${::minNonTerminals.name} must not be negative" }
        require(maxNonTerminals >= 0) { "${::maxNonTerminals.name} must not be negative" }
        require(minNonTerminals <= maxNonTerminals) { "need ${::minNonTerminals.name} <= ${::maxNonTerminals.name}" }
    }

    /** Minimum and maximum expansion costs of grammar constructs, used to grow and to close. */
    private val cost = ExpansionCost(grammar)

    /** Cost- and strategy-based node selection: which node to expand next and which expansion to pick. */
    private val selector = ExpansionSelector(cost, recursionStrategy)

    /**
     * Recursion depth of the most recent [fuzz] output: the maximum number of times any single
     * symbol repeats on one path of the derivation tree. `--grammar-recursive-depth` bounds it.
     */
    var achievedRecursiveDepth: Int = 0
        private set

    /** Builds a derivation tree from the start symbol, then renders it as the generated input. */
    override fun fuzz(random: Random): String {
        val state = ExpansionState(random)
        val tree = DerivationNode(Expression.NonTerminal(grammar.start), emptyList(), level = 0)
        expandTree(tree, state)
        achievedRecursiveDepth = state.recursiveDescents
        return buildString { tree.render(this) }
    }

    /** Per-generation state: the random source and the deepest recursion reached so far. */
    private class ExpansionState(val random: Random) {
        var recursiveDescents = 0
    }

    /** Runs the generation phases, then closes every node still pending. */
    private fun expandTree(root: DerivationNode, state: ExpansionState) {
        if (recursiveDepth > 0) {
            // Depth-targeted generation: grow one recursive path to the requested depth, then close.
            expandToRecursiveDepth(root, state)
        } else {
            // Generic three-phase generation: max-cost grow, random expansion, then close.
            expandWithLimit(root, state, ExpansionStrategy.MAX, minNonTerminals)
            expandWithLimit(root, state, ExpansionStrategy.RANDOM, maxNonTerminals)
        }
        close(root, state)
    }

    /** Grows recursive nodes until the recursion reaches [recursiveDepth] or nothing more can grow. */
    private fun expandToRecursiveDepth(root: DerivationNode, state: ExpansionState) {
        while (state.recursiveDescents < recursiveDepth && root.hasPending()) {
            if (!expandOne(root, state, ExpansionStrategy.MAX, preferRecursive = true)) return
        }
    }

    /** Expands nodes with [strategy] until the tree has at least [limit] pending leaves. */
    private fun expandWithLimit(root: DerivationNode, state: ExpansionState, strategy: ExpansionStrategy, limit: Int) {
        while (root.possibleExpansions() < limit && root.hasPending()) {
            expandOne(root, state, strategy, preferRecursive = false)
        }
    }

    /** Closes the tree: gives every pending node its cheapest terminating expansion. */
    private fun close(node: DerivationNode, state: ExpansionState) {
        if (node.pending) {
            node.children = expandNode(node, state, ExpansionStrategy.MIN, preferRecursive = false)
        }
        node.children.orEmpty().forEach { close(it, state) }
    }

    /** Expands one pending node chosen for [strategy]; returns false if none could be expanded. */
    private fun expandOne(
        root: DerivationNode,
        state: ExpansionState,
        strategy: ExpansionStrategy,
        preferRecursive: Boolean
    ): Boolean {
        val pending = mutableListOf<DerivationNode>().also(root::collectPending)
        val candidates =
            if (preferRecursive) pending.filter { selector.canGrowRecursively(it.expression, it.path) } else pending
        if (candidates.isEmpty()) return false
        val chosen = selector.chooseNodeToExpand(candidates, state.random, preferRecursive)
        chosen.children = expandNode(chosen, state, strategy, preferRecursive)
        return true
    }

    /** Expands one node into its child nodes, one case per EBNF construct. */
    private fun expandNode(
        node: DerivationNode,
        state: ExpansionState,
        strategy: ExpansionStrategy,
        preferRecursive: Boolean
    ): MutableList<DerivationNode> = TODO("Exercise 2: expand one pending derivation-tree node")

    /** Marks terminal children as already closed, so they are never expanded again. */
    private fun resolveTerminals(nodes: MutableList<DerivationNode>) {
        nodes.filter { it.expression is Expression.Terminal }.forEach { it.children = mutableListOf() }
    }

    /** Picks one alternative of a choice for [strategy]: costliest (recursive first), cheapest, or uniform. */
    private fun chooseAlternative(
        alternatives: List<Expression>,
        path: List<String>,
        state: ExpansionState,
        strategy: ExpansionStrategy
    ): Expression = when (strategy) {
        ExpansionStrategy.MAX -> {
            val recursive = alternatives.filter { selector.canGrowRecursively(it, path) }
            val pool = recursive.ifEmpty { alternatives }
            selector.chooseByCost(pool, state.random, maximize = true) { cost.maxCost(it, path.toSet()) }
        }

        ExpansionStrategy.MIN ->
            selector.chooseByCost(alternatives, state.random, maximize = false) { cost.minCost(it, path.toSet()) }

        ExpansionStrategy.RANDOM -> alternatives[state.random.nextInt(alternatives.size)]
    }

    /** Whether to include an optional's body for [strategy]. */
    private fun includeOptional(
        body: Expression,
        path: List<String>,
        state: ExpansionState,
        strategy: ExpansionStrategy,
        preferRecursive: Boolean
    ): Boolean = when (strategy) {
        ExpansionStrategy.MAX -> if (preferRecursive) selector.canGrowRecursively(body, path) else true
        ExpansionStrategy.MIN -> false
        ExpansionStrategy.RANDOM -> state.random.nextBoolean()
    }

    /** How many times to repeat a repetition's body for [strategy]. */
    private fun repetitionCount(
        body: Expression,
        path: List<String>,
        state: ExpansionState,
        strategy: ExpansionStrategy,
        preferRecursive: Boolean
    ): Int = when (strategy) {
        ExpansionStrategy.MAX -> if (preferRecursive) {
            if (selector.canGrowRecursively(body, path)) 1 else 0
        } else {
            1
        }

        ExpansionStrategy.MIN -> 0

        ExpansionStrategy.RANDOM -> state.random.nextInt(0, MAX_RANDOM_REPETITIONS + 1)
    }

    private companion object {
        const val DEFAULT_MIN_NON_TERMINALS = 0
        const val DEFAULT_MAX_NON_TERMINALS = 10
        const val MAX_RANDOM_REPETITIONS = 2
    }
}
