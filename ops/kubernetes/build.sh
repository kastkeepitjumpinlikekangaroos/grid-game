#!/usr/bin/env bash
# The game servers' image (gridgame:dev): the central server's and a match server's deploy jars, and
# the worlds, on a Java 21 runtime. Built for this machine's processor, which is the kind cluster's.
set -euo pipefail
cd "$(dirname "$0")/../.."
TAG="${1:-gridgame:dev}"

bazel build //src/main/scala/com/gridgame/server:server_deploy.jar \
            //src/main/scala/com/gridgame/server:match_server_deploy.jar

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -f bazel-bin/src/main/scala/com/gridgame/server/server_deploy.jar "$STAGE/server.jar"
cp -f bazel-bin/src/main/scala/com/gridgame/server/match_server_deploy.jar "$STAGE/match_server.jar"
mkdir "$STAGE/worlds"
cp worlds/*.json "$STAGE/worlds/"
cp ops/kubernetes/Dockerfile "$STAGE/"
chmod -R u+w "$STAGE"

docker build -t "$TAG" "$STAGE"
echo "Built $TAG"
