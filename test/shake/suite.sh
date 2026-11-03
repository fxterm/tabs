#!/usr/bin/env bash
# Every animation family, the old build against the new, off-screen at 125% scaling, then the report page.
# Usage: suite.sh <oldJar> <newJar> <onScreenOldFrames> <onScreenNewFrames>   (the last two optional: an on-screen pair)
HERE="$(cd "$(dirname "$0")" && pwd)"; OLD="$1"; NEW="$2"; D="$(mktemp -d)"; ARGS=()
if [ -n "$3" ] && [ -n "$4" ]; then
  ARGS+=("Claude's stars, on screen (window title bar included)" "Before: the build in use" "$3" "After: the new build" "$4" 306 38 80 112 50 84 9)
fi
for m in claude braille wheel counter words; do
  for w in old new; do jar=$OLD; [ $w = new ] && jar=$NEW; MODE=$m SCALE=1.25 KEEP="$D/$m-$w" bash "$HERE/run.sh" "$jar" >/dev/null 2>&1; done
  case $m in
    claude)  t="Claude's stars (· ✢ ✳ ✶ ✻ ✽) in front of the name";;
    braille) t="Braille dots (npm, pip, cargo)";;
    wheel)   t="Plain ASCII wheel  |  /  -  \\";;
    counter) t="Pure ASCII text that counts: build (1s), build (10s), build (100s)";;
    words)   t="Pure ASCII text swinging between one letter and a very long word";;
  esac
  case $m in counter|words) slot="56 236 8 34";; *) slot="56 80 8 34";; esac
  ARGS+=("$t" "Before: the build in use" "$D/$m-old" "After: the new build" "$D/$m-new" 236 0 $slot 0)
done
java "$HERE/Report.java" "$HERE/report.html" "${ARGS[@]}"
