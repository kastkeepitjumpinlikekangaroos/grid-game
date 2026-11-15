# Sourced by the balance scripts: where the repo and the work folder are.
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
: "${BALANCE_DIR:?set BALANCE_DIR to a work folder outside the repo (setup.sh makes it)}"
export BALANCE_DIR
PY="$BALANCE_DIR/venv/bin/python"
# Everything the simulator runs on, a candidate's classes first when one is given
classpath() { echo "${1:+$1:}$(ls "$BALANCE_DIR"/jars/*.jar | tr '\n' ':')"; }
