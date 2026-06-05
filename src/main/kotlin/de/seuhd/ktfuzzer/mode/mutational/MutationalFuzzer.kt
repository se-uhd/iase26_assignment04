package de.seuhd.ktfuzzer.mode.mutational

import de.seuhd.ktfuzzer.mode.Fuzzer
import kotlin.random.Random

/**
 * Seed-corpus mutational fuzzer, following The Fuzzing Book
 * (https://www.fuzzingbook.org/html/MutationFuzzer.html). [fuzz] yields each seed from the
 * fixed [population] once, then returns mutated candidates. A candidate is a random seed with
 * [mutate] applied `minMutations..maxMutations` times ([createCandidate]). The [population] never
 * grows: without a coverage signal there is nothing on which to select new seeds to add.
 *
 * @param population the fixed seed inputs to mutate.
 * @param alphabet characters [Mutators.insertRandomCharacter] draws from.
 * @param minMutations fewest mutations applied to build a candidate.
 * @param maxMutations most mutations applied to build a candidate.
 */
internal class MutationalFuzzer(
    private val population: List<String>,
    private val alphabet: List<Char>,
    private val minMutations: Int = DEFAULT_MIN_MUTATIONS,
    private val maxMutations: Int = DEFAULT_MAX_MUTATIONS
) : Fuzzer {
    /** Index of the next seed [fuzz] returns verbatim before it switches to mutated candidates. */
    private var seedIndex = 0

    init {
        require(population.isNotEmpty()) { "${::population.name} must contain at least one seed" }
        require(minMutations in 1..maxMutations) { "need 1 <= ${::minMutations.name} <= ${::maxMutations.name}" }
    }

    /** Returns each seed from [population] once, then mutated candidates from [createCandidate]. */
    override fun fuzz(random: Random): String =
        if (seedIndex < population.size) population[seedIndex++] else createCandidate(random)

    /** A random seed with [mutate] applied a random `minMutations..maxMutations` times. */
    internal fun createCandidate(random: Random): String {
        var candidate = population[random.nextInt(population.size)]
        repeat(random.nextInt(minMutations, maxMutations + 1)) {
            candidate = mutate(candidate, random)
        }
        return candidate
    }

    /** Applies one randomly chosen mutator to [input]. */
    internal fun mutate(input: String, random: Random): String =
        TODO("Exercise 1: apply one randomly chosen mutator from Mutators")

    private companion object {
        /** Fewest [mutate] applications used to build a candidate when no minimum is passed. */
        const val DEFAULT_MIN_MUTATIONS = 1

        /** Most [mutate] applications used to build a candidate when no maximum is passed. */
        const val DEFAULT_MAX_MUTATIONS = 10
    }
}
