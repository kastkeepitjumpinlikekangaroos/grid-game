#!/usr/bin/env bash
# Take the local cluster away, accounts and all.
set -euo pipefail
kind delete cluster --name gridgame
