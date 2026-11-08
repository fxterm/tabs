#!/usr/bin/env bash
# Does a launch join the running window when instance.json is stale, and does a window remove its registration on exit?
# Runs with user.home in a temp folder, so a running Tabs is never touched. No window is shown.
# Usage: run.sh [jar]     exit 0 all passed, 1 a check failed, 2 the harness itself did not work
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
JAR="$(cygpath -w "$(realpath "${1:-$ROOT/shade/fxterm-tabs.jar}")")"
SRC="$(cygpath -w "$(realpath "$ROOT/app/src/main/java/fxterm/tabs")")"
TESTSRC="$(cygpath -w "$HERE/JoinTest.java")"
OUT="$(mktemp -d)"; WOUT="$(cygpath -w "$OUT")"
trap 'rm -rf "$OUT"' EXIT
# the sources under test are compiled fresh and come first on the classpath, so they shadow the jar's copies
javac -d "$WOUT" -cp "$JAR" "$SRC\\Control.java" "$SRC\\Instance.java" "$SRC\\Main.java" "$SRC\\Request.java" "$TESTSRC" || { echo "HARNESS: compile failed"; exit 2; }
java -cp "$WOUT;$JAR" fxterm.tabs.JoinTest
