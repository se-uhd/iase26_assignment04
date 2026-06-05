package de.seuhd.ktfuzzer.mode.grammar

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EbnfGrammarParserTest {
    @Test
    fun `loads and parses the toml_parser grammar`() {
        val grammar = GrammarLoader.load(Paths.get("targets/toml/toml_parser.ebnf"))
        assertEquals("document", grammar.start, "the first production is the start symbol")
        // A few productions the grammar fuzzer relies on must be present.
        val requiredProductions = listOf(
            "separator",
            "padding",
            "keyvalue",
            "value",
            "array",
            "key",
            "keychar",
            "basicstring",
            "stringchar"
        )
        for (production in requiredProductions) {
            assertTrue(production in grammar.productions, "expected production '$production'")
        }
    }

    @Test
    fun `value is a choice of basicstring and array`() {
        val grammar = GrammarLoader.load(Paths.get("targets/toml/toml_parser.ebnf"))
        assertEquals(
            Expression.Choice(listOf(Expression.NonTerminal("basicstring"), Expression.NonTerminal("array"))),
            grammar.productions.getValue("value")
        )
    }

    @Test
    fun `terminal escapes are interpreted, including the quote terminal`() {
        // newline = "\n" | ( "\r" , "\n" ); the escapes resolve to real control characters, and
        // basicstring opens with the double-quote written as the single-quoted terminal '"'.
        val grammar = GrammarLoader.load(Paths.get("targets/toml/toml_parser.ebnf"))
        val newline = grammar.productions.getValue("newline") as Expression.Choice
        assertEquals(Expression.Terminal("\n"), newline.alternatives[0])
        val crlf = (newline.alternatives[1] as Expression.Group).body as Expression.Sequence
        assertEquals(Expression.Terminal("\r"), crlf.parts[0])
        assertEquals(Expression.Terminal("\n"), crlf.parts[1])
        val basic = grammar.productions.getValue("basicstring") as Expression.Sequence
        assertEquals(Expression.Terminal("\""), basic.parts[0])
    }

    @Test
    fun `escapes are interpreted in an inline grammar`() {
        val grammar = EbnfGrammarParser.parse("""a = "\n" , "\t" , "\\" , '\'' ;""")
        assertEquals(
            Expression.Sequence(
                listOf(
                    Expression.Terminal("\n"),
                    Expression.Terminal("\t"),
                    Expression.Terminal("\\"),
                    Expression.Terminal("'")
                )
            ),
            grammar.productions.getValue("a")
        )
    }

    @Test
    fun `a small grammar round-trips to the expected tree`() {
        val grammar = EbnfGrammarParser.parse("""a = "x" , b ; b = "y" | "z" ;""")
        assertEquals("a", grammar.start)
        assertEquals(
            Expression.Sequence(listOf(Expression.Terminal("x"), Expression.NonTerminal("b"))),
            grammar.productions.getValue("a")
        )
        assertEquals(
            Expression.Choice(listOf(Expression.Terminal("y"), Expression.Terminal("z"))),
            grammar.productions.getValue("b")
        )
    }

    @Test
    fun `repetition, optional, and group are parsed`() {
        val grammar = EbnfGrammarParser.parse("""a = { "x" } , [ "y" ] , ( "z" ) ;""")
        assertEquals(
            Expression.Sequence(
                listOf(
                    Expression.Repetition(Expression.Terminal("x")),
                    Expression.Optional(Expression.Terminal("y")),
                    Expression.Group(Expression.Terminal("z"))
                )
            ),
            grammar.productions.getValue("a")
        )
    }

    @Test
    fun `a comment is skipped`() {
        val grammar = EbnfGrammarParser.parse("""a = (* a note *) "x" ;""")
        assertEquals(Expression.Terminal("x"), grammar.productions.getValue("a"))
    }

    @Test
    fun `an unterminated comment is rejected`() {
        assertFailsWith<EbnfGrammarParsingException> { EbnfGrammarParser.parse("""a = (* oops "x" ;""") }
    }

    @Test
    fun `an undefined non-terminal is rejected`() {
        assertFailsWith<EbnfGrammarParsingException> { EbnfGrammarParser.parse("""a = b ;""") }
    }

    @Test
    fun `an undefined non-terminal nested in any construct is rejected`() {
        // The reference checker must descend into every construct, not just top-level references.
        for (rhs in listOf("\"x\" , b", "\"x\" | b", "{ b }", "[ b ]", "( b )")) {
            assertFailsWith<EbnfGrammarParsingException>("undefined reference in '$rhs' should be rejected") {
                EbnfGrammarParser.parse("a = $rhs ;")
            }
        }
    }

    @Test
    fun `a missing terminator is rejected`() {
        assertFailsWith<EbnfGrammarParsingException> { EbnfGrammarParser.parse("""a = "x"""") }
    }
}
