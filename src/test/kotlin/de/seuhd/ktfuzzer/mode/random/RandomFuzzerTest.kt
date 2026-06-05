package de.seuhd.ktfuzzer.mode.random

import de.seuhd.ktfuzzer.TestFixtures
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RandomFuzzerTest {
    @Test
    fun `length stays within bounds and characters come from the alphabet`() {
        val alphabet = TestFixtures.ALPHABET.toList()
        val fuzzer = RandomFuzzer(alphabet, minLength = 3, maxLength = 12)
        val random = Random(1)
        repeat(500) {
            val generated = fuzzer.fuzz(random)
            assertTrue(generated.length in 3..12, "length ${generated.length} out of bounds")
            assertTrue(generated.all { it in alphabet }, "drew a character outside the alphabet: $generated")
        }
    }

    @Test
    fun `the same seed produces the same sequence`() {
        val alphabet = TestFixtures.ALPHABET.toList()
        val a = RandomFuzzer(alphabet)
        val b = RandomFuzzer(alphabet)
        val randomA = Random(42)
        val randomB = Random(42)
        repeat(100) { assertEquals(a.fuzz(randomA), b.fuzz(randomB)) }
    }

    @Test
    fun `equal min and max give a fixed length`() {
        val fuzzer = RandomFuzzer(TestFixtures.ALPHABET.toList(), minLength = 5, maxLength = 5)
        val random = Random(3)
        repeat(50) { assertEquals(5, fuzzer.fuzz(random).length) }
    }

    @Test
    fun `an empty alphabet or an inverted length range is rejected`() {
        assertFailsWith<IllegalArgumentException> { RandomFuzzer(alphabet = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            RandomFuzzer(TestFixtures.ALPHABET.toList(), minLength = 10, maxLength = 5)
        }
    }
}
