#!/usr/bin/env bash
# Builds the release APK and packages the KSU/Magisk priv-app module zip.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :app:assembleRelease --console=plain -q
APK=app/build/outputs/apk/release/app-release.apk
cp "$APK" ksu-module/system/priv-app/CellScope/CellScope.apk
OUT=dist/cellscope-privapp-$(date +%Y%m%d-%H%M).zip
mkdir -p dist; rm -f "$OUT"
(cd ksu-module && zip -qr "../$OUT" . -x '.DS_Store')
echo "module: $OUT"
echo "apk:    $APK"
