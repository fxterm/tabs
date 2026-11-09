#!/usr/bin/env bash
# PRP 17: `ctl screen`, the styled visible screen of a tab and its cheap change check (since=<version>).
# Starts an offscreen Tabs on its own state file, control socket and instance file, with user.home in a temp folder: a running Tabs is never touched. No window is shown.
# Usage: run.sh [jar]     exit 0 all passed, 1 a check failed, 2 Tabs did not start
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
JAR="$(cygpath -w "$(realpath "${1:-$ROOT/shade/fxterm-tabs.jar}")")"
OUT="$(mktemp -d)"; WOUT="$(cygpath -w "$OUT")"
trap 'rm -rf "$OUT"' EXIT
javac -d "$WOUT" -cp "$JAR" "$(cygpath -w "$HERE/ScreenTest.java")" || { echo "HARNESS: compile failed"; exit 2; }
java -cp "$WOUT;$JAR" fxterm.tabs.ScreenTest "$JAR"
