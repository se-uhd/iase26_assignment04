# Exercises

The fuzzing harness is provided; you implement the generation logic at the `TODO(...)` markers.
The full assignment, with points and submission rules, is in the assignment sheet; this file is
the in-repo summary. After cloning, `gradle test` reports **20 failing tests** covering the
functions you implement; your goal is zero failures.

## Exercise 1: mutational fuzzer

- The four mutators in
  [`Mutators.kt`](src/main/kotlin/de/seuhd/ktfuzzer/mode/mutational/Mutators.kt):
  `deleteRandomCharacter`, `insertRandomCharacter`, `flipRandomCharacter`, and
  `repeatRandomCharacter`. Each takes `input: String` and returns the edited `String`;
  `insertRandomCharacter` also takes `alphabet: List<Char>`. Whenever a mutator needs a random
  position, character, bit, or repeat count, draw it from `random: Random`. Do not use fixed
  choices, `Random.Default`, or a new `Random`. Two runs with the same `--random-seed` must draw
  the same sequence.
- `mutate` in
  [`MutationalFuzzer.kt`](src/main/kotlin/de/seuhd/ktfuzzer/mode/mutational/MutationalFuzzer.kt), which
  applies one randomly chosen mutator. The provided `createCandidate` calls it several times to
  stack edits on a seed.

Run mutational mode with `--stop-on-crash` (or omit it for longer campaigns) until you have
crashes with at least two different exit codes; crashing inputs are grouped under
`output/crashes/exit<code>/`, so the exit code is the directory name.

## Exercise 2: grammar-based fuzzer

- `expandNode` in
  [`GrammarFuzzer.kt`](src/main/kotlin/de/seuhd/ktfuzzer/mode/grammar/GrammarFuzzer.kt). The
  repository already parses `targets/toml/toml_parser.ebnf` into a small grammar AST. Grammar mode
  builds a derivation tree in phases. A node is pending while its expression has not yet been
  expanded; `expandNode` receives one pending node and returns the derivation-tree children for
  that node's expression. Use the `Expression` sealed type as the case split and the existing
  helper functions for strategy-dependent choices. The phase, depth, and termination logic is
  already provided.

Run `--mode grammar --grammar-recursive-depth 300 --grammar-recursion-strategy linear
--stop-on-crash` and confirm it crashes the binary.

## Testing your work

`gradle test` runs the unit tests. The ones under `mode/` check the shape of what you produce
(how a mutator changes the input's length, the structure of the grammar output) and fail until you
implement the matching function. They do **not** tell you which inputs crash the target; for that,
run the fuzzer against [`targets/toml/bin/`](targets/toml/bin/) and inspect `output/crashes/`.

## Cross-platform runs and CI

[`.github/workflows/fuzz.yml`](.github/workflows/fuzz.yml) runs your fuzzer against the matching
binary on Linux, macOS, and Windows and uploads the crashes it found. Using it is optional; your
repository stays private.

## Submission

Record your findings in `REPORT.md` as the sheet asks, then run `./prepare-submission.sh` to build
the zip (run it without arguments for usage). The zip includes `.git/` and local crash output
under `output/`; it excludes build artifacts and Gradle wrapper files. The extracted zip must pass
`mise trust`, `mise install`, and `gradle test` from the repository root. See the assignment sheet
for how to submit.
