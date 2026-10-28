#!/bin/sh
# Builds cleanyourtv's home screen (upstream/cleanyourtv/launcher, MIT) and re-signs it with the
# TidyTV release key, then copies it into the app as assets/launcher/cleanyourtv-home.apk.
# Builds in a temporary copy so the submodule stays untouched.
set -e
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
WORK="$(mktemp -d)"
# The lists follow the submodule's newest commit, but the launcher is built from a released tag: LAUNCHER_REF=v3.0.0.
REF="${LAUNCHER_REF:-v3.0.0}"
git -C upstream/cleanyourtv archive "$REF" launcher | tar -x -C "$WORK"
mv "$WORK/launcher/"* "$WORK/launcher/".[!.]* "$WORK/" 2>/dev/null || true
rmdir "$WORK/launcher" 2>/dev/null || true
echo "sdk.dir=$SDK" > "$WORK/local.properties"
(cd "$WORK" && ./gradlew -q assembleRelease --console=plain)
APK="$WORK/app/build/outputs/apk/release/app-release.apk"
OUT=app/src/main/assets/launcher/cleanyourtv-home.apk
if [ -f keys/signing.properties ]; then
  storePassword=$(sed -n 's/^storePassword=//p' keys/signing.properties)
  keyAlias=$(sed -n 's/^keyAlias=//p' keys/signing.properties)
  keyPassword=$(sed -n 's/^keyPassword=//p' keys/signing.properties)
  "$BT/zipalign" -f -p 4 "$APK" "$WORK/aligned.apk"
  "$BT/apksigner" sign --ks keys/tidytv-release.jks --ks-key-alias "$keyAlias" \
    --ks-pass "pass:$storePassword" --key-pass "pass:$keyPassword" --out "$OUT" "$WORK/aligned.apk"
else
  cp "$APK" "$OUT"
fi
"$BT/apksigner" verify "$OUT"
ls -la "$OUT"
rm -rf "$WORK"
