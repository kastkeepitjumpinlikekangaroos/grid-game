#!/bin/bash
# build.sh <levers.json> <name>: the candidate roster cand/<name> — the original sources with the
# levers applied (apply.py), each ability's listed damage moved with its projectile's (sync.py),
# compiled, and dumped as cand/<name>/roster.tsv. Its sources are what a pass ends by copying into
# the tree.
set -e
source "$(dirname "$0")/env.sh"
C="$BALANCE_DIR/cand/$2"
levers="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
rm -rf "$C"; mkdir -p "$C"
compile() {
  java -cp "$(cat "$BALANCE_DIR/scalac.cp")" scala.tools.nsc.Main -nowarn -cp "$(classpath)" -d "$C/classes" \
    "$C"/roster/*.scala "$C/ProjectileDef.scala" "$C/Trap.scala"
  java -cp "$(classpath "$C/classes")" com.gridgame.server.RosterDump "$C/roster.tsv"
}
cd "$HERE"
"$PY" apply.py "$levers" "$C"
mkdir -p "$C/classes"; compile
cp "$levers" "$C/levers.json"
"$PY" sync.py "$2"
cp "$C/roster.tsv" "$C/roster.before-sync.tsv"; compile
cmp "$C/roster.tsv" "$C/roster.before-sync.tsv" && rm "$C/roster.before-sync.tsv"
