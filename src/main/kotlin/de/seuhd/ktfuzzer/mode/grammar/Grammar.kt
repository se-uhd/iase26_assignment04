package de.seuhd.ktfuzzer.mode.grammar

/**
 * A parsed grammar: Every production keyed by its non-terminal name, plus the start symbol's name.
 *
 * @property start the name of the start symbol (the first production defined).
 * @property productions each non-terminal name mapped to its expression.
 */
internal data class Grammar(val start: String, val productions: Map<String, Expression>)
