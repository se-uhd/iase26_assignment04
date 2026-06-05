package de.seuhd.ktfuzzer.mode.random

import de.seuhd.ktfuzzer.mode.Fuzzer
import kotlin.random.Random

/**
 * Generation-based random fuzzer, following The Fuzzing Book
 * (<https://www.fuzzingbook.org/html/Fuzzer.html>). Each input is built from scratch: pick a length
 * uniformly in `minLength..maxLength`, then draw that many characters uniformly from [alphabet].
 *
 * @param alphabet characters each generated string is drawn from.
 * @param minLength smallest generated length.
 * @param maxLength largest generated length.
 */
internal class RandomFuzzer(
    private val alphabet: List<Char>,
    private val minLength: Int = DEFAULT_MIN_LENGTH,
    private val maxLength: Int = DEFAULT_MAX_LENGTH
) : Fuzzer {
    init {
        require(alphabet.isNotEmpty()) { "alphabet must not be empty" }
        require(minLength in 0..maxLength) { "need 0 <= minLength <= maxLength, got $minLength..$maxLength" }
    }

    /** Builds one random string: a uniform length in `minLength..maxLength` filled from [alphabet]. */
    override fun fuzz(random: Random): String {
        val length = random.nextInt(minLength, maxLength + 1)
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.size)]) }
        }
    }

    companion object {
        /** Default lower bound on the generated string length when `--min-length` is not given. */
        const val DEFAULT_MIN_LENGTH = 0

        /** Default upper bound on the generated string length when `--max-length` is not given. */
        const val DEFAULT_MAX_LENGTH = 100
    }
}
