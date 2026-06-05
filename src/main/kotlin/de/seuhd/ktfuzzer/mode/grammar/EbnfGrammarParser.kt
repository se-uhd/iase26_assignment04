package de.seuhd.ktfuzzer.mode.grammar

/**
 * A tokenizer and recursive-descent parser for the ISO/IEC 14977 EBNF subset used by
 * `toml_parser.ebnf`:
 *
 *   production = identifier '=' expression ';'
 *   expression = sequence ( '|' sequence )*
 *   sequence   = factor ( ',' factor )*
 *   factor     = terminal | identifier
 *              | '{' expression '}' | '[' expression ']' | '(' expression ')'
 *
 * Terminals are `"..."` or `'...'` and interpret the C-style escapes `\n \r \t \\ \" \'`, so a
 * literal newline can be written `"\n"`. Comments are `(* ... *)`. ISO/IEC 14977 does not
 * designate a start symbol; this parser uses the first production defined.
 */
internal object EbnfGrammarParser {
    /** Tokenizes [source] and parses it into a [Grammar]. */
    fun parse(source: String): Grammar {
        val tokens = Tokenizer(source).tokenize()
        return Parser(tokens).parseGrammar()
    }

    /** Lexical token kinds the EBNF tokenizer produces. */
    private enum class Kind {
        IDENTIFIER, // a non-terminal name, e.g. document
        TERMINAL, // a quoted literal, e.g. "[" or '\n'
        EQUALS, // = defines a production
        COMMA, // , concatenation
        PIPE, // | alternation
        SEMICOLON, // ; ends a production
        LEFT_BRACE, // { opens a repetition (zero or more)
        RIGHT_BRACE, // }
        LEFT_BRACKET, // [ opens an optional (zero or one)
        RIGHT_BRACKET, // ]
        LEFT_PAREN, // ( opens a group
        RIGHT_PAREN, // )
        EOF // end of input
    }

    /** A lexical token: its [kind] and the matched source text (empty for single-character tokens). */
    private data class Token(val kind: Kind, val text: String)

    /** Splits the EBNF source into [Token]s, skipping whitespace and `(* *)` comments. */
    private class Tokenizer(private val src: String) {
        private var index = 0
        private val length = src.length

        /** Scans [src] into the full token list, terminated by a [Kind.EOF] token. */
        fun tokenize(): List<Token> = buildList {
            while (true) {
                skipWhitespaceAndComments()
                if (index >= length) {
                    add(Token(Kind.EOF, ""))
                    break
                }
                add(readToken())
            }
        }

        /** Advances [index] past whitespace and `(* *)` comments. */
        private fun skipWhitespaceAndComments() {
            while (index < length) {
                val c = src[index]
                when {
                    c.isWhitespace() -> index++
                    c == '(' && index + 1 < length && src[index + 1] == '*' -> skipComment()
                    else -> return
                }
            }
        }

        /** Skips a `(* ... *)` comment. */
        private fun skipComment() {
            val start = index
            index += 2
            while (index + 1 < length && !(src[index] == '*' && src[index + 1] == ')')) index++
            if (index + 1 >= length) throw EbnfGrammarParsingException("unterminated comment starting at offset $start")
            index += 2
        }

        /** Reads the next token at [index], dispatching on the current character. */
        private fun readToken(): Token {
            val c = src[index]
            return when {
                c == '"' || c == '\'' -> readTerminal(c)
                c == '=' -> singleCharToken(Kind.EQUALS)
                c == ',' -> singleCharToken(Kind.COMMA)
                c == '|' -> singleCharToken(Kind.PIPE)
                c == ';' -> singleCharToken(Kind.SEMICOLON)
                c == '{' -> singleCharToken(Kind.LEFT_BRACE)
                c == '}' -> singleCharToken(Kind.RIGHT_BRACE)
                c == '[' -> singleCharToken(Kind.LEFT_BRACKET)
                c == ']' -> singleCharToken(Kind.RIGHT_BRACKET)
                c == '(' -> singleCharToken(Kind.LEFT_PAREN)
                c == ')' -> singleCharToken(Kind.RIGHT_PAREN)
                c.isLetter() || c == '_' -> readIdentifier()
                else -> throw EbnfGrammarParsingException("unexpected character '$c' at offset $index")
            }
        }

        /** Consumes the single character at [index] and returns a [kind] token with no text. */
        private fun singleCharToken(kind: Kind): Token {
            index++
            return Token(kind, "")
        }

        /**
         * Reads a `"..."` or `'...'` terminal, interpreting C-style escapes inside it. A backslash
         * escapes the next character: `\n \r \t` become control characters, `\\ \" \'` become the
         * literal backslash or quote, and any other `\x` collapses to `x`. This lets the grammar
         * write a newline terminal as `"\n"` and the quote terminal as `'"'`.
         */
        private fun readTerminal(delim: Char): Token {
            val start = index
            index++ // skip the opening delimiter
            val text = StringBuilder()
            while (index < length && src[index] != delim) {
                if (src[index] == '\\' && index + 1 < length) {
                    text.append(unescape(src[index + 1]))
                    index += 2
                } else {
                    text.append(src[index])
                    index++
                }
            }
            if (index >= length) throw EbnfGrammarParsingException("unterminated terminal starting at offset $start")
            index++ // skip the closing delimiter
            return Token(Kind.TERMINAL, text.toString())
        }

        /** Maps the character after a backslash to the control character it stands for. */
        private fun unescape(escaped: Char): Char = when (escaped) {
            'n' -> '\n'

            'r' -> '\r'

            't' -> '\t'

            // \\ \" \' and any other escape stand for the literal character
            else -> escaped
        }

        /** Reads consecutive letters, digits, and underscores as an identifier name. */
        private fun readIdentifier(): Token {
            val start = index
            while (index < length && (src[index].isLetterOrDigit() || src[index] == '_')) index++
            return Token(Kind.IDENTIFIER, src.substring(start, index))
        }
    }

    /** Builds a [Grammar] from the token stream by recursive descent. */
    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        /** Reads every `name = expression ;` production; the first name becomes the start symbol. */
        fun parseGrammar(): Grammar {
            val productions = LinkedHashMap<String, Expression>()
            var start: String? = null
            while (peek().kind != Kind.EOF) {
                val name = expect(Kind.IDENTIFIER).text
                expect(Kind.EQUALS)
                productions[name] = parseExpression()
                expect(Kind.SEMICOLON)
                if (start == null) start = name
            }
            if (start == null) throw EbnfGrammarParsingException("grammar has no productions")
            validateReferences(productions)
            return Grammar(start, productions)
        }

        /** Parses an alternation: one or more sequences separated by `|`. */
        private fun parseExpression(): Expression {
            val alternatives = mutableListOf(parseSequence())
            while (accept(Kind.PIPE)) alternatives.add(parseSequence())
            return if (alternatives.size == 1) alternatives[0] else Expression.Choice(alternatives)
        }

        /** Parses a concatenation: one or more factors separated by `,`. */
        private fun parseSequence(): Expression {
            val parts = mutableListOf(parseFactor())
            while (accept(Kind.COMMA)) parts.add(parseFactor())
            return if (parts.size == 1) parts[0] else Expression.Sequence(parts)
        }

        /** Parses one factor: a terminal, a reference, or a `{}` / `[]` / `()` group. */
        private fun parseFactor(): Expression {
            val token = peek()
            return when (token.kind) {
                Kind.TERMINAL -> {
                    pos++
                    Expression.Terminal(token.text)
                }

                Kind.IDENTIFIER -> {
                    pos++
                    Expression.NonTerminal(token.text)
                }

                Kind.LEFT_BRACE -> {
                    pos++
                    Expression.Repetition(parseExpression()).also { expect(Kind.RIGHT_BRACE) }
                }

                Kind.LEFT_BRACKET -> {
                    pos++
                    Expression.Optional(parseExpression()).also { expect(Kind.RIGHT_BRACKET) }
                }

                Kind.LEFT_PAREN -> {
                    pos++
                    Expression.Group(parseExpression()).also { expect(Kind.RIGHT_PAREN) }
                }

                else -> throw EbnfGrammarParsingException("unexpected ${token.kind} '${token.text}'")
            }
        }

        /** The current token, without consuming it. */
        private fun peek(): Token = tokens[pos]

        /** Consumes the current token if it is [kind]; returns whether it did. */
        private fun accept(kind: Kind): Boolean = if (peek().kind == kind) {
            pos++
            true
        } else {
            false
        }

        /** Consumes the current token, requiring it to be [kind], or throws. */
        private fun expect(kind: Kind): Token {
            val token = peek()
            if (token.kind != kind) {
                throw EbnfGrammarParsingException("expected $kind but found ${token.kind} '${token.text}'")
            }
            pos++
            return token
        }

        /** Throws [EbnfGrammarParsingException] if a production references a non-terminal no production defines. */
        private fun validateReferences(productions: Map<String, Expression>) {
            val undefined = mutableSetOf<String>()

            // Gather every undefined reference so the error below lists them all at once.
            fun walk(expression: Expression) {
                when (expression) {
                    is Expression.NonTerminal -> if (expression.name !in productions) undefined.add(expression.name)
                    is Expression.Sequence -> expression.parts.forEach(::walk)
                    is Expression.Choice -> expression.alternatives.forEach(::walk)
                    is Expression.Repetition -> walk(expression.body)
                    is Expression.Optional -> walk(expression.body)
                    is Expression.Group -> walk(expression.body)
                    is Expression.Terminal -> Unit
                }
            }
            productions.values.forEach(::walk)
            if (undefined.isNotEmpty()) throw EbnfGrammarParsingException("undefined non-terminals: $undefined")
        }
    }
}
