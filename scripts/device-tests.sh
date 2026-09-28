#!/usr/bin/env bash
# Runs instrumented tests against Melete Debug, never the everyday release app.
# Installs with -r to preserve debug data on install; the tests themselves use in-memory
# databases. Release uses a separate application ID and private storage.
#
# Usage: scripts/device-tests.sh [class-or-method filter]
#   scripts/device-tests.sh
#   scripts/device-tests.sh com.yokodake.melete.data.MigrationTest
set -euo pipefail

ADB="${ADB:-$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
readonly PACKAGE="com.yokodake.melete.debug"
readonly RUNNER="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"

cd "$(dirname "$0")/.."

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

# Reject stale builds from before the application-ID split before installing either APK.
check_package() {
  local metadata="$1" expected="$2" actual
  actual="$(sed -n 's/^[[:space:]]*"applicationId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$metadata")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Refusing to install: $metadata must identify $expected" >&2
    exit 1
  fi
}
check_package app/build/outputs/apk/debug/output-metadata.json "$PACKAGE"
check_package app/build/outputs/apk/androidTest/debug/output-metadata.json "$PACKAGE.test"

"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

if [ $# -gt 0 ]; then
  "$ADB" shell am instrument -w -e class "$1" "$RUNNER"
else
  "$ADB" shell am instrument -w "$RUNNER"
fi
