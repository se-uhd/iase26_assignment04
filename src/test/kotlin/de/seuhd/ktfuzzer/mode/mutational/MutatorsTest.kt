package de.seuhd.ktfuzzer.mode.mutational

import de.seuhd.ktfuzzer.TestFixtures
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutatorsTest {
    private val random = Random(TestFixtures.SEED)
    private val alphabet = TestFixtures.ALPHABET.toList()

    @Test
    fun `repeatRandomCharacter on a single character yields a run of that character`() {
        val result = Mutators.repeatRandomCharacter("x", random)
        assertTrue(result.length >= 2, "expected a run, got ${result.length} char(s)")
        assertTrue(result.all { it == 'x' }, "every character should be 'x'")
    }

    @Test
    fun `repeatRandomCharacter grows the input`() {
        val input = "abcdef"
        assertTrue(Mutators.repeatRandomCharacter(input, random).length > input.length)
    }

    @Test
    fun `deleteRandomCharacter removes exactly one character`() {
        val input = "abcd"
        val result = Mutators.deleteRandomCharacter(input, random)
        assertEquals(input.length - 1, result.length)
        val matches = input.indices.any { drop -> result == input.filterIndexed { idx, _ -> idx != drop } }
        assertTrue(matches, "result should be the input minus one character")
    }

    @Test
    fun `insertRandomCharacter adds exactly one character from the alphabet`() {
        val input = "abcd"
        val result = Mutators.insertRandomCharacter(input, alphabet, random)
        assertEquals(input.length + 1, result.length)
        val inserted = result.indices.first { i -> result.removeRange(i, i + 1) == input }
        assertTrue(result[inserted] in alphabet, "the inserted character should come from the alphabet")
    }

    @Test
    fun `flipRandomCharacter changes exactly one character by a single bit`() {
        val input = "abcd"
        val result = Mutators.flipRandomCharacter(input, random)
        assertEquals(input.length, result.length, "flipping keeps the length")
        val diffs = input.indices.filter { input[it] != result[it] }
        assertEquals(1, diffs.size, "exactly one character changes")
        val delta = input[diffs[0]].code xor result[diffs[0]].code
        assertTrue(delta != 0 && (delta and (delta - 1)) == 0, "the change is a single bit flip")
    }

    @Test
    fun `the length-preserving mutators are no-ops on empty input`() {
        assertEquals("", Mutators.deleteRandomCharacter("", random))
        assertEquals("", Mutators.flipRandomCharacter("", random))
        assertEquals("", Mutators.repeatRandomCharacter("", random))
    }

    @Test
    fun `insertRandomCharacter on empty input yields a single character`() {
        assertEquals(1, Mutators.insertRandomCharacter("", alphabet, random).length)
    }

    @Test
    fun `mutators never throw on arbitrary input`() {
        runBlocking {
            checkAll(Arb.string(0..MAX_LEN)) { input ->
                val r = Random(input.length.toLong() + 1)
                Mutators.deleteRandomCharacter(input, r)
                Mutators.insertRandomCharacter(input, alphabet, r)
                Mutators.flipRandomCharacter(input, r)
                Mutators.repeatRandomCharacter(input, r)
            }
        }
    }

    @Test
    fun `delete shrinks by one and insert grows by one on non-empty input`() {
        runBlocking {
            checkAll(Arb.string(1..MAX_LEN)) { input ->
                val r = Random(7)
                assertEquals(input.length - 1, Mutators.deleteRandomCharacter(input, r).length)
                assertEquals(input.length + 1, Mutators.insertRandomCharacter(input, alphabet, r).length)
            }
        }
    }

    @Test
    fun `flip preserves length on non-empty input`() {
        runBlocking {
            checkAll(Arb.string(1..MAX_LEN)) { input ->
                assertEquals(input.length, Mutators.flipRandomCharacter(input, Random(7)).length)
            }
        }
    }

    private companion object {
        const val MAX_LEN = 200
    }
}
