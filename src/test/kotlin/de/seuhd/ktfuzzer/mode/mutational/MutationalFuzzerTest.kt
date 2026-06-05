package de.seuhd.ktfuzzer.mode.mutational

import de.seuhd.ktfuzzer.TestFixtures
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MutationalFuzzerTest {
    private val seed = "greeting=\"hello world\"\n"
    private val alphabet = TestFixtures.ALPHABET.toList()

    private fun fuzzer(population: List<String> = listOf(seed)) = MutationalFuzzer(population, alphabet)

    @Test
    fun `fuzz returns each seed once, in order, before any candidate`() {
        val seeds = listOf("a=\"x\"\n", "b=\"y\"\n", "c=\"z\"\n")
        val f = fuzzer(seeds)
        val random = Random(TestFixtures.SEED)
        assertEquals(seeds, List(seeds.size) { f.fuzz(random) }, "the first calls replay the population verbatim")
    }

    @Test
    fun `fuzz produces mutated candidates once the population is exhausted`() {
        val f = fuzzer()
        val random = Random(TestFixtures.SEED)
        assertEquals(seed, f.fuzz(random), "the lone seed comes first, unchanged")
        repeat(20) { assertNotEquals(seed, f.fuzz(random), "later outputs are mutated candidates, not the seed") }
    }

    @Test
    fun `createCandidate mutates a seed and is reproducible`() {
        assertNotEquals(
            seed,
            fuzzer().createCandidate(Random(3)),
            "a candidate is the seed after one or more mutations"
        )
        assertEquals(fuzzer().createCandidate(Random(3)), fuzzer().createCandidate(Random(3)))
    }

    @Test
    fun `mutate changes a non-empty seed`() {
        val random = Random(TestFixtures.SEED)
        val f = fuzzer()
        repeat(50) { assertNotEquals(seed, f.mutate(seed, random), "each mutator alters a non-empty input") }
    }

    @Test
    fun `mutate is reproducible from the same Random`() {
        assertEquals(fuzzer().mutate(seed, Random(7)), fuzzer().mutate(seed, Random(7)))
    }

    @Test
    fun `an empty population is rejected`() {
        assertFailsWith<IllegalArgumentException> { fuzzer(emptyList()) }
    }

    @Test
    fun `the mutation count bounds must be positive and ordered`() {
        assertFailsWith<IllegalArgumentException> { MutationalFuzzer(listOf(seed), alphabet, minMutations = 0) }
        assertFailsWith<IllegalArgumentException> {
            MutationalFuzzer(listOf(seed), alphabet, minMutations = 5, maxMutations = 3)
        }
    }
}
