package de.seuhd.ktfuzzer.mode.grammar

/** One node of the derivation tree: its [expression], the symbols on the path to it, and its depth. */
internal class DerivationNode(val expression: Expression, val path: List<String>, val level: Int) {
    /** Children once expanded, or null while the node is still pending. */
    var children: MutableList<DerivationNode>? = null

    /** True until this node has been expanded. */
    val pending: Boolean get() = children == null

    /** Pending leaves at or below this node (1 for a pending leaf, the sum over children otherwise). */
    fun possibleExpansions(): Int = children?.sumOf { it.possibleExpansions() } ?: 1

    /** True if this node or any descendant is still pending. */
    fun hasPending(): Boolean = pending || children.orEmpty().any { it.hasPending() }

    /** Adds every pending node at or below this one to [into]. */
    fun collectPending(into: MutableList<DerivationNode>) {
        val current = children
        if (current == null) into.add(this) else current.forEach { it.collectPending(into) }
    }

    /** Renders the subtree: a terminal appends its text, any other node renders its children. */
    fun render(output: StringBuilder) {
        when (val e = expression) {
            is Expression.Terminal -> output.append(e.text)
            else -> children.orEmpty().forEach { it.render(output) }
        }
    }
}
