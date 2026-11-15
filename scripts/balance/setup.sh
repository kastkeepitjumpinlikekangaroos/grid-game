#!/bin/bash
# setup.sh: make the work folder a balance pass is played in (BALANCE_DIR, outside the repo).
#   jars/        the simulator and everything it runs on, as built now (a pass measures against these
#                while the tree moves on: run setup.sh again to take the tree's code as it is)
#   orig/        the roster's sources as they are now: every candidate is these with levers applied
#   roster0.tsv  that roster, as built; cand/base is the candidate with no levers
#   venv/        a Python with numpy, for the solver
set -e
source "$(dirname "$0")/env.sh"
cd "$REPO"
bazel build //src/test/scala/com/gridgame/server:balance_sim
mkdir -p "$BALANCE_DIR"/{jars,orig/roster,cand/base,runs}
rm -f "$BALANCE_DIR"/jars/*.jar
n=0
for f in bazel-bin/src/test/scala/com/gridgame/server/balance_sim.jar \
         $(find -L bazel-bin/src/test/scala/com/gridgame/server/balance_sim.runfiles -name '*.jar'); do
  n=$((n + 1)); cp -f "$f" "$BALANCE_DIR/jars/$(printf %03d $n)_$(basename "$f")"
done
M=src/main/scala/com/gridgame/common/model
cp $M/roster/*.scala "$BALANCE_DIR/orig/roster/"
cp $M/ProjectileDef.scala $M/Trap.scala $M/ProjectileType.scala $M/CharacterId.scala "$BALANCE_DIR/orig/"
# The toolchain's own scalac, which compiles a candidate's roster in a few seconds
ls "$(bazel info output_base)"/external/rules_scala~~scala_deps~io_bazel_rules_scala_scala_{compiler,library,reflect}_*/scala-*[0-9].jar \
  | tr '\n' ':' > "$BALANCE_DIR/scalac.cp"
[ -x "$PY" ] || { python3 -m venv "$BALANCE_DIR/venv" && "$BALANCE_DIR/venv/bin/pip" install -q numpy; }
java -cp "$(classpath)" com.gridgame.server.RosterDump "$BALANCE_DIR/roster0.tsv"
cp "$BALANCE_DIR/roster0.tsv" "$BALANCE_DIR/cand/base/roster.tsv"
echo '{}' > "$BALANCE_DIR/cand/base/levers.json"
echo "ready in $BALANCE_DIR: $(grep -c '^C' "$BALANCE_DIR/roster0.tsv") characters, $(grep -c '^P' "$BALANCE_DIR/roster0.tsv") attacks"
