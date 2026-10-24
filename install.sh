#!/usr/bin/env bash
# Installs over adb. Default: the debug app (dev.satotek.cellscope.debug, debug key), which sits
# next to a release install. `./install.sh release` installs the release-key build instead, which
# can update a release priv-app in place. Use make-module.sh + KSU Manager to create the priv-app.
set -euo pipefail
cd "$(dirname "$0")"
case "${1:-debug}" in
    debug)
        ./gradlew :app:assembleDebug --console=plain -q
        APK=app/build/outputs/apk/debug/app-debug.apk; PKG=dev.satotek.cellscope.debug ;;
    release)
        ./gradlew :app:assembleRelease --console=plain -q -Pcellscope.requireReleaseSigning
        APK=app/build/outputs/apk/release/app-release.apk; PKG=dev.satotek.cellscope ;;
    *) echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac
adb install -r -g "$APK"
adb shell am start -n "$PKG/dev.satotek.cellscope.MainActivity"
