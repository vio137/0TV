#!/bin/sh
# Build the redesigned launcher with its distinct package identity.
set -eu
cd "$(dirname "$0")/../launcher-fork/launcher"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew -PlauncherApplicationId=app.tvtidy.home :app:assembleRelease
# Signing differs from the public binary unless you own its original key.
