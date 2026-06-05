#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<USAGE
Usage (solo):
  ./prepare-submission.sh <Lastname> <Firstname>

Usage (group of two):
  ./prepare-submission.sh <Lastname1> <Firstname1> <Lastname2> <Firstname2>

Examples:
  ./prepare-submission.sh Mueller Anna
    -> ../iase26_assignment04_Mueller_Anna.zip

  ./prepare-submission.sh Mueller-Schmidt Anna Weber Berta
    -> ../iase26_assignment04_Mueller-Schmidt_Anna__Weber_Berta.zip
USAGE
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "error: required command not found: $1" >&2
        exit 1
    }
}

if [ "$#" -ne 2 ] && [ "$#" -ne 4 ]; then
    usage
    exit 1
fi

for part in "$@"; do
    case "$part" in
        "" | *[!A-Za-z0-9-]*)
            echo "error: name parts must use only ASCII letters, digits, and hyphens" >&2
            exit 1
            ;;
    esac
done

if [ "$#" -eq 2 ]; then
    NAME_PART="$1_$2"
else
    NAME_PART="$1_$2__$3_$4"
fi

OUTPUT="../iase26_assignment04_${NAME_PART}.zip"

if [ ! -d ".git" ]; then
    echo "error: not a git repository (run from the root of your assignment clone)" >&2
    exit 1
fi

for required in \
    "settings.gradle.kts" \
    "EXERCISES.md" \
    "REPORT.md" \
    "targets/toml/target.yaml" \
    "src/main/kotlin/de/seuhd/ktfuzzer"
do
    if [ ! -e "$required" ]; then
        echo "error: missing $required (run from the root of your assignment clone)" >&2
        exit 1
    fi
done

require_command zip
require_command unzip

SYMLINK="$(find . -type l -print -quit)"
if [ -n "$SYMLINK" ]; then
    echo "error: refusing to package repository with symlinks" >&2
    echo "first symlink found: $SYMLINK" >&2
    exit 1
fi

rm -f "$OUTPUT"

# Exclude generated build files, IDE files, and Gradle wrapper files.
COPYFILE_DISABLE=1 zip -qr "$OUTPUT" . \
    -x "*/.gradle/*"  -x ".gradle/*" \
    -x "*/.gradle"    -x ".gradle" \
    -x "*/gradle/*"   -x "gradle/*" \
    -x "*/gradle"     -x "gradle" \
    -x "*/gradle/wrapper/*" -x "gradle/wrapper/*" \
    -x "*/gradle/wrapper"   -x "gradle/wrapper" \
    -x "*/gradlew"          -x "gradlew" \
    -x "*/gradlew.bat"      -x "gradlew.bat" \
    -x "*/build/*"    -x "build/*" \
    -x "*/build"      -x "build" \
    -x "*/.idea/*"    -x ".idea/*" \
    -x "*/.idea"      -x ".idea" \
    -x "*.iml"        -x "*/*.iml" \
    -x "*/.kotlin/*"  -x ".kotlin/*" \
    -x "*/.kotlin"    -x ".kotlin" \
    -x "*/.vscode/*"  -x ".vscode/*" \
    -x "*/.vscode"    -x ".vscode" \
    -x "*.zip" \
    -x ".DS_Store" -x "*/.DS_Store"

if ! unzip -l "$OUTPUT" ".git/*" >/dev/null 2>&1; then
    echo "error: archive does not include .git/" >&2
    exit 1
fi

echo "Wrote $OUTPUT"
