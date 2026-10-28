#!/bin/sh
# Build the recovered tv-detox 1.5.7 source snapshot. Requires JDK 17 and Android SDK.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT/upstream/tv-detox"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${SDK:?Set ANDROID_HOME to your Android SDK}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
D8="$SDK/build-tools/36.0.0/d8"
[ -f "$JAR" ] && [ -x "$D8" ] || { echo 'Install platform 34 and build-tools 34.0.0 and 36.0.0.' >&2; exit 1; }
rm -rf out; mkdir -p out/classes out/dex out/gen/com/ansub/tvhome
{
 echo 'package com.ansub.tvhome;'
 echo 'final class Apps { static final String[] LIST = {'
 grep -v '^[[:space:]]*#' "${APPS:-apps.txt}" | tr -d ' \t\r' | grep -v '^$' | sed 's/.*/    "&",/'
 echo '}; }'
} > out/gen/com/ansub/tvhome/Apps.java
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 21 --target-sdk-version 34 out/res.zip
javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d out/classes $(find src out/gen -name '*.java')
"$D8" --min-api 21 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk; (cd out/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk
# Output stays unsigned. Supply your own signing identity with apksigner; do not overwrite the shipped asset blindly.
echo "Unsigned output: $PWD/out/aligned.apk"
