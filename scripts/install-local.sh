#!/usr/bin/env bash
# Builds the signed release APK and installs it on the connected phone,
# without going through CI.
#
# It reuses the version code already installed, so it replaces the nightly
# in place (same key) and the next nightly still updates over it: release
# builds can't be downgraded.
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
KEYS="${WHATSUP_SIGNING_DIR:-$HOME/.local/share/whats-up-signing}"
# The system openjdk package has no javac; Android Studio's JBR does.
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"

installed=$("$ADB" shell dumpsys package app.whatsup | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
code="${installed:-1001}"
version="0.1.0-local.$(git rev-parse --short HEAD)$(git diff --quiet HEAD || echo "-dirty")"

WHATSUP_KEYSTORE="$KEYS/whatsup-release.p12" \
WHATSUP_KEYSTORE_PASSWORD="$(cat "$KEYS/password.txt")" \
    ./gradlew :app:assembleRelease -PversionCode="$code" -PversionName="$version"

"$ADB" install -r app/build/outputs/apk/release/app-release.apk
echo "Installed $version (versionCode $code)"
