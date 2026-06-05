package de.seuhd.ktfuzzer.mode.grammar

/** How to expand a node: MAX grows, RANDOM varies, MIN closes with the cheapest terminating form. */
internal enum class ExpansionStrategy { MAX, RANDOM, MIN }
