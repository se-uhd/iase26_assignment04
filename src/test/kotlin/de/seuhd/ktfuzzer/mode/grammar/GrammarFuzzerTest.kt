package de.seuhd.ktfuzzer.mode.grammar

import java.nio.file.Paths
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrammarFuzzerTest {
    private val grammar = GrammarLoader.load(Paths.get("targets/toml/toml_parser.ebnf"))

    @Test
    fun `generated documents render grammar terminals only`() {
        val deep = GrammarFuzzer(
            nestedListGrammar(),
            recursiveDepth = SYNTHETIC_DEEP_DEPTH,
            recursionStrategy = RecursionStrategy.LINEAR
        )
        val terminalOnly = GrammarFuzzer(terminalOnlyGrammar())
        val random = Random(1)
        repeat(40) {
            val nested = deep.fuzz(random)
            assertTrue(nested.isNotEmpty(), "document should not be empty")
            assertTrue(nested.all { it == '[' || it == ']' || it == ',' }, "unexpected terminal in $nested")
            assertEquals("[]", terminalOnly.fuzz(random))
        }
    }

    @Test
    fun `deep nesting is one linear chain, not a branching tree`() {
        val fuzzer = GrammarFuzzer(
            nestedListGrammar(),
            recursiveDepth = SYNTHETIC_DEEP_DEPTH,
            recursionStrategy = RecursionStrategy.LINEAR
        )
        val random = Random(5)
        // A linear chain costs a few characters per level; a tree would grow far past this bound.
        val bound = SYNTHETIC_DEEP_DEPTH * MAX_CHARS_PER_LEVEL
        repeat(20) {
            val doc = fuzzer.fuzz(random)
            assertTrue(
                doc.length < bound,
                "a depth-$SYNTHETIC_DEEP_DEPTH document grew to ${doc.length} chars, suggesting branching"
            )
        }
    }

    @Test
    fun `the same seed produces the same documents`() {
        val first = GrammarFuzzer(grammar)
        val second = GrammarFuzzer(grammar)
        val randomA = Random(7)
        val randomB = Random(7)
        repeat(50) { assertEquals(first.fuzz(randomA), second.fuzz(randomB)) }
    }

    @Test
    fun `broad recursive growth can use sibling recursive repetitions`() {
        val broad = GrammarFuzzer(
            nestedListGrammar(),
            recursiveDepth = 6,
            recursionStrategy = RecursionStrategy.BROAD
        )
        val random = Random(11)
        val outputs = List(80) { broad.fuzz(random) }

        assertTrue(outputs.any { "," in it }, "broad recursive growth should not suppress recursive repetitions")
    }

    @Test
    fun `recursion-depth generation reaches the requested depth for any grammar`() {
        // Target-blind: the generator reports the recursion depth it reached through its own counter,
        // not by counting any grammar's terminals. Holds across differently-shaped grammars.
        val directlyRecursive = EbnfGrammarParser.parse("""list = "[" , [ list ] , "]" ;""")
        val grammars = listOf(grammar, directlyRecursive, nestedListGrammar())
        for (depth in listOf(2, 5, 20, 300)) {
            for (g in grammars) {
                val fuzzer = GrammarFuzzer(
                    g,
                    recursiveDepth = depth,
                    recursionStrategy = RecursionStrategy.LINEAR
                )
                fuzzer.fuzz(Random(depth.toLong()))
                assertEquals(depth, fuzzer.achievedRecursiveDepth, "depth=$depth start=${g.start}")
            }
        }
    }

    @Test
    fun `the recursion-depth counter matches the rendered nesting on a controlled grammar`() {
        // On a grammar this test owns (its only nesting token is '['), a depth-N linear chain
        // renders exactly N opening brackets, so the counter must equal that bracket count.
        val list = EbnfGrammarParser.parse("""list = "[" , [ list ] , "]" ;""")
        for (depth in listOf(2, 5, 20, 300)) {
            val fuzzer = GrammarFuzzer(
                list,
                recursiveDepth = depth,
                recursionStrategy = RecursionStrategy.LINEAR
            )
            val renderedBrackets = fuzzer.fuzz(Random(depth.toLong())).count { it == '[' }
            assertEquals(depth, fuzzer.achievedRecursiveDepth, "counter at depth=$depth")
            assertEquals(fuzzer.achievedRecursiveDepth, renderedBrackets, "counter vs rendered at depth=$depth")
        }
    }

    private fun nestedListGrammar() =
        EbnfGrammarParser.parse("""start = item ; item = "[" , [ item ] , { "," , item } , "]" ;""")

    private fun terminalOnlyGrammar() = EbnfGrammarParser.parse("""start = "[" , "]" ;""")

    private companion object {
        const val SYNTHETIC_DEEP_DEPTH = 80
        const val MAX_CHARS_PER_LEVEL = 20
    }
}
