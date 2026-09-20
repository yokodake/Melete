# Build status

Last updated: 2026-09-20, end of phase 1.

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

Not in phase 1, by design: exercise designer, library picker, set logger, timer, modules,
dashboard, export. Occurrence cards are not tappable yet.

## Architecture as built

```
com.yokodake.melete
  MeleteApplication.kt      Application + AppContainer (manual DI, no framework)
  MainActivity.kt           single activity, Compose, edge-to-edge
  core/WeekMath.kt          Monday-based ISO week arithmetic and labels (pure, unit-tested)
  data/
    MeleteDatabase.kt       Room database v1, exportSchema = true, no destructive fallback
    MeleteConverters.kt     enum <-> String converters (stored names are part of the format)
    TrainingRepository.kt   Flow<List<PlannedOccurrence>> per week + sample-data actions
    DevSampleData.kt        explicitly marked development data
    dao/TrainingDao.kt      week query, sample-data counts and deletes
    entity/Entities.kt      ExerciseEntity, PrescriptionEntity, ExerciseOccurrenceEntity
    model/PrescriptionPayload.kt  versioned named-field JSON payload
  ui/week/                  WeekScreen, WeekViewModel, WeekUiState, PrescriptionSummary
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
  one quantity (invariant 6). `meaning` is present from v1 deliberately, to avoid a later migration
  that would have to guess what historical numbers meant.
- **Prescriptions are value rows, shared by pointer, copied on scheduling.** An exercise points at
  its default prescription (`exercises.defaultPrescriptionId`) and an occurrence points at its own
  copy (`exercise_occurrences.prescriptionId`). Seeding already writes two separate rows, so
  editing a library default cannot reach work already placed in a week (invariant 1).
- **Occurrences snapshot their template.** Name, mode, unilateral flag and measurement unit are
  copied onto the occurrence. `exerciseId` is lineage only and deliberately carries **no** foreign
  key, so a renamed or deleted library entry cannot rewrite or delete history (invariants 1 and 5).
- **Explicit state.** `OccurrenceState` is `PLANNED` / `COMPLETED` / `SKIPPED`; nothing is inferred
  from the absence of actuals (invariant 3).
- **No destructive migration.** `MeleteDatabase.build()` has no `fallbackToDestructiveMigration`.
  Schemas are exported to `app/schemas/` for future migration tests (invariant 10).
- **Sample data is flagged and removable.** Every seeded row carries `isSampleData = true` and the
  name prefix `Sample · `, the UI shows a SAMPLE chip, the menu action exists only in debug builds,
  and nothing is inserted on launch.
- **Manual DI.** `AppContainer` on the `Application`; a DI framework would add more machinery than
  it removes for a single-user app.
- **Text chevrons instead of Material icons.** `material-icons-core` is frozen at 1.7.8 and is not
  part of the current Compose BOM, so the week navigation uses `‹` / `›` / `⋮` glyphs with
  `contentDescription` semantics. Revisit if an icon dependency is added later.

## Commands that work

`JAVA_HOME` must point at a JDK; Android Studio's bundled one works:
`C:\Program Files\Android\Android Studio\jbr`.

| Purpose | Command |
| --- | --- |
| Build the debug APK | `./gradlew :app:assembleDebug` |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests (device/emulator required) | `./gradlew :app:connectedDebugAndroidTest` |
| Install on a connected device | `./gradlew :app:installDebug` |
| Install a built APK by hand | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Launch | `adb shell am start -n com.yokodake.melete/.MainActivity` |

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Checks actually run (phase 1)

- `./gradlew :app:assembleDebug` — success.
- `./gradlew :app:testDebugUnitTest` — 17 tests, all passing (week arithmetic and labels including
  the new-year boundary, prescription JSON round-trip, absence-stays-absence, added load vs
  assistance, unknown-field tolerance, week grouping and ordering, per-side labelling).
- `./gradlew :app:connectedDebugAndroidTest` — 4 Room persistence tests passing on a physical
  Pixel 9 (Android 17): the seeded week survives closing and reopening the database, prescription
  snapshots are readable after reopening, neighbouring weeks stay empty, and clearing sample data
  removes every marked row.
- On-device manual check on the same Pixel 9: sample data inserted from the debug menu appears on
  the right days, the unscheduled item appears in the unscheduled section, previous/next week
  navigation and the *Today* action work, and today is marked in the current week.

## Known limitations

- Occurrence cards are inert — tapping does nothing until the logger exists (phase 2).
- No exercise creation, library picker, modules, timer, sessions, actuals, durations, dashboard or
  export yet; those tables and screens are deliberately not created speculatively.
- No session container table yet. It arrives in phase 2 with the first actual sets, because actuals
  are what need a container.
- Snapshot isolation is implemented (separate prescription rows, snapshotted names) but not yet
  covered by a test that *edits* a template, because phase 1 has no editing path. Add that test in
  phase 2 as soon as editing exists.
- Sample data is inserted into the week currently on screen; inserting twice creates duplicates by
  design (removal clears all sample rows at once).
- Ordering within a day is `orderIndex`, which nothing edits yet; reordering arrives in phase 4.
- `today` is computed when the UI state is built, so an app left open across midnight keeps the old
  highlight until the state is rebuilt.
- The month abbreviation in week labels comes from the device locale (JDK/CLDR data), e.g. `Sep`
  in `en-US` and `Sept` in `en-GB`. Unit tests pin `Locale.US`.

## Next step

**Phase 2 — create an exercise and log it with minimal friction.** Start from the phase 2 prompt in
`training-app-coding-prompts.md` together with `docs/project-brief.md`. First additions will be the
exercise creation/editing form, the library picker that copies a prescription into a week, the
session container, actual-set records with side and optional RPE/RIR, and an occurrence-level
comment. Keep phase 1 behaviour intact.
