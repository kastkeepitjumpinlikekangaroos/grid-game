#!/usr/bin/env bash
# Package the Grid Game client (or map editor) for Linux via jpackage: a folder with a launcher and
# a Java runtime of its own, so nothing needs installing first, tarred up to unpack anywhere; and,
# with --deb, a Debian package, which also puts the game in the desktop's menu. The .deb is the way
# onto a Chromebook: with Linux turned on (Settings > About ChromeOS > Developers), a double-click on
# it in the Files app installs it, and the game shows up in the launcher.
#
# jpackage only packages for the system it runs on, so this runs it on Linux, for the processor
# asked for: here, when this is that Linux and has a JDK 21 with jpackage, and otherwise in a Docker
# container (eclipse-temurin:21-jdk-jammy), which Docker Desktop on a Mac runs for either processor.
# The jar is built here either way: Bazel builds client_linux and client_linux_arm64 from any machine.
#
# Usage: scripts/build_linux_package.sh [client|mapeditor] [--arch x86_64|arm64] [--deb]
#   --arch   the processor to package for: this machine's on Linux, x86_64 anywhere else
#   --deb    also build the .deb
#
#   -> dist/linux/<arch>/Grid Game/bin/Grid Game              the app, run where it is
#      dist/linux/grid-game-linux-<arch>.tar.gz                the same, to unpack anywhere
#      dist/linux/grid-game_1.0-<build>_<amd64|arm64>.deb      with --deb
set -euo pipefail

# What the .deb depends on: the Linux libraries the game loads, by their Debian 12 and Ubuntu 22.04
# package names, which the "t64" packages of Ubuntu 24.04 and Debian 13 provide too: GTK 3 and libXtst
# for JavaFX's windows; libGL and Mesa's drivers for JavaFX's and the match's OpenGL; the X11
# libraries GLFW loads as it starts (Xcursor makes the match's blank pointer, Xrandr finds the
# screens) and the ones the runtime's AWT links, which draws the match's text; ALSA for the sound.
DEB_DEPENDS="libgtk-3-0, libxtst6, libgl1, libgl1-mesa-dri, libx11-6, libxext6, libxrender1, libxi6, libxrandr2, libxcursor1, libxinerama1, libxxf86vm1, libfreetype6, libfontconfig1, libasound2"

# What a Docker build packages in: a JDK 21 on Ubuntu 22.04
DOCKER_IMAGE="eclipse-temurin:21-jdk-jammy"

TARGET=client
ARCH=""
DEB=false
INSIDE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    client|mapeditor) TARGET="$1" ;;
    --arch) ARCH="${2:-}"; shift ;;
    --arch=*) ARCH="${1#--arch=}" ;;
    --deb) DEB=true ;;
    # The packaging step alone, given the staged jar's folder: what the Docker container runs
    --package-in) INSIDE="${2:-}"; shift ;;
    *)
      echo "Usage: $0 [client|mapeditor] [--arch x86_64|arm64] [--deb]" >&2
      exit 1
      ;;
  esac
  shift
done

HOST_ARCH=""
if [[ "$(uname -s)" == "Linux" ]]; then
  case "$(uname -m)" in
    x86_64|amd64) HOST_ARCH=x86_64 ;;
    aarch64|arm64) HOST_ARCH=arm64 ;;
  esac
fi
if [[ -z "$ARCH" ]]; then ARCH="${HOST_ARCH:-x86_64}"; fi
case "$ARCH" in
  x86_64|amd64) ARCH=x86_64; LABEL_SUFFIX=""; DOCKER_PLATFORM=linux/amd64 ;;
  arm64|aarch64) ARCH=arm64; LABEL_SUFFIX="_arm64"; DOCKER_PLATFORM=linux/arm64 ;;
  *) echo "Unknown --arch '$ARCH': x86_64 or arm64" >&2; exit 1 ;;
esac

case "$TARGET" in
  client)
    APP_NAME="Grid Game"
    PACKAGE_NAME="grid-game"
    DESCRIPTION="Multiplayer 2D isometric arena game"
    MAIN_CLASS="com.gridgame.client.Main"
    # What JavaFX names the app's windows on Linux (its Application's class), and the match's window
    # after them (MatchWindow): the menu entry claims them by it, so the desktop and a Chromebook's
    # shelf show them under the game's own icon
    WM_CLASS="com.gridgame.client.ClientMain"
    BAZEL_TARGET="//src/main/scala/com/gridgame/client:client_linux${LABEL_SUFFIX}"
    DEPLOY_JAR="bazel-bin/src/main/scala/com/gridgame/client/client_linux${LABEL_SUFFIX}_deploy.jar"
    # Heap sizing, as the client's jvm_flags in BUILD.bazel (a deploy jar carries none)
    JAVA_OPTIONS="-Xms64m -Xmx768m"
    ;;
  mapeditor)
    APP_NAME="Grid Game Map Editor"
    PACKAGE_NAME="grid-game-map-editor"
    DESCRIPTION="Map editor for Grid Game"
    MAIN_CLASS="com.gridgame.mapeditor.Main"
    WM_CLASS="com.gridgame.mapeditor.MapEditorApp"
    BAZEL_TARGET="//src/main/scala/com/gridgame/mapeditor:mapeditor_linux${LABEL_SUFFIX}"
    DEPLOY_JAR="bazel-bin/src/main/scala/com/gridgame/mapeditor/mapeditor_linux${LABEL_SUFFIX}_deploy.jar"
    JAVA_OPTIONS=""
    ;;
esac

# Package what has been staged in $1 (app.jar, the icon) into $1/.., with a runtime made from the JDK
# at $2
package() {
  local stage="$1" jdk="$2"
  local dest; dest="$(cd "$(dirname "$stage")" && pwd)"
  local image_dir="$dest/$ARCH"
  local runtime="$stage/runtime"

  # The runtime: every module of the JDK but the incubating ones (whose presence a launch warns of),
  # without debug information, man pages or C headers, which is 170MB where the whole JDK is 300.
  # Leaving modules out to save more risks one the game reaches only now and then (the elliptic
  # curves of the server's TLS are a module of their own in Java 21). A JDK installed without its
  # jmods (some distributions package them apart) can't be cut down, and is taken whole.
  rm -rf "${runtime:?}"
  if [[ -d "$jdk/jmods" && -x "$jdk/bin/jlink" ]]; then
    local modules
    modules="$("$jdk/bin/java" --list-modules | sed 's/@.*//' | grep -v '^jdk\.incubator\.' | paste -sd, -)"
    "$jdk/bin/jlink" --module-path "$jdk/jmods" --add-modules "$modules" \
      --strip-debug --no-man-pages --no-header-files --output "$runtime"
  else
    runtime="$jdk"
  fi

  local java_option_args=()
  for opt in $JAVA_OPTIONS; do java_option_args+=(--java-options "$opt"); done

  rm -rf "${image_dir:?}/${APP_NAME}"
  mkdir -p "$image_dir"
  jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --input "$stage/input" \
    --main-jar app.jar \
    --main-class "$MAIN_CLASS" \
    --icon "$stage/icon.png" \
    --runtime-image "$runtime" \
    ${java_option_args[@]+"${java_option_args[@]}"} \
    --dest "$image_dir"
  tar -C "$image_dir" -czf "$dest/${PACKAGE_NAME}-linux-${ARCH}.tar.gz" "$APP_NAME"

  if $DEB; then
    local resources="$stage/resources"
    mkdir -p "$resources"
    # The menu entry, as jpackage's own but for the window class it claims
    cat > "$resources/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Name=APPLICATION_NAME
Comment=APPLICATION_DESCRIPTION
Exec=APPLICATION_LAUNCHER
Icon=APPLICATION_ICON
Terminal=false
Type=Application
Categories=DEPLOY_BUNDLE_CATEGORY
StartupWMClass=$WM_CLASS
EOF
    # The package's dependencies are ours alone. jpackage adds those it finds the runtime linking, by
    # the names this system gives them: built on Ubuntu 24.04, that was libasound2t64, which Debian 12
    # (a Chromebook's) has never heard of, and the package wouldn't install there. And no xdg-utils,
    # which jpackage adds for a scripts' tool we don't use (below).
    cat > "$resources/control" <<EOF
Package: APPLICATION_PACKAGE
Version: APPLICATION_VERSION_WITH_RELEASE
Section: APPLICATION_SECTION
Maintainer: APPLICATION_MAINTAINER
Priority: optional
Architecture: APPLICATION_ARCH
Provides: APPLICATION_PACKAGE
Description: APPLICATION_DESCRIPTION
Depends: libc6, $DEB_DEPENDS
Installed-Size: APPLICATION_INSTALLED_SIZE
EOF
    # The entry goes where every desktop, and a Chromebook's launcher, looks for one as the package
    # is installed, and comes away as it is removed. Not with xdg-desktop-menu, as in jpackage's own
    # scripts: it refuses wherever there is no /etc/xdg/menus, which only a desktop's menu packages
    # make, and failing, it failed the whole install or removal (Debian 12 without a desktop). On
    # such a system there may be no /usr/share/applications either, until this makes it.
    local entry="/opt/$PACKAGE_NAME/lib/$PACKAGE_NAME-${APP_NAME// /_}.desktop"
    cat > "$resources/postinst" <<EOF
#!/bin/sh
set -e
if [ "\$1" = configure ]; then
    install -D -m 644 "$entry" "/usr/share/applications/$(basename "$entry")"
    update-desktop-database -q /usr/share/applications 2>/dev/null || true
fi
exit 0
EOF
    cat > "$resources/prerm" <<EOF
#!/bin/sh
set -e
case "\$1" in
    remove|upgrade|deconfigure)
        rm -f "/usr/share/applications/$(basename "$entry")"
        update-desktop-database -q /usr/share/applications 2>/dev/null || true
        ;;
esac
exit 0
EOF
    # Every build a newer package than the last (1.0-<when it was built>), so installing a new one
    # over an old one replaces it: at the same version, apt calls it installed already and keeps
    # the old one. The last build's goes.
    rm -f "$dest/${PACKAGE_NAME}"_*_"$(dpkg --print-architecture)".deb
    jpackage \
      --type deb \
      --name "$APP_NAME" \
      --app-image "$image_dir/$APP_NAME" \
      --app-version 1.0 \
      --description "$DESCRIPTION" \
      --icon "$stage/icon.png" \
      --resource-dir "$resources" \
      --linux-package-name "$PACKAGE_NAME" \
      --linux-app-release "$(date -u +%Y%m%d%H%M)" \
      --linux-app-category games \
      --linux-menu-group Game \
      --linux-shortcut \
      --linux-package-deps "$DEB_DEPENDS" \
      --dest "$dest"
  fi
}

# Inside the Docker container: package, and give the files back to whoever ran the script
if [[ -n "$INSIDE" ]]; then
  if $DEB; then
    apt-get update -qq >/dev/null
    apt-get install -y -qq --no-install-recommends fakeroot >/dev/null
  fi
  package "$INSIDE" "$JAVA_HOME"
  rm -rf "${INSIDE:?}"
  chown -R "${HOST_UID:-0}:${HOST_GID:-0}" "$(dirname "$INSIDE")"
  exit 0
fi

cd "$(dirname "$0")/.."

ICON="sprites/icon_wizard_256.png"
if [[ ! -f "$ICON" ]]; then
  echo "Missing $ICON — run: python3 scripts/generate_icon.py" >&2
  exit 1
fi

echo "Building ${BAZEL_TARGET}_deploy.jar..."
bazel build "${BAZEL_TARGET}_deploy.jar"

# Staged in dist/, which the container sees
STAGE="dist/linux/.stage-${TARGET}-${ARCH}"
rm -rf "${STAGE:?}"
mkdir -p "$STAGE/input"
cp "$DEPLOY_JAR" "$STAGE/input/app.jar"
chmod u+w "$STAGE/input/app.jar"
cp "$ICON" "$STAGE/icon.png"

# Here, on Linux of the processor asked for with jpackage (and, for a .deb, Debian's tools to make
# one), and otherwise in Docker
LOCAL=false
if [[ "$HOST_ARCH" == "$ARCH" ]] && command -v jpackage >/dev/null; then
  if ! $DEB || { command -v dpkg-deb >/dev/null && command -v fakeroot >/dev/null; }; then LOCAL=true; fi
fi

if $LOCAL; then
  # The runtime is made from JPACKAGE_RUNTIME_HOME, JAVA_HOME, or the JDK jpackage is from: a 21 or
  # later, which the game's classes are built for
  JDK="${JPACKAGE_RUNTIME_HOME:-${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v jpackage)")")")}}"
  JDK_VERSION="$("$JDK/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
  if [[ "${JDK_VERSION%%.*}" -lt 21 ]]; then
    echo "The game needs Java 21, and $JDK is Java $JDK_VERSION: point JAVA_HOME (or JPACKAGE_RUNTIME_HOME) at a JDK 21." >&2
    exit 1
  fi
  package "$STAGE" "$JDK"
  rm -rf "${STAGE:?}"
else
  if ! command -v docker >/dev/null || ! docker info >/dev/null 2>&1; then
    echo "Packaging for Linux on $ARCH needs that Linux with a JDK 21's jpackage (and dpkg-deb and" >&2
    echo "fakeroot for --deb: sudo apt install fakeroot), or Docker running to stand in for it." >&2
    exit 1
  fi
  echo "Packaging in Docker ($DOCKER_IMAGE, $DOCKER_PLATFORM)..."
  DEB_FLAG=()
  if $DEB; then DEB_FLAG=(--deb); fi
  docker run --rm --platform "$DOCKER_PLATFORM" \
    -v "$PWD/scripts/build_linux_package.sh:/build_linux_package.sh:ro" \
    -v "$PWD/dist/linux:/dist" \
    -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
    "$DOCKER_IMAGE" \
    bash /build_linux_package.sh "$TARGET" --arch "$ARCH" ${DEB_FLAG[@]+"${DEB_FLAG[@]}"} \
      --package-in "/dist/.stage-${TARGET}-${ARCH}"
fi

echo "Built dist/linux/$ARCH/$APP_NAME and dist/linux/${PACKAGE_NAME}-linux-${ARCH}.tar.gz"
if $DEB; then echo "Built $(ls -t dist/linux/"${PACKAGE_NAME}"_*.deb | head -1)"; fi
