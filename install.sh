#!/usr/bin/env bash
# Installs the debug app (dev.satotek.cellscope.debug) over adb; it sits next to a release install.
# Use make-module.sh + KSU Manager for priv-app mode.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :app:assembleDebug --console=plain -q
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.satotek.cellscope.debug/dev.satotek.cellscope.MainActivity
