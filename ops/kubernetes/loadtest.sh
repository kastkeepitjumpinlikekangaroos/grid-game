#!/usr/bin/env bash
# What one match server can play at once (ops/kubernetes/CLAUDE.md, *How many matches a match server
# plays*): the central server, one match server held to a pod's CPU and memory, and a driver of real
# clients (e2e:load_driver), each a container on a network of its own in Docker's VM: Linux, cgroup
# limits as a pod has them, the kernel's own networking between them, and one clock. The driver adds
# matches stage by stage; the match server logs its status every 10s; Docker's stats are sampled.
#
#   ops/kubernetes/loadtest.sh <out-dir> [--cpus=n] [--pin=6-7 --others=0-5 [--hog=2]] [--memory=1g]
#                              [--java="-XX:MaxRAMPercentage=75"] [--matches=64]
#                              [-- <driver flags: --stages=4,8,16 --players=8 ...>]
#
# --cpus is a hard CPU limit (a pod's `limits.cpu`: its quota, every 100ms); none by default.
# --pin keeps the match server to those of the VM's CPUs (and --others the central server and the
# driver to the rest), and --hog=n runs n busy loops beside it there with the same CPU shares: a node
# whose every core is taken, where a pod gets what it requests and no more (pinned to two with two
# loops, the match server gets one core when busy, as a pod requesting one would).
# --matches is the match server's capacity in slots (high by default: the load decides here, not it).
# Leaves match.log, central.log, driver.log and stats.csv in <out-dir>, and prints a stage per line
# (loadtest_report.py). Nothing is kept between runs: the accounts are the central container's own.
set -euo pipefail
OUT="$1"; shift
CPUS=0; PIN=""; OTHERS=""; HOG=0; MEM=1g; JAVA_OPTS="-XX:MaxRAMPercentage=75"; MATCHES=64; DRIVER_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --cpus=*) CPUS="${1#*=}" ;;
    --pin=*) PIN="${1#*=}" ;;
    --others=*) OTHERS="${1#*=}" ;;
    --hog=*) HOG="${1#*=}" ;;
    --memory=*) MEM="${1#*=}" ;;
    --java=*) JAVA_OPTS="${1#*=}" ;;
    --matches=*) MATCHES="${1#*=}" ;;
    --) shift; DRIVER_ARGS=("$@"); break ;;
    *) echo "loadtest: unknown $1" >&2; exit 2 ;;
  esac
  shift
done

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
NET=gg-load
IMAGE=gridgame:load
CENTRAL_IP=172.31.250.2
MATCH_IP=172.31.250.3

cd "$ROOT"
"$HERE/build.sh" "$IMAGE" >/dev/null
bazel build //src/test/scala/com/gridgame/e2e:load_driver_deploy.jar 2>/dev/null
cp -f bazel-bin/src/test/scala/com/gridgame/e2e/load_driver_deploy.jar "$OUT/load_driver.jar"
chmod u+w "$OUT/load_driver.jar"

docker network inspect "$NET" >/dev/null 2>&1 || docker network create --subnet 172.31.250.0/24 "$NET" >/dev/null
cleanup() {
  [ -n "${SAMPLER:-}" ] && kill "$SAMPLER" 2>/dev/null || true
  docker rm -f gg-load-central gg-load-match gg-load-driver gg-load-hog >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

LIMITS=(--memory="$MEM")
[ "$CPUS" != "0" ] && LIMITS+=(--cpus="$CPUS")
[ -n "$PIN" ] && LIMITS+=(--cpuset-cpus="$PIN")
REST=()
[ -n "$OTHERS" ] && REST+=(--cpuset-cpus="$OTHERS")

# The run's own secret, through the environment only (never on a command line)
GRIDGAME_CLUSTER_SECRET="$(openssl rand -hex 32)"
export GRIDGAME_CLUSTER_SECRET

# Every client comes from the driver's one address: its connections aren't to be counted against it
docker run -d --name gg-load-central --network "$NET" --ip "$CENTRAL_IP" ${REST[@]+"${REST[@]}"} \
  -e GRIDGAME_CLUSTER_SECRET -e OTEL_SDK_DISABLED=true -e GRIDGAME_CONNECTIONS_PER_MINUTE=1000000 \
  -w /data "$IMAGE" java -Xmx512m -jar /app/server.jar 25565 --control-port=25575 >/dev/null

docker run -d --name gg-load-match --network "$NET" --ip "$MATCH_IP" "${LIMITS[@]}" \
  -e GRIDGAME_CLUSTER_SECRET -e OTEL_SDK_DISABLED=true -e GRIDGAME_CONNECTIONS_PER_MINUTE=1000000 \
  -w /app "$IMAGE" sh -c "exec java $JAVA_OPTS -jar /app/match_server.jar --central=$CENTRAL_IP:25575 \
    --public-host=$MATCH_IP --port=7777 --matches=$MATCHES --status-seconds=10" >/dev/null

# A node with no core to spare: busy loops on the match server's CPUs, at the same shares
if [ "$HOG" -gt 0 ] && [ -n "$PIN" ]; then
  docker run -d --name gg-load-hog --cpuset-cpus="$PIN" "$IMAGE" \
    sh -c "for i in \$(seq 1 $HOG); do (while :; do :; done) & done; wait" >/dev/null
fi

# Wait for the match server to register
for _ in $(seq 1 60); do
  docker logs gg-load-central 2>&1 | grep -q "registered" && break
  sleep 1
done

# Docker's own count of each container's CPU and memory, every couple of seconds (of those running)
( set +e +o pipefail
  while true; do
    ids="$(docker ps -q --filter name=gg-load-)"
    [ -n "$ids" ] && docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' $ids 2>/dev/null \
      | sed "s/^/$(date +%s),/"
    sleep 1
  done ) > "$OUT/stats.csv" &
SAMPLER=$!

docker run -d --name gg-load-driver --network gg-load ${REST[@]+"${REST[@]}"} \
  -e GRIDGAME_AUDIO=off -e OTEL_SDK_DISABLED=true -v "$OUT/load_driver.jar:/app/load_driver.jar:ro" -w /app \
  "$IMAGE" java -Xmx2500m -Duser.home=/tmp -Dio.netty.eventLoopThreads=1 -cp /app/load_driver.jar \
  com.gridgame.e2e.LoadDriver --host="$CENTRAL_IP" --port=25565 "${DRIVER_ARGS[@]}" >/dev/null
docker logs -f gg-load-driver 2>&1 | grep --line-buffered -E '^(STAGE|Load:)' || true
docker wait gg-load-driver >/dev/null

docker logs -t gg-load-match > "$OUT/match.log" 2>&1
docker logs -t gg-load-central > "$OUT/central.log" 2>&1
docker logs -t gg-load-driver > "$OUT/driver.log" 2>&1
echo "cpus=$CPUS pin=$PIN hog=$HOG memory=$MEM java=$JAVA_OPTS matches=$MATCHES driver=${DRIVER_ARGS[*]}" > "$OUT/run.txt"
python3 "$HERE/loadtest_report.py" "$OUT"
