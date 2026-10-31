#!/usr/bin/env bash
# Bazel's workspace status (.bazelrc: --workspace_status_command): what a build is stamped with, which
# //src/main/scala/com/gridgame/common:build_info puts in every jar as build_info.properties
# (common/BuildInfo). STABLE_ keys rebuild what is stamped when they change; the others don't.
cd "$(dirname "$0")/.." || exit 0
commit=$(git rev-parse --short=10 HEAD 2>/dev/null || echo unknown)
# Changes to tracked files only: an untracked scratch file doesn't make a build "dirty"
if git diff --quiet HEAD -- 2>/dev/null; then dirty=0; else dirty=1; fi
echo "STABLE_GIT_COMMIT ${commit}"
echo "STABLE_GIT_DIRTY ${dirty}"
echo "BUILD_UTC $(date -u +%Y-%m-%dT%H:%M:%SZ)"
