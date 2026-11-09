#!/usr/bin/env bash
# PRP 03 (and 12, item 3): no TCP port; launches join through the control socket; ctl arguments with a bell, accents and quotes arrive unchanged.
# Starts an offscreen Tabs on its own state file and control socket, with user.home in a temp folder: a running Tabs is never touched. No window is shown.
# Usage: run.sh [jar]     exit 0 all passed, 1 a check failed, 2 Tabs did not start
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
JAR="$(cygpath -w "$(realpath "${1:-$ROOT/shade/fxterm-tabs.jar}")")"
OUT="$(mktemp -d)"; WOUT="$(cygpath -w "$OUT")"
trap 'rm -rf "$OUT"' EXIT
javac -d "$WOUT" -cp "$JAR" "$(cygpath -w "$HERE/HandoffTest.java")" || { echo "HARNESS: compile failed"; exit 2; }
java -cp "$WOUT;$JAR" fxterm.tabs.HandoffTest "$JAR"
