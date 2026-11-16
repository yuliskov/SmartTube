#!/usr/bin/env bash
# Build the stbeta debug APK in the android-dev Docker container with persistent
# gradle cache + Android home (so the debug keystore survives between runs and
# `adb install -r` keeps working without uninstalling).
#
# Usage:  ./build-and-install.sh [device-ip[:port]]
# If no argument is given, only builds (no install).

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_CACHE="/opt/sttv-cache/gradle"
ANDROID_HOME_CACHE="/opt/sttv-cache/android-home"
IMAGE="reactnativecommunity/react-native-android:latest"
APK_GLOB="smarttubetv/build/outputs/apk/stbeta/debug/SmartTube_beta_*_arm64-v8a.apk"

mkdir -p "$GRADLE_CACHE" "$ANDROID_HOME_CACHE"

echo "==> Building stbeta debug APK..."
docker run --rm \
  -v "$PROJECT_DIR:/project" \
  -v "$GRADLE_CACHE:/root/.gradle" \
  -v "$ANDROID_HOME_CACHE:/root/.android" \
  -w /project \
  -e ANDROID_HOME=/opt/android \
  -e ANDROID_SDK_ROOT=/opt/android \
  -e GRADLE_USER_HOME=/root/.gradle \
  "$IMAGE" \
  bash -c "bash ./gradlew :smarttubetv:assembleStbetaDebug --no-daemon"

APK="$(ls -t $PROJECT_DIR/$APK_GLOB 2>/dev/null | head -1 || true)"
if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK" >&2
  exit 1
fi
echo "==> APK: $APK"

if [[ $# -ge 1 ]]; then
  DEVICE="$1"
  [[ "$DEVICE" != *:* ]] && DEVICE="${DEVICE}:5555"
  echo "==> Installing on $DEVICE ..."
  ADB="${ADB:-/root/platform-tools/adb}"
  "$ADB" connect "$DEVICE"
  "$ADB" -s "$DEVICE" install -r "$APK"
fi
