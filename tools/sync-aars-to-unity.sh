#!/usr/bin/env bash
# Copy built Bidscube SDK AARs (lite / full) into Unity adapter packages.
# Run from repo root after:
#   ./gradlew :sdk:assembleLiteNoVideoRelease :sdk:assembleFullVideoRelease
#
# Usage:
#   BIDSCUBE_VERSION=1.2.11 ./tools/sync-aars-to-unity.sh [path/to/Runtime/Plugins/Android ...]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="${BIDSCUBE_VERSION:-1.2.11}"

LITE_BUILT="$ROOT/sdk/build/outputs/aar/sdk-liteNoVideo-release.aar"
FULL_BUILT="$ROOT/sdk/build/outputs/aar/sdk-fullVideo-release.aar"

LITE_NAME="bidscube-sdk-lite-no-video-${VER}.aar"
FULL_NAME="bidscube-sdk-full-video-${VER}.aar"

if [[ ! -f "$LITE_BUILT" ]]; then
  echo "Missing $LITE_BUILT — run :sdk:assembleLiteNoVideoRelease first" >&2
  exit 1
fi
if [[ ! -f "$FULL_BUILT" ]]; then
  echo "Missing $FULL_BUILT — run :sdk:assembleFullVideoRelease first" >&2
  exit 1
fi

DEFAULT_TARGETS=(
  "$ROOT/../AppLovin-SDK-Unity/Runtime/Plugins/Android"
  "$ROOT/../LevelPlay-SDK-for-BidsCube-Unity/Runtime/Plugins/Android"
)

TARGETS=("$@")
if [[ ${#TARGETS[@]} -eq 0 ]]; then
  TARGETS=("${DEFAULT_TARGETS[@]}")
fi

for dir in "${TARGETS[@]}"; do
  if [[ ! -d "$dir" ]]; then
    echo "Skip (not a directory): $dir" >&2
    continue
  fi
  cp -f "$LITE_BUILT" "$dir/$LITE_NAME"
  cp -f "$FULL_BUILT" "$dir/$FULL_NAME"
  echo "Updated $dir/$LITE_NAME and $FULL_NAME"
done
