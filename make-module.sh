#!/usr/bin/env bash
# Builds the release APK and packages the KSU/Magisk priv-app module zip.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :app:assembleRelease --console=plain -q -Pcellscope.requireReleaseSigning "$@"
APK=app/build/outputs/apk/release/app-release.apk
VERSION=$(sed -n 's/^cellscope\.versionName=//p' gradle.properties)
IFS=. read -r MAJOR MINOR PATCH <<<"$VERSION"
CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))

# Stage a copy so the tracked module.prop keeps no build-specific version.
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp -R ksu-module/. "$STAGE"
mkdir -p "$STAGE/system/priv-app/CellScope"
cp "$APK" "$STAGE/system/priv-app/CellScope/CellScope.apk"
sed -i.bak -e "s/^version=.*/version=v$VERSION/" -e "s/^versionCode=.*/versionCode=$CODE/" "$STAGE/module.prop"
rm "$STAGE/module.prop.bak"

OUT=dist/cellscope-privapp-v$VERSION.zip
mkdir -p dist; rm -f "$OUT"
(cd "$STAGE" && zip -qr - . -x '.DS_Store' '*.gitkeep') > "$OUT"
echo "module: $OUT"
echo "apk:    $APK"
