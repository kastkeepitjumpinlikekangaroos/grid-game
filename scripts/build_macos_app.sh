#!/usr/bin/env bash
# Package the Grid Game client as a proper macOS .app bundle via jpackage.
#
# A plain `bazel run` launches the JVM directly, so macOS shows "java" as the
# app name/icon in the Dock, Cmd+Tab switcher, and Force Quit dialog — there is
# no supported Java API to override that for an unbundled process. Wrapping the
# deploy jar in a real .app with an Info.plist (CFBundleName/CFBundleIconFile)
# is the only way macOS shows "Grid Game" and the wizard icon everywhere.
#
# Usage: scripts/build_macos_app.sh [mapeditor]
set -euo pipefail
cd "$(dirname "$0")/.."

TARGET="${1:-client}"

case "$TARGET" in
  client)
    APP_NAME="Grid Game"
    MAIN_CLASS="com.gridgame.client.Main"
    BAZEL_LABEL="//src/main/scala/com/gridgame/client:client_deploy.jar"
    DEPLOY_JAR="bazel-bin/src/main/scala/com/gridgame/client/client_deploy.jar"
    BUNDLE_ID="com.gridgame.client"
    ;;
  mapeditor)
    APP_NAME="Grid Game Map Editor"
    MAIN_CLASS="com.gridgame.mapeditor.Main"
    BAZEL_LABEL="//src/main/scala/com/gridgame/mapeditor:mapeditor_deploy.jar"
    DEPLOY_JAR="bazel-bin/src/main/scala/com/gridgame/mapeditor/mapeditor_deploy.jar"
    BUNDLE_ID="com.gridgame.mapeditor"
    ;;
  *)
    echo "Usage: $0 [client|mapeditor]" >&2
    exit 1
    ;;
esac

ICON="sprites/icon_wizard.icns"
DEST="dist/macos"

if [[ ! -f "$ICON" ]]; then
  echo "Missing $ICON — run: python3 scripts/generate_icon.py && scripts/generate_icns.sh" >&2
  exit 1
fi

if ! command -v jpackage >/dev/null; then
  echo "jpackage not found. Install a JDK 14+ (e.g. 'brew install openjdk') and ensure it's on PATH." >&2
  exit 1
fi

echo "Building $BAZEL_LABEL..."
bazel build "$BAZEL_LABEL"

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
mkdir -p "$WORKDIR/input"
cp "$DEPLOY_JAR" "$WORKDIR/input/app.jar"

rm -rf "$DEST/${APP_NAME}.app"
mkdir -p "$DEST"

JAVA_HOME_FOR_RUNTIME="${JPACKAGE_RUNTIME_HOME:-$(/usr/libexec/java_home)}"

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --input "$WORKDIR/input" \
  --main-jar app.jar \
  --main-class "$MAIN_CLASS" \
  --icon "$ICON" \
  --runtime-image "$JAVA_HOME_FOR_RUNTIME" \
  --mac-package-identifier "$BUNDLE_ID" \
  --dest "$DEST"

echo "Built $DEST/${APP_NAME}.app — launch with: open \"$DEST/${APP_NAME}.app\""
