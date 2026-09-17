#!/usr/bin/env bash
# Plain user-app install over adb (root/dumpsys mode). Use make-module.sh + KSU Manager for priv-app mode.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :app:assembleDebug --console=plain -q
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.satotek.cellscope/.MainActivity
