package de.seuhd.ktfuzzer

/** Shared constants for the fuzzer's own unit tests. */
internal object TestFixtures {
    /** A fixed seed so generator and mutator tests are reproducible. Test-owned, not the CLI default. */
    const val SEED = 0xA5E26L

    /** A simple, target-blind alphabet (letters and digits) for generator and mutator tests. */
    const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
}
