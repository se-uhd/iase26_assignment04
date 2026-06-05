package de.seuhd.ktfuzzer.mode.grammar

/** An EBNF expression (ISO/IEC 14977 subset). */
internal sealed interface Expression {
    /**
     * A literal string, `"x"` or `'x'`.
     *
     * @property text the literal characters emitted verbatim.
     */
    data class Terminal(val text: String) : Expression

    /**
     * A non-terminal: a reference to another production by name.
     *
     * @property name the referenced production's name.
     */
    data class NonTerminal(val name: String) : Expression

    /**
     * Concatenation: `a , b , c`.
     *
     * @property parts the sub-expressions, emitted in order.
     */
    data class Sequence(val parts: List<Expression>) : Expression

    /**
     * Alternation: `a | b | c`.
     *
     * @property alternatives the mutually exclusive options.
     */
    data class Choice(val alternatives: List<Expression>) : Expression

    /**
     * Repetition (zero or more): `{ a }`.
     *
     * @property body the expression repeated zero or more times.
     */
    data class Repetition(val body: Expression) : Expression

    /**
     * Optional (zero or one): `[ a ]`.
     *
     * @property body the expression included zero or one time.
     */
    data class Optional(val body: Expression) : Expression

    /**
     * Grouping: `( a )`.
     *
     * @property body the grouped expression.
     */
    data class Group(val body: Expression) : Expression
}
