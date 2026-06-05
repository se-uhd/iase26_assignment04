package de.seuhd.ktfuzzer.mode.grammar

import java.nio.file.Files
import java.nio.file.Path

/** Loads and parses the EBNF grammar from a file. */
internal object GrammarLoader {
    /** Reads the EBNF grammar from [path] and parses it into a [Grammar]. */
    fun load(path: Path): Grammar = EbnfGrammarParser.parse(Files.readString(path))
}
