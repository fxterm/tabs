#!/usr/bin/env bash
# The same question as run.sh, on screen: a real window, the real title bar, the real display scaling.
# Opens a test window (its own state file; it never touches a running Tabs) for about ten seconds.
# Usage: screen.sh [jar] [tabs] [frames]     exit 0 steady, 1 shaky, 2 the harness itself did not work
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
JAR="${1:-$ROOT/shade/fxterm-tabs.jar}"; TABS="${2:-10}"; FRAMES="${3:-40}"
OUT="$(mktemp -d)"; STATE="$(cygpath -w "$OUT")\state.json"; CTL="$ROOT/dist/fxterm-tabs.exe ctl --state=$STATE"
cleanup() { powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'state.json' -and \$_.CommandLine -match '$(basename "$OUT")' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1; }
trap cleanup EXIT
(java -jar "$JAR" --state "$STATE" --tabs "$TABS" --restore false > "$OUT/app.out" 2>&1 &)
for i in $(seq 1 40); do $CTL list >/dev/null 2>&1 && break; sleep 1; done
$CTL list >/dev/null 2>&1 || { echo "HARNESS: the test instance did not start"; exit 2; }
PID=$(powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match '$(basename "$OUT")' }).ProcessId")
for n in 1 2 3 4; do $CTL separator title="Group $n" at=$(( n * 2 - 1 )) >/dev/null; done
ID=$($CTL list | jq -r '[.windows[0].tabs[]|select(.separator|not)][0].id')
$CTL send id=$ID text="chcp 65001>nul && powershell -NoProfile -File $(cygpath -w "$HERE/spin.ps1") -Mode ${MODE:-claude}" enter=true >/dev/null
sleep 4
powershell -NoProfile -File "$(cygpath -w "$HERE/capture.ps1")" -TabsPid "$PID" -Dir "$(cygpath -w "$OUT/frames")" -Frames "$FRAMES" || { echo "HARNESS: no window to capture"; exit 2; }
files=(); for i in $(seq 1 "$FRAMES"); do files+=("$(cygpath -w "$OUT/frames/f$i.png")"); done
# the animated tab's glyph slot: first row under the title bar (about 31 px tall)
[ -n "$KEEP" ] && cp -r "$OUT/frames" "$KEEP"
# physical pixels at 125% display scaling: sidebar 306 wide, title bar 38 tall, the glyph slot of the first row
INSET=9 java "$HERE/Shake.java" 306 38 80 112 50 84 "${files[@]}"
