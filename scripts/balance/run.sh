#!/bin/bash
# run.sh <run name> <candidate, or - for the jars' own roster> <matches> <processes> [BalanceSim args...]
# Plays the matches side by side, each process a slice of them in a folder of its own, into
# runs/<run name>.csv. Add `pace=500,550 reach=1` for the way a player plays.
source "$(dirname "$0")/env.sh"
name=$1; cand=$2; total=$3; par=$4; shift 4
classes=""
if [ "$cand" != "-" ]; then
  classes="$BALANCE_DIR/cand/$cand/classes"
  [ -d "$classes/com" ] || { echo "no candidate $cand (build.sh makes one)"; exit 1; }
fi
CP="$(classpath "$classes")"
per=$(( (total + par - 1) / par ))
pids=""
for ((i = 0; i < par; i++)); do
  from=$(( i * per )); to=$(( from + per )); [ $to -gt $total ] && to=$total
  [ $from -ge $total ] && continue
  d="$BALANCE_DIR/cwd/$name.$i"; mkdir -p "$d"; ln -sfn "$REPO/worlds" "$d/worlds"
  ( cd "$d" && OTEL_SDK_DISABLED=true java -Xmx700m -XX:+UseSerialGC -cp "$CP" com.gridgame.server.BalanceSim \
      out="$BALANCE_DIR/runs/$name.$i.part" from=$from to=$to "$@" 2>>"$BALANCE_DIR/runs/$name.log" ) &
  pids="$pids $!"
done
wait $pids
cat "$BALANCE_DIR"/runs/$name.*.part > "$BALANCE_DIR/runs/$name.csv" && rm -f "$BALANCE_DIR"/runs/$name.*.part
rm -rf "$BALANCE_DIR"/cwd/$name.*
echo "$name: $(( $(wc -l < "$BALANCE_DIR/runs/$name.csv") / 14 )) matches"
