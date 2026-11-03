#!/usr/bin/env bash
# Starts a test window with an animating tab and checks that the comet stays on its ring.
# Usage: comet.sh [jar]   (the ring is 18 px; the check looks only at its own square, since the name text beside it has blue colour fringes)
HERE="$(cd "$(dirname "$0")" && pwd)"; D="$(mktemp -d)"
MODE=claude SCALE=1.25 KEEP="$D" FRAMES=24 bash "$HERE/run.sh" "${1:-$HERE/../../shade/fxterm-tabs.jar}" >/dev/null 2>&1
java "$HERE/Comet.java" 56 8 78 34 $(ls "$D"/f*.png | sort -V)
