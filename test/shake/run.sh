#!/usr/bin/env bash
# Is the tab list steady while a tab's title animates like claude's? Off-screen, so it never touches a running Tabs.
# Usage: run.sh [jar] [tabs] [frames]     exit 0 steady, 1 shaky, 2 the harness itself did not work
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
JAR="${1:-$ROOT/shade/fxterm-tabs.jar}"; TABS="${2:-10}"; FRAMES="${3:-14}"
OUT="$(mktemp -d)"; STATE="$(cygpath -w "$OUT")\state.json"; CTL="$ROOT/dist/fxterm-tabs.exe ctl --state=$STATE"
cleanup() { powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'state.json' -and \$_.CommandLine -match '$(basename "$OUT")' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1; }
trap cleanup EXIT
(java ${SCALE:+-Dglass.win.uiScale=$SCALE} -jar "$JAR" --offscreen true --state "$STATE" --tabs "$TABS" --restore false > "$OUT/app.out" 2>&1 &)
for i in $(seq 1 40); do $CTL list >/dev/null 2>&1 && break; sleep 1; done
$CTL list >/dev/null 2>&1 || { echo "HARNESS: the test instance did not start"; exit 2; }
# group labels under the animated tab, so the list has the mix of rows a real one has
for n in 1 2 3 4; do $CTL separator title="Group $n" at=$(( n * 2 - 1 )) >/dev/null; done
for t in $($CTL list | jq -r ".windows[0].tabs[]|select(.separator|not)|.id" | head -4); do $CTL color id=$t color="#fa5355" >/dev/null; done
ID=$($CTL list | jq -r '[.windows[0].tabs[]|select(.separator|not)][0].id')
$CTL send id=$ID text="chcp 65001>nul && powershell -NoProfile -File $(cygpath -w "$HERE/spin.ps1") -Mode ${MODE:-claude}" enter=true >/dev/null
sleep 4
files=()
for i in $(seq 1 "$FRAMES"); do $CTL shot file="$(cygpath -w "$OUT/f$i.png")" >/dev/null; files+=("$(cygpath -w "$OUT/f$i.png")"); done
case "${MODE:-claude}" in counter|words) SLOT="56 236 8 34";; *) SLOT="56 80 8 34";; esac
[ -n "$KEEP" ] && { mkdir -p "$KEEP"; cp "$OUT"/f*.png "$KEEP"/; }
java "$HERE/Shake.java" 236 0 $SLOT "${files[@]}"
