#!/bin/bash
# round.sh <name> <levers.json> <seed> [matches=1184] [processes=7]: a round of a pass — the candidate
# built, then played both ways: as the bots are (runs/<name>_A.csv) and the way a player plays
# (runs/<name>_B.csv). 1,184 matches put every fighter in 160 and every ranged character in 142.
set -e
source "$(dirname "$0")/env.sh"
n=${4:-1184}; par=${5:-7}
"$HERE/build.sh" "$2" "$1"
"$HERE/run.sh" "${1}_A" "$1" $n $par seed=$3
"$HERE/run.sh" "${1}_B" "$1" $n $par seed=$3 pace=500,550 reach=1
