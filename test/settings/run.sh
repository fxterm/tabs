#!/usr/bin/env bash
# PRP 20: default folder, bare launch, "no colour" saved as none. user.home is a temp folder, so a running Tabs is never touched. No window is shown.
# Usage: run.sh [jar] [source folder]     exit 0 all passed, 1 a check failed, 2 the harness itself did not work (on main, where DefaultFolder does not exist, the compile fails)
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
JAR="$(cygpath -w "$(realpath "${1:-$ROOT/shade/fxterm-tabs.jar}")")"
SRC="$(cygpath -w "$(realpath "${2:-$ROOT/app/src/main/java/fxterm/tabs}")")"
TESTSRC="$(cygpath -w "$HERE/SettingsTest.java")"
OUT="$(mktemp -d)"; WOUT="$(cygpath -w "$OUT")"
trap 'rm -rf "$OUT"' EXIT
FILES=()
for f in DefaultFolder Request WindowLook WindowState; do [ -f "$(cygpath -u "$SRC")/$f.java" ] && FILES+=("$SRC\\$f.java"); done
javac -d "$WOUT" -cp "$JAR" "${FILES[@]}" "$TESTSRC" || { echo "HARNESS: compile failed"; exit 2; }
java -cp "$WOUT;$JAR" fxterm.tabs.SettingsTest
