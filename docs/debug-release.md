# Everyday release and isolated debug app

Since 28 September 2026, the same project builds two separately installed applications:

| | Everyday app | Development app |
| --- | --- | --- |
| Build variant | `release` | `debug` |
| Launcher name | Melete | Melete Debug |
| Icon | Original colour illustration | Black-and-white illustration |
| Application ID | `com.yokodake.melete` | `com.yokodake.melete.debug` |
| Version name | `1.0` | `1.0-debug` |
| Instrumentation package | None | `com.yokodake.melete.debug.test` |

Each application has its own private database, preferences, timer state and recovery-copy directory. Installing, resetting or uninstalling **Melete Debug** does not reset the everyday app. Their training records do not synchronise. Files deliberately exported to shared storage are outside this isolation; use distinct filenames if exporting from both apps.

## First installation after the split

Keep the existing `com.yokodake.melete` application installed. Do not uninstall or clear it. Installing the new debug APK adds a second app and leaves its existing database alone. Debug starts with its own data; this is not the loss of the original record.

The existing app might itself be an older debug build, because both build types previously used the same ID. The new debug installation does not convert that existing app into release. To make it your stable everyday release, install a compatible release build over it as an update, preserving its ID and signing key. Do not do this blindly across a schema change: release has no destructive fallback and needs a real migration for a changed database schema. First export a recoverable backup through the existing app.

Release retains the project's existing debug-keystore signing configuration for compatibility with prior sideloaded releases. Do not change that key or its application ID as part of ordinary development. Keep the keystore safe. Separate application IDs, not separate signing keys, provide this build separation.

## Daily workflow

In Android Studio, select the **debug** build variant for normal Run/Debug. It installs **Melete Debug**, with the grayscale icon. Continue logging real training in the coloured **Melete** app.

To test realistic data, export a backup from Melete and restore that file inside Melete Debug. This creates an independent copy. Verify the app name before restoring; do not restore test records into the everyday app unintentionally.

Build commands (set `JAVA_HOME` to Android Studio's JBR if necessary):

```text
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

Installation is a separate step:

```text
./gradlew :app:installDebug
./gradlew :app:installRelease
```

`installRelease` updates the everyday app, so use it only when intentionally deploying a validated release. `installDebug` updates only the development app. APKs remain at `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

Launch explicitly:

```text
adb shell am start -n com.yokodake.melete.debug/com.yokodake.melete.MainActivity
adb shell am start -n com.yokodake.melete/com.yokodake.melete.MainActivity
```

The Kotlin namespace and activity/test class names remain `com.yokodake.melete`; do not append `.debug` to source-class names.

## Tests and device helpers

`scripts/device-tests.sh` builds debug and debug instrumentation, checks both Gradle output metadata files for the expected debug IDs before installing, and targets:

```text
com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner
```

There is no release-target option. `install -r` avoids deleting debug data during installation, but seed/import helpers can still change debug records, and a schema change migrates debug exactly as it will release. Treat the debug database as disposable. Gradle-managed instrumented tests can also remove/reset their debug installation; this no longer affects release data.

`LibrarySeed` and `DeviceActions` additionally refuse to modify a target whose package is not `com.yokodake.melete.debug`. Their example ADB commands use the new test package. Old installed `com.yokodake.melete.test` instrumentation belongs to the pre-split setup: do not invoke it. Updating the source does not disable an old APK still installed on the phone. It can be removed separately when deliberately cleaning up the old test installation; never remove the everyday `com.yokodake.melete` application to do so.

For debug database inspection, use `run-as com.yokodake.melete.debug`. This cannot inspect release data. For a release backup, use the in-app export; installing debug no longer provides a route to release's private database.

### Commands

Every device helper targets the debug runner, `com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner`. In Git Bash, set `MSYS_NO_PATHCONV=1` for any adb command with a phone path.

```text
# Instrumented suite, or one class
scripts/device-tests.sh
scripts/device-tests.sh com.yokodake.melete.data.PlanImportTest

# Seed Melete Debug: the test library, two weeks, a few logged days (-e planning false: exercises only)
adb shell am instrument -w -e seed library -e class com.yokodake.melete.LibrarySeed com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner

# Import the test library as the Backup & restore screen would (dry: preview only)
adb shell am instrument -w -e action import -e mode add -e scope today -e dry true -e class com.yokodake.melete.DeviceActions com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner

# Put back one of Melete Debug's recovery copies
adb shell run-as com.yokodake.melete.debug ls files/backups
adb shell am instrument -w -e action restore -e copy melete-before-restore-….json -e class com.yokodake.melete.DeviceActions com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner

# Copy Melete Debug's database off the phone (all three files)
for f in melete.db melete.db-wal melete.db-shm; do adb exec-out run-as com.yokodake.melete.debug cat "databases/$f" > "backups/$f"; done

# Put the test plan where either app's file picker can find it
adb push app/src/androidTest/assets/test-library.json /sdcard/Download/test-library.json
```

Seeding and `DeviceActions` refuse to run unless the target is `com.yokodake.melete.debug`.

## Database upgrades

Since the 5B baseline (schema 10), neither build has a destructive fallback: both open a changed schema only through a migration in `MeleteMigrations`, so a missing one fails in Melete Debug first, where its data does not matter. Every schema change needs its migration and `MigrationTest`; see `MeleteMigrations` for the steps. Never solve a release migration/install failure by uninstalling the everyday app, clearing its storage, or adding a destructive fallback.

## Verification

Before first use, inspect built APK identities: release must be `com.yokodake.melete`, debug `com.yokodake.melete.debug`, and the test APK must target the latter. Confirm the labels and adaptive icon overrides. On the phone, verify both launchers are visible and a debug-only edit does not appear in release. Phone installation/reset testing is separate from building and must be deliberate; do not clear real records as a verification step.
