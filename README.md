# kt-fuzzer

A Kotlin command-line fuzzer with three selectable modes (**random**, **mutational**, and
**grammar**) in one codebase. The fuzzer code is target-blind: it feeds generated input to a target
program on stdin and treats any exit code outside the target's expected set (the target config's
`expectedExitCodes`, default `0,1`), or any kill by a signal, as a crash and saves it.

The bundled `targets/toml/target.yaml` points the fuzzer at the stripped `toml_parser` binaries in
`targets/toml/bin/`, selecting the one for your OS. The fuzzer cannot read the parser's source or
which bug a crash hit, only the exit or signal code. Point `--target` at another config to fuzz a
different program.

Each crash is saved under `output/crashes/exit<code>/<sha256>/` as `input` (the raw crashing bytes)
plus the target's `stdout` and `stderr` from that run, grouped by the exit code the target returned
and deduplicated by input hash. The run's `campaign-summary.json` is written to the output dir.

## The three modes

- **random** generates input from scratch: a uniform-length string of characters drawn uniformly
  from the target config's alphabet. Target-blind: it rarely forms valid structure, so it crashes the
  target only by chance.
- **mutational** starts from the seed documents in `targets/toml/seeds/` and applies generic
  character edits (delete, insert, flip, or repeat a character), stacking a few per input.
- **grammar** parses `targets/toml/toml_parser.ebnf` at runtime and derives documents from it. With
  `--grammar-recursive-depth` and `--grammar-recursion-strategy linear` it generates nesting far
  deeper than a random or lexical fuzzer reaches: that depth is context-free, so only a
  grammar-aware generator produces it.

## Build and run

Needs JDK 25 and Gradle 9.5, pinned in `mise.toml`. With [mise](https://mise.jdx.dev), `mise
install` provisions both; otherwise put a JDK 25 and Gradle 9.5 on `PATH`. There is no Gradle
wrapper; run `gradle` directly.

```sh
gradle build  # compile, static checks, and tests

gradle run --args="--mode grammar --grammar-recursive-depth 300 --grammar-recursion-strategy linear --stop-on-crash"
gradle run --args="--mode mutational --stop-on-crash"
gradle run --args="--mode random --time-limit 30000 --stop-on-crash"
gradle run --args="--help"
```

A fuzzing run exits 0 whether or not it finds crashes; a usage error exits 2. Pass
`--fail-on-crash` to exit non-zero when at least one crash was found (for a CI gate). The same
`--random-seed` makes the fuzzer choose the same sequence of inputs.

### Flags

<!-- flags:start -->

```text
kt-fuzzer [flags]

  --mode MODE                     random | mutational | grammar (default: random)
  --target PATH                   YAML config describing the target: binaries, grammar, seeds, alphabet, expected exit codes (default: targets/toml/target.yaml)
  --min-length N                  random-mode minimum input length (default: 0)
  --max-length N                  random-mode maximum input length (default: 100)
  --max-executions N              stop after N executions (0 = unbounded) (default: 10000)
  --time-limit MS                 stop after this many milliseconds of wall-clock time
  --stop-on-crash                 stop at the first crash
  --fail-on-crash                 exit non-zero if any crash was found (for a CI gate)
  --grammar-recursive-depth N     grammar-mode recursion depth, 0 = generic expansion (default: 0, max 50000)
  --grammar-min-nonterminals N    grammar-mode max-cost grow budget (default: 0)
  --grammar-max-nonterminals N    grammar-mode random expansion budget (default: 10)
  --grammar-recursion-strategy S  grammar-mode recursion strategy: broad | linear (default: broad)
  --output-dir DIR                crashes go to DIR/crashes/ (default: output)
  --run-timeout MS                per-run timeout in milliseconds (default: 15000)
  --random-seed LONG              seed for random choices (default: 679462)
  --help, -h                      show this help
```

<!-- flags:end -->

Successful runs write the banner and summary to `stdout`. The machine-readable campaign summary is
always written to `<output-dir>/campaign-summary.json`. Usage errors and file-write warnings go to
`stderr`.

## How grammar mode works

`targets/toml/toml_parser.ebnf` is the ISO/IEC 14977 EBNF for the valid input language. A rendered
railroad diagram is at
[`targets/toml/toml_parser_railroad.html`](targets/toml/toml_parser_railroad.html).
Grammar mode reads the grammar at runtime:

1. `EbnfGrammarParser` tokenizes and parses the EBNF into a small grammar AST, handling terminals,
   references, sequence, choice, repetition `{}`, optional `[]`, and group `()`. Terminals
   interpret C-style escapes (`\n \r \t \\ \" \'`), so a newline is written `"\n"`.
2. `ExpansionCost` gives each expression two costs (following
   [The Fuzzing Book](https://www.fuzzingbook.org/html/GrammarFuzzer.html)): `minCost`, the
   cheapest way to finish it (optionals absent, repetitions empty, the shortest choice), and
   `maxCost`, the largest. Both treat re-entering a symbol already on the active derivation path
   as infinite, so a terminating expansion always exists.
3. `GrammarFuzzer` builds a derivation tree in three phases: max-cost growth up to
   `--grammar-min-nonterminals`, random expansion up to `--grammar-max-nonterminals`, then
   min-cost closure. `--grammar-recursive-depth d` instead grows one path that re-enters a symbol
   already on the derivation path, to depth `d`, then closes; `--grammar-recursion-strategy linear`
   keeps that to a single deep chain.

## Testing

`gradle test` runs the deterministic suite without launching any binary. `gradle check` adds the
static analysis (detekt, with ktlint's formatting rules via `.editorconfig`) and the build's other
quality gates.

## Layout

<!-- layout:start -->

```text
src/main/kotlin/de/seuhd/ktfuzzer/  (Main.kt, CliArgs.kt, FuzzerConfig.kt: entry point, flag parser, defaults)
  exec/    run the target binary, classify exit codes, load the target config
  engine/  the fuzzing loop, stop conditions, crash output, clock
  report/  counters and the run summary
  mode/    Fuzzer interface, the random/mutational/grammar fuzzers, seeds, mutators, EBNF
```

<!-- layout:end -->

The grammar (`targets/toml/toml_parser.ebnf`), its railroad diagram
(`targets/toml/toml_parser_railroad.html`), the seed corpus (`targets/toml/seeds/*.toml`), the target
binaries (`targets/toml/bin/`), and the target config (`targets/toml/target.yaml`) live under
`targets/toml/`.

## Attribution

The development of this fuzzer was supported by Claude Code (Opus 4.8) and Codex (GPT-5.5).
