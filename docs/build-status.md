# Build status

Last updated: 2026-09-20, end of phase 2.

## Completed phases

### Phase 1 — runnable foundation ✅

- Kept the existing Android Studio toolchain (AGP 9.4.1 / Gradle 9.6 / Kotlin 2.2.10 /
  compileSdk 37 / minSdk 33 / Compose BOM 2026.02.01) and added Room, KSP and
  kotlinx.serialization to it.
- **This week** screen: one vertical list with an unscheduled section on top, then Monday–Sunday,
  today highlighted, previous/next week and a *Today* action, edge-to-edge and inset-aware.
- Minimal persistence: exercises, prescriptions, exercise occurrences (Room, schema v1).
- Explicit debug-only sample data: overflow menu → *Insert sample data in this week* /
  *Remove all sample data*. Nothing is ever seeded automatically.
- Documentation files created.

### Phase 2 — create an exercise and log it with minimal friction ✅

- Exercise editor: name, mode (repetitions / timed sets / duration-only activity), unilateral
  flag, optional measurement unit with explicit load semantics (total / added / assistance),
  optional variation notes, and the default prescription. No exercise is hardcoded.
- Library picker, reached from a `+` on the unscheduled heading and on every day heading. Picking
  copies the exercise and its prescription into that slot. Library entries can be edited from the
  picker.
- Logger, opened by tapping an occurrence: the planned prescription (editable for this copy only),
  previous results for the same stable exercise, the sets recorded this time, an occurrence-level
  comment, and mark done / skip.
- Set entry bar pinned to the bottom: prefilled values, one tap to confirm, undo, tap a recorded
  set to correct it, optional RPE/RIR behind a toggle, explicit L/R chips for unilateral work.
- Remove from this week, offered in the logger only while nothing has been recorded.
- Schema v2 with a real migration (`MIGRATION_1_2`): `training_sessions` and `actual_sets` added,
  plus `exercises.measurementMeaning`, `exercises.notes` and
  `exercise_occurrences.measurementMeaningSnapshot`.
- Navigation added (`navigation-compose`, type-safe routes in `ui/Navigation.kt`).

Not implemented yet, by design: timer, modules, duration capture, dashboard, export — and moving
an occurrence that already has a date, which belongs to phase 4 because it needs the explicit
distinction between moving remaining planned work and correcting a historical training date.

## Architecture as built

```
com.yokodake.melete
  MeleteApplication.kt      Application + AppContainer (manual DI, no framework)
  MainActivity.kt           single activity, Compose, edge-to-edge
  core/WeekMath.kt          Monday-based ISO week arithmetic and labels (pure, unit-tested)
  ui/Navigation.kt          type-safe routes + NavHost (week, picker, editor, logger)
  data/
    MeleteDatabase.kt       Room database v2, exportSchema = true, explicit migrations only
    MeleteConverters.kt     enum <-> String converters (stored names are part of the format)
    TrainingRepository.kt   week, library, scheduling and logging operations
    DevSampleData.kt        explicitly marked development data
    dao/TrainingDao.kt      TrainingDao, LibraryDao, LoggingDao
    entity/Entities.kt      ExerciseEntity, PrescriptionEntity, ExerciseOccurrenceEntity,
                            TrainingSessionEntity, ActualSetEntity
    model/PrescriptionPayload.kt  versioned named-field prescription payload
    model/ActualSetPayload.kt     versioned named-field actual-set payload
  ui/components/            PrescriptionFormState + the shared prescription fields
  ui/week/                  WeekScreen, WeekViewModel, WeekUiState, PrescriptionSummary
  ui/library/               LibraryPicker, ExerciseEditor (+ view models)
  ui/logger/                LoggerScreen, LoggerViewModel, SetDraft
```

## Important choices

- **Week identity.** The training week is Monday–Sunday (ISO-8601) and is identified by its
  Monday's `LocalDate`. `exercise_occurrences.weekStartEpochDay` is that Monday;
  `trainingDateEpochDay` is the local training date, or `NULL` for an unscheduled item inside the
  week. Local dates are stored as epoch days, event timestamps as epoch milliseconds — two
  different columns, never derived from each other (invariant 9).
- **Prescription payload as versioned JSON.** `prescriptions.payloadJson` holds a named-field
  document (`sets`, `targetReps`, `targetDurationSeconds`, `restSeconds`, `measurement`, `rpe`,
  `rir`) with `payloadVersion` alongside it. Absent values are serialized as `null`, never `0`
  (invariant 7). Decoding uses `ignoreUnknownKeys` so a payload written by a newer version is not
  lost. A measurement carries `value`, `unit` and an explicit `meaning`
  (`TOTAL_LOAD` / `ADDED_LOAD` / `ASSISTANCE`) so added load and assistance can never collapse into
  one quantity (invariant 6).
- **Prescriptions are value rows, shared by pointer, copied on scheduling.** An exercise points at
  its default prescription (`exercises.defaultPrescriptionId`) and an occurrence points at its own
  copy (`exercise_occurrences.prescriptionId`), so editing a library default cannot reach work
  already placed in a week (invariant 1).
- **Editing never mutates a shared row.** Editing a library default or a scheduled copy inserts a
  *new* `prescriptions` row and repoints its owner. An actual set therefore keeps pointing at what
  was really planned when it was performed. Superseded rows are kept, not deleted.
- **Occurrences snapshot their template.** Name, mode, unilateral flag, measurement unit and
  meaning are copied onto the occurrence. `exerciseId` is lineage only and deliberately carries
  **no** foreign key, so a renamed or deleted library entry cannot rewrite or delete history
  (invariants 1 and 5).
- **Actual sets are identified by id, never by set number.** `actual_sets.orderIndex` is only a
  position; `id` is identity. Deleting or inserting a set cannot move a correction onto another
  record. The number shown on screen is computed, and counted per side for unilateral work
  (invariant 2).
- **Actual payloads are independent of prescriptions.** `actual_sets.payloadJson` carries its own
  versioned named fields (`reps`, `durationSeconds`, `measurement`, `rpe`, `rir`);
  `prescriptionId` is an optional reference. An unplanned set is ordinary, and six performed sets
  against four planned ones need no special case (invariants 2 and 3).
- **Sessions are implicit.** The first confirmed set of a training date creates that day's
  `training_sessions` row and every later set that day reuses it. The user never starts or ends a
  session, and several exercises trained on one date stay one session.
- **An undated item is filed when it is logged.** Logging an unscheduled occurrence assigns it to
  the target date — today by default, overridable in the logger for backfilling.
- **Deleting an occurrence that has actuals is refused**, enforced by `ON DELETE RESTRICT` and
  checked in `deleteOccurrenceIfEmpty`, so evidence is never silently dropped.
- **Explicit state.** `OccurrenceState` is `PLANNED` / `COMPLETED` / `SKIPPED`; nothing is inferred
  from the absence of actuals, and recording a set does not flip the state by itself (invariant 3).
- **The set draft lives in `SavedStateHandle`** as JSON, so a half-entered set survives rotation,
  navigation and process death. Prefilled values are only ever a suggestion: nothing reaches the
  record until the user confirms it.
- **No destructive migration.** `MeleteDatabase.build()` has no `fallbackToDestructiveMigration`;
  it registers `MIGRATIONS` explicitly. Schemas are exported to `app/schemas/` and the 1→2
  migration is covered by a test (invariant 10).
- **Sample data is flagged and removable.** Every seeded row carries `isSampleData = true` and the
  name prefix `Sample · `, the UI shows a SAMPLE chip, the menu action exists only in debug builds,
  and nothing is inserted on launch.
- **Manual DI.** `AppContainer` on the `Application`; a DI framework would add more machinery than
  it removes for a single-user app.
- **Text chevrons instead of Material icons.** `material-icons-core` is frozen at 1.7.8 and is not
  part of the current Compose BOM, so navigation uses `‹` / `›` / `⋮` / `+` glyphs with
  `contentDescription` semantics. Revisit if an icon dependency is added later.

## Commands that work

`JAVA_HOME` must point at a JDK; Android Studio's bundled one works:
`C:\Program Files\Android\Android Studio\jbr`.

| Purpose | Command |
| --- | --- |
| Build the debug APK | `./gradlew :app:assembleDebug` |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests, keeping app data | `scripts/device-tests.sh` |
| Instrumented tests via Gradle (WIPES app data, see below) | `./gradlew :app:connectedDebugAndroidTest` |
| Install on a connected device | `./gradlew :app:installDebug` |
| Install a built APK by hand | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Launch | `adb shell am start -n com.yokodake.melete/.MainActivity` |

APK: `app/build/outputs/apk/debug/app-debug.apk`.

**`./gradlew connectedDebugAndroidTest` uninstalls the app when it finishes**, and uninstalling
deletes `/data/user/0/com.yokodake.melete` — the whole training record on that device. Use
`scripts/device-tests.sh` instead: it installs both APKs with `install -r`, which keeps app data,
and drives `am instrument` directly. Reach for the Gradle task only on a device whose data does not
matter.

## Checks actually run

Phase 1:

- `./gradlew :app:assembleDebug` — success.
- `./gradlew :app:testDebugUnitTest` — 17 tests passing (week arithmetic and labels including the
  new-year boundary, prescription JSON round-trip, absence-stays-absence, added load vs
  assistance, unknown-field tolerance, week grouping and ordering, per-side labelling).
- 4 Room persistence tests passing on a physical Pixel 9 (Android 17).
- On-device manual check: sample data appears on the right days, the unscheduled item appears in
  the unscheduled section, week navigation and the *Today* action work, today is marked.

Phase 2:

- `./gradlew :app:testDebugUnitTest` — 23 tests passing (phase 1 tests plus set-draft conversion:
  an empty field stays absent, no unit means no measurement is recorded, the load meaning travels
  with the set, an empty draft cannot be confirmed, draft and payload round-trips).
- 20 instrumented tests passing on the Pixel 9,
  including: a library edit leaving a scheduled copy and its actuals untouched; editing this
  week's plan leaving the library and the actuals untouched; six sets logged against a plan of
  four; an unplanned set still belonging to an occurrence and a session; left and right recorded
  separately with different loads; deleting a set not moving a later correction onto another
  record; one session per day across several exercises; an undated item filed under the chosen
  date; previous results coming only from other occurrences; refusing to delete an occurrence that
  has actuals; completion state independent of recorded sets; and
  `migrate1To2KeepsExistingRowsAndAddsTheNewTables`.
- Full on-device walkthrough on the Pixel 9: created an exercise from an empty library, scheduled
  it onto today, and confirmed three sets with one tap each. The database pulled off the device
  after a force-stop showed `user_version = 2`, three `actual_sets` rows with explicit
  `LEFT` / `RIGHT` sides and `rpe` / `rir` stored as `null` rather than `0`, one `training_sessions`
  row for the training date, and two `prescriptions` rows (the library default and the scheduled
  copy).
- `MIGRATION_1_2` has **not** been exercised against a real phase 1 database on a device. The
  Gradle device-test task had uninstalled the app before the phase 2 build was installed, so the
  phone started from an empty schema 2 database. The migration is covered by `MigrationTest`, which
  builds a schema 1 database, fills it and validates the upgrade. Before shipping a schema 3, run
  the upgrade once over a populated database using `scripts/device-tests.sh` so the install is not
  wiped first.

## Known limitations

- No timer, modules, duration capture, dashboard or export yet; those tables and screens are
  deliberately not created speculatively.
- An occurrence that already has a date cannot be moved to another day, and a recorded set cannot
  be re-dated. Both need phase 4's explicit distinction between rescheduling remaining work and
  correcting a historical date.
- Target durations and rest are entered in seconds. Acceptable for a library form; worth revisiting
  if it grates in use.
- Superseded prescription rows are kept forever and never garbage collected. Harmless at this scale
  and safer than deleting a row an actual set may still reference.
- Sample data is inserted into the week currently on screen; inserting twice creates duplicates by
  design (removal clears all sample rows at once).
- Ordering within a day is `orderIndex`, which nothing edits yet; reordering arrives in phase 4.
- `today` is computed when the UI state is built, so an app left open across midnight keeps the old
  highlight until the state is rebuilt.
- The month abbreviation in week labels comes from the device locale (JDK/CLDR data), e.g. `Sep`
  in `en-US` and `Sept` in `en-GB`. Unit tests pin `Locale.US`.

## Next step

**Phase 3 — the timer.** Start from the phase 3 prompt in `training-app-coding-prompts.md`
together with `docs/project-brief.md`. It needs a single countdown for work or rest started from an
exercise, monotonic deadlines with explicit running/paused/finished state, a persistent
notification through a user-started foreground service, an advance warning that is not duplicated
across pause, resume or recreation, and stale active timing marked interrupted after a reboot
rather than resumed from an invalid deadline. Check the current official Android documentation for
foreground-service types and background execution limits before choosing the approach, and record
the choice here. Timer completion must never create a performed set.
