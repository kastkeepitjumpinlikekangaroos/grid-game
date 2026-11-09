#!/usr/bin/env bash
# A local Kubernetes cluster playing the game (ops/kubernetes/CLAUDE.md): kind, Agones, the central
# server and a fleet of match servers. Log in at 127.0.0.1:25570. Run it again after a code change:
# it rebuilds the image and rolls everything onto it.
set -euo pipefail
cd "$(dirname "$0")"
CLUSTER=gridgame
AGONES_VERSION=1.62.0

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  kind create cluster --name "$CLUSTER" --config kind-cluster.yaml
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

# Agones: GameServers in our namespace, on the ports kind maps to this machine (26000-26009)
helm repo add agones https://agones.dev/chart/stable >/dev/null 2>&1 || true
helm repo update agones >/dev/null
kubectl get namespace gridgame >/dev/null 2>&1 || kubectl create namespace gridgame
helm upgrade --install agones agones/agones --version "$AGONES_VERSION" \
  --namespace agones-system --create-namespace \
  --set "gameservers.namespaces={gridgame}" \
  --set gameservers.minPort=26000 --set gameservers.maxPort=26009 \
  --set agones.ping.install=false --set agones.allocator.install=false \
  --wait --timeout 10m

./build.sh gridgame:dev
kind load docker-image gridgame:dev --name "$CLUSTER"

# The secret the match servers prove they share with the central server: made once, kept in the cluster
kubectl -n gridgame get secret gridgame-cluster >/dev/null 2>&1 || \
  kubectl -n gridgame create secret generic gridgame-cluster --from-literal=secret="$(openssl rand -hex 32)"

kubectl apply -k overlays/kind
# The same tag, a new image: start everything again on it
kubectl -n gridgame rollout restart deployment/central
kubectl -n gridgame rollout status deployment/central --timeout=5m
kubectl -n gridgame delete gameservers --all --wait=false >/dev/null 2>&1 || true
# At least one match server Ready again (the autoscaler keeps as many as its buffer of free slots needs)
for _ in $(seq 1 150); do
  [ "$(kubectl -n gridgame get fleet match-servers -o jsonpath='{.status.readyReplicas}')" -ge 1 ] 2>/dev/null && break
  sleep 2
done
kubectl -n gridgame get pods,fleet,gameservers
echo
echo "Log in at 127.0.0.1:25570. The central server's identity:"
kubectl -n gridgame logs deployment/central | grep "Server identity" | tail -1
