#!/usr/bin/env bash
# Runs the instrumented tests WITHOUT wiping the app database.
#
# ./gradlew connectedDebugAndroidTest uninstalls the app when it finishes, and uninstalling takes
# /data/user/0/com.yokodake.melete with it — including the training record. This script installs
# both APKs over the existing ones (install -r keeps app data) and drives the test runner directly,
# so whatever is in the app survives the run.
#
# Usage: scripts/device-tests.sh [class-or-method filter]
#   scripts/device-tests.sh
#   scripts/device-tests.sh com.yokodake.melete.data.MigrationTest
set -euo pipefail

ADB="${ADB:-$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
PACKAGE="com.yokodake.melete"
RUNNER="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"

cd "$(dirname "$0")/.."

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

if [ $# -gt 0 ]; then
  "$ADB" shell am instrument -w -e class "$1" "$RUNNER"
else
  "$ADB" shell am instrument -w "$RUNNER"
fi
