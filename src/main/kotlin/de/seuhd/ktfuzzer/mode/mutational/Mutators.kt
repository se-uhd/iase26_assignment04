package de.seuhd.ktfuzzer.mode.mutational

import kotlin.random.Random

/**
 * Character-level mutators from The Fuzzing Book's MutationFuzzer
 * (<https://www.fuzzingbook.org/html/MutationFuzzer.html>), plus [repeatRandomCharacter]. Each
 * function applies one small edit to an input string and returns the result.
 */
internal object Mutators {
    /** Deletes one randomly chosen character. */
    fun deleteRandomCharacter(input: String, random: Random): String =
        TODO("Exercise 1: delete one randomly chosen character")

    /** Inserts one character drawn uniformly from [alphabet] at a random position. */
    fun insertRandomCharacter(input: String, alphabet: List<Char>, random: Random): String =
        TODO("Exercise 1: insert one character from the alphabet at a random position")

    /** Flips one randomly chosen low bit of one randomly chosen character. */
    fun flipRandomCharacter(input: String, random: Random): String =
        TODO("Exercise 1: flip one random low bit of one randomly chosen character")

    /** Repeats one randomly chosen character a random number of times in place. */
    fun repeatRandomCharacter(input: String, random: Random): String =
        TODO("Exercise 1: repeat one randomly chosen character a random number of times")
}
