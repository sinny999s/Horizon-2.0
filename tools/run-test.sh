#!/bin/sh
# run-test.sh <script.txt>: plays a ShotBot script in the dev client (./gradlew runClient) and prints its SHOT lines.
# Screenshots and shot.log go to tools/test-out/<script name>/, the whole game log to tools/test-out/<script name>.log.
# Set up the dev config first with testcfg.py, and restore it afterwards.
set -e
TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$TOOLS")"
SCRIPT="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
NAME="$(basename "$1" .txt)"
OUT="$TOOLS/test-out/$NAME"
mkdir -p "$ROOT/run/mods" "$TOOLS/test-out"
rm -rf "$OUT"
cp "$TOOLS/shotbot/shotbot-26.3.jar" "$ROOT/run/mods/shotbot.jar"
# Cygwin/Git Bash paths become Windows paths for the game; plain paths elsewhere.
win() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }
(cd "$ROOT"; SHOT_SCRIPT="$(win "$SCRIPT")" SHOT_OUT="$(win "$OUT")" timeout 600 ./gradlew --no-daemon -q runClient > "$TOOLS/test-out/$NAME.log" 2>&1) || true
rm -f "$ROOT/run/mods/shotbot.jar"
grep -h "SHOT:" "$TOOLS/test-out/$NAME.log" || echo "no SHOT lines: see $TOOLS/test-out/$NAME.log"
