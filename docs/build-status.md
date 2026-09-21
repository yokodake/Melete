# Build status

Last updated: 2026-09-21, after the sixth round of use feedback.

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

### Phase 3 — the timer ✅

- One countdown, for work or for rest, started from the timer tab or from the exercise being
  logged. Adjustable before it starts, then pause, resume and cancel.
- Three families of cue, each switchable on its own, each with its own sound: a heads-up at
  **thirty seconds left**, a **3-2-1** tick through the last three seconds, and **quarter, half
  and three-quarter** marks through a work interval of a minute or more. The screen says how many
  cues the countdown will actually sound, and why a setting does not apply when it cannot.
- A persistent notification carries the remaining time as a countdown chronometer, with pause,
  resume and cancel buttons.
- The logger shows the remaining time inline and offers *Rest* right after a set is confirmed.
- The screen is held awake while a countdown is running and the app is on screen, so the phone
  does not lock mid-rest.
- Reaching zero never records a set. The finished notification says so in as many words.

### Bottom navigation and the effort scale

- Three tabs — week, library, timer — hidden inside the picker, exercise editor and logger.
- Effort is a five-point verbal scale stored as its integer level; reps in reserve stays a
  planning field and is not asked for when logging.

### First round of use feedback ✅

Changes made after the app was used, from `src/feedback.md`. Schema 3.

- **A workout opens as information, not a form.** Tapping an exercise — in the week or in the
  library — lands on `ui/detail/ExerciseDetailScreen`: what it is, how to do it, what is planned,
  and the facts that change how a set is performed. From a planned copy the bottom bar offers the
  only two things worth doing next, *Log the workout* and *Start the timer*. Editing the definition
  and its default prescription is a button on that screen, never the screen you land on.
- **Exercises carry an explanation and a category.** `description` is reference material and is
  read live from the library rather than snapshotted, so correcting how a movement is done corrects
  it everywhere; `category` *is* snapshotted alongside mode and unilateral, so re-categorising a
  library entry cannot recolour history. Three categories for now — open (orange), conditioning
  (green), flexibility (yellow) — shown as a small coloured dot before the name.
- **The default prescription no longer prescribes load.** A plan fixes the shape of the work: sets,
  reps or duration, rest, target effort or RIR. The weight is what the day decides and what the
  logger records. Older rows keep whatever load they were written with — prescription rows are
  never mutated — but re-saving one drops it.
- **Removing a planned workout costs intent.** The text button in the logger is gone. It is now a
  long press on the week screen and a menu item, and it is still refused outright once sets have
  been recorded against the occurrence.
- **Done reads green.** A saturated green chip from `ui/theme/SemanticColors.kt` rather than a
  scheme colour, so it means the same thing on every phone's wallpaper.
- **The timer, first pass.** Work/rest no longer discards the duration you just typed; the duration
  is typed as minutes and seconds instead of nudged in steps of 15 or 60; the cue families moved
  into an overflow menu in the top right and can be switched **while a countdown is running**; the
  whole screen goes green through a work interval and amber for the last five seconds of a rest; a
  countdown carries the exercise it was started from; and starting one from a workout while another
  is still counting asks before calling the first one off. More timer work is expected.

### Second round: the timer counts sets ✅

- **The timer was the wrong shape.** It could count one work interval or one rest, so real
  training — *five sets of ten seconds with three minutes between* — had to be driven by hand
  between every set. `TimerProgram` now holds all three numbers, and `TimerTransitions` walks it:
  work, rest, work, rest, with **no trailing rest after the last set**, because that is time the
  app would have invented.
- **Reps are not timed.** A set of eights has no honest length, so a reps program stops at
  `TimerState.AwaitingSet` and waits; the athlete says the set is done, and the rest starts by
  itself. That is the one manual step in an otherwise automatic sequence, and it is on the
  notification as well as the screen.
- **One interval is one run id.** The at-most-once cue bookkeeping is keyed by run id, so a
  program that kept a single id would sound the first set's end and then stay silent for every
  set after it. Each interval gets a fresh id and the finished one's bookkeeping is cleared.
- The create screen now asks what a set is made of, how long the rest is and how many times round,
  and shows the whole program in one line before you start it.
- Starting from a workout builds the program from the prescription, which already knows all three
  numbers. Nothing further is asked.
- The plan is edited by a cog on the card that shows it, rather than a button lower down that has
  to re-explain which plan it means; from the week that edits this copy, from the library the
  default. Changing what the exercise *is* moved into the top-bar overflow.
- The library list no longer prints the explanation under every row: the list is for finding an
  exercise, and a paragraph under each one turns scanning into reading.

### Fourth round:

- 81 unit tests still passing, unchanged: none of them knew about alarms, because the sequencing
  they cover was always pure.
- Four instrumented tests deleted (they existed only to arbitrate between the two delivery paths),
  one rewritten without the fake alarm scheduler, and one added in their place: a three-set program
  sounding the end of every preparation and every set, which is the property the run ids were
  protecting. **Still not run on a device.**

Phase 4A — written without a device, then the instrumented suite was run on the Pixel 9 by the
user:

- 93 unit tests passing, 9 of them a new `PlanningTest` covering the two decisions that are
  arithmetic rather than SQL: where an item lands when nudged (clamped at both ends, unchanged for
  an item not in the list) and what to say when a planned and a performed date differ.
- `assembleDebug`, `assembleDebugAndroidTest` and the schema-4 export all clean.
- **34 instrumented tests, all passing**, which is the whole suite. That includes
  `migrate3To4RetiresNothingAndKeepsEverything` — a schema-3 database carrying an exercise upgrades
  to 4 with its description and category intact and nothing retired — and the check that the
  production builder carries all three migrations. The schema-4 upgrade is verified on hardware.
- Still **unverified end to end**: the acceptance scenarios in the phase prompt — scheduling three
  weeks out, moving between weeks, surviving a library deletion, the planned/performed mismatch.
  The instrumented suite exercises the database, not the screens, so these are being checked by
  hand.

Fifth round — all on the Pixel 9:

- 33 instrumented tests passing, including the four rounds of timer work that had never run on a
  device. One caught a real bug (below).
- The three cue-delivery tests pass under genuine `HARDENING_THROW` with no violations. They now
  assert on the platform's register of active players rather than on the audio-focus result, since
  focus is no longer requested — and being granted focus never proved a sound came out anyway.
- A 3 × 15 s program with 31 s rest was run with the phone **locked**, over Spotify. The user heard
  every cue and the music kept playing.

**Bug found by the device suite:** `start(program, settings)` planned the first interval from the
settings passed in, while every later interval read `store.cueSettings` — so a program started with
explicit settings quietly reverted after one set. The store is now the single source.

Third round: preparation, and transport controls ✅

- **Five seconds before a set.** `TimerPhase.PREPARE` is a real countdown, not a flag, so its
  3-2-1-go falls out of the ordinary cue planner and the screen has something true to display.
  It runs before the first set, and before any set you arrive at by pressing next or previous.
  It does **not** run when a rest flows into a set on its own — the tail of the rest is already
  the getting-ready, which is what the amber is for — unless that rest is shorter than the
  preparation itself.
- **Resuming a nearly-finished rest becomes a preparation.** Unpausing with two seconds of rest
  left would drop you straight into the set. Below five seconds it hands back a fresh five-second
  preparation instead of stretching the rest, because the old rest has already sounded some of its
  cues and stretching it would re-owe them.
- **Transport controls.** Previous / play-pause / next, because a program is a sequence and that is
  what a sequence's controls look like. Cancel sits apart and below: it ends the whole thing and
  has no business being a mis-tap from the button pressed between every set. A reps set shows no
  play-pause, since nothing is counting.
- `TimerProgram.steps` flattens a program into its intervals, so sequencing, skipping forward and
  going back are all one operation on an index.

### Fourth round: the alarm backstop is gone ✅

The user was explicit about scope: the timer has to survive the phone being **locked** and other
apps being in front, not being **killed** from the recents drawer. The foreground service provides
the first. Exact alarms existed only for the second.

Removing them removed everything they forced:

- `TimerAlarms.kt` (the scheduler, the receiver) and the `USE_EXACT_ALARM` permission.
- `TimerStore.markCueDelivered` / `clearCues` and the `cue:` keys — the durable at-most-once
  bookkeeping, which existed because two independent paths could sound the same beep. With one
  path it is just `TimerState.Running.delivered`, which empties on its own at each interval.
- `TimerController.onAlarm`, the cue mutex, and the "is this cue owed" negotiation.
- The countdown loop's generation counter. The loop now runs across a whole program instead of
  cancelling and relaunching itself at every interval, so there is nothing to guard against.

About 170 lines net out of the main sources, and the timer's hardest invariant — "exactly one of
two racing deliverers wins, durably, across process death" — stopped existing rather than being
maintained.

What is kept: the *deadline* is still persisted. A killed process makes no sound, but reopening the
app recomputes from the monotonic clock and either shows the true remaining time or says the
countdown ended while the app was not running, rather than pretending it alerted.

### Fifth round: the cue stops interrupting the music ✅

The cue was stopping Spotify dead for every tick. Two separate causes, and fixing only the first
did nothing audible:

1. **Audio focus.** It was requested as transient-may-duck, which is the polite form, but asking at
   all hands the music app the decision of whether to duck or stop. Nothing is requested now.
2. **`USAGE_ALARM`.** The platform fades media to zero *on its own* when an alarm-usage player
   starts — no focus involved — which is heard as the music stopping. Alarm usage means "interrupt
   the user", which a timer cue during a set is not.

The cue now plays as `USAGE_MEDIA` and mixes into the music at the volume already set for it.
Confirmed by ear on the Pixel 9: Spotify keeps playing, the beep is audible over it, **including
with the phone locked**. `AS.FadeOutManager` no longer appears in logcat while a cue sounds.

There is no ducking. Ducking is the other app's decision and cannot be forced (`setForceDucking`
is honoured only for accessibility services), so "mixes over" is the achievable version of "does
not interrupt".

### Phase 4A — library and planning organisation ✅

Schema 4. One nullable column: `exercises.deletedAtEpochMs`.

**Deletion is as complete as the history allows.** Removing an exercise counts what refers to it
first, and says which of three things it is about to do:

| What exists | What happens |
| --- | --- |
| Nothing | The row is deleted, with its default prescription. |
| Planned copies, never logged | The row and those copies are deleted. A plan never carried out is a mistake too. |
| At least one logged set | The row is kept as a tombstone and leaves the library. |

The rule is the user's: *keep the fact that I did it, never the fact that I planned it.* A single
logged set is the whole difference, because the row anchors that set's lineage and the stable
identity that makes "previous results" group. `LibraryDao.observeExercises` filters on
`deletedAtEpochMs IS NULL`; everything else reads the row regardless.

`exercise_occurrences.exerciseId` and `actual_sets.exerciseId` are lineage references rather than
foreign keys, so nothing in the schema stops a delete — the count is what decides. It is re-taken
inside the transaction rather than trusted from the dialog, so a set logged between asking and
confirming still protects itself.

**One date, and the log follows it.** 4A first shipped a planned date and a performed date that
were allowed to disagree, with the planner reporting the difference as `Planned Mon · Logged Tue`.
That was cut on the user's call: *keep the fact that I did it, never the fact that I planned it.*

A placement now carries one date and its sets are filed under that same date. Moving a placement
re-dates its sets with it and re-homes them into the new day's session, so the two cannot drift
apart. A card that has been trained says when the work happened; a card that has not says when you
intend it to.

- **Unlogged cards never move by themselves**, which is what makes the planner still worth reading
  backwards: what is left sitting on a past day is exactly what you did not do.
- **Trained work cannot be made unscheduled.** It happened on a day, and "anytime this week" would
  make the record vaguer than the truth. The dialog omits the option and `moveOccurrence` returns
  false if asked anyway.
- Logging an unscheduled card already planted it on the day it was done (`assignOccurrenceDate`);
  this is the same rule applied to scheduled ones.

Deleted with it: `PlannedOccurrence.performedDate`, `observePerformedDatesInWeek`,
`PerformedDateRow`, `Planning.dateMismatch`, the `DateMismatch` label and the *Change the day it was
done…* menu item. `observeWeek` is a plain `map` again rather than a `combine` reconciling two
sources.

**Organising.** Move to any week (day or unscheduled), copy, nudge up and down within a slot, edit
the local prescription, and remove. Scheduling from the library reaches any week through the same
week/day dialog. Copying duplicates the prescription into its own row, so the copies diverge — and
a copy is a fresh plan, so it never inherits what was logged against the original.

**Removing something that has been trained.** A placement with no log removes with an ordinary
confirmation. One with a log gets a dialog naming how many sets it would destroy, and the only
button that does it says so. History never leaves through an ambiguous yes.

**Interaction.** Long press opens the contextual menu, as established for the week screen in the
first feedback round. Every long press also has a visible `⋮` beside it, because an invisible
gesture must not be the only route to an action.

Not implemented yet, by design: modules, duration capture, dashboard, export.

### Sixth round: logging becomes one act ✅

Used on the phone, and most of it was wrong. Five changes, all of them the user's calls.

**The tick was the write.** Ticking a row recorded a set and unticking deleted one, so the table
was a live database view wearing a form's clothes and *Mark done* had nothing left to do. The
table is a draft now: ticking, unticking and typing cost nothing, and **marking done writes the
whole workout in one transaction, sets the occurrence completed and closes the screen**. Pressing
it again on a logged workout *replaces* what was written rather than adding to it
(`replaceSetsForOccurrence`), so correcting a log is the same gesture as making one — the button
says *Save changes* to admit it. `SetRow.done` is a plain `Boolean` instead of a list of database
ids, which is the whole change in one line.

- Planned rows start ticked: the ordinary case is that you did what you planned, and unticking is
  how you say otherwise.
- A recorded load stays editable. The tick says the set happened, the load says what it weighed,
  and correcting the second is not a statement about the first.
- Nothing is loggable until every ticked set says what it weighed **or** a max load stands for all
  of them. The button stays enabled and says which is missing rather than going dead and silent.
- The max load is a fallback, never an override: a row that says something of its own keeps it.
  It is also *deduced* from the rows — the heaviest set is the max load — so it cannot sit empty
  above a table that plainly answers it.
- Unilateral rows fall back per side, so "60 both" and "60 left, 50 right" both work.

**One date per placement.** 4A shipped a planned date and a performed date allowed to disagree,
reported as `Planned Mon · Logged Tue`. Cut, on the rule *keep the fact that I did it, never the
fact that I planned it*. Moving a placement re-dates its sets with it; unlogged cards never move by
themselves, which is what keeps the planner worth reading backwards. Trained work cannot be made
unscheduled, because it happened on a day.

**Deleting is as complete as the history allows.** Nothing refers to it → the row and its default
prescription go. Planned but never trained → those planned copies go too, because a plan never
carried out is a mistake as well. One logged set → tombstone, and it leaves the library only. The
dialog says which of the three it is about to do; the count is re-taken inside the transaction, so
a set logged between asking and confirming still protects itself.

**Planning got smaller.** *Copy to…* became **Duplicate** — same week, unscheduled, at the top,
no dialog. Adding from the library asks for **a week and nothing finer** (last week included, so a
session trained but never written down can still go where it happened); the day is a later
question, answered in the planner.

**Density.** Material's `OutlinedTextField` enforces a 56dp minimum height and 16dp of padding on
four sides — none of it reachable through parameters, and absurd for a grid of two-digit numbers.
`ui/components/CompactField.kt` rebuilds the field from `BasicTextField` and Material's own
decoration box, same container and colours, at 10dp × 6dp. Every text field in the app goes
through it. The tick stopped being an `IconButton` (48dp enforced touch target, which was setting
the height of every row) and the rows moved into one list item so their spacing is 4dp rather than
the page's 8dp. A set row went from 68dp to 48dp, and effort and max load each went from a heading
plus a full-width box to a single line: about 175dp, with no font made smaller.

Also: the countdown left the logger entirely (the exercise screen owns that button), the note is
written in place instead of behind a dialog, effort is a dropdown starting from what the plan
asked for, skip moved into the top-bar menu, and a done card in the planner shows the heaviest set
it took, in bold.

### Release builds ✅

`release` is signed with the **debug key**, deliberately. This app is sideloaded onto one phone and
never distributed, and sharing the signature with the debug build is what lets a release install
over it as an *update* instead of demanding an uninstall — which would take the training history
with it. `optimization { enable = false }`, so R8 does not run and cannot strip Room or
kotlinx.serialization reflection; turning minification on is the moment to re-test the database
paths. A release build is not debuggable, so `adb run-as` cannot read the database from it.

## Architecture as built

```
com.yokodake.melete
  MeleteApplication.kt      Application + AppContainer (manual DI, no framework)
  MainActivity.kt           single activity, Compose, edge-to-edge
  core/WeekMath.kt          Monday-based ISO week arithmetic and labels (pure, unit-tested)
  ui/Navigation.kt          type-safe routes + NavHost (week, detail, picker, editor, logger)
  data/
    MeleteDatabase.kt       Room database v3, exportSchema = true, explicit migrations only
    MeleteConverters.kt     enum <-> String converters (stored names are part of the format)
    TrainingRepository.kt   week, library, scheduling and logging operations
    DevSampleData.kt        explicitly marked development data
    dao/TrainingDao.kt      TrainingDao, LibraryDao, LoggingDao
    entity/Entities.kt      ExerciseEntity, PrescriptionEntity, ExerciseOccurrenceEntity,
                            TrainingSessionEntity, ActualSetEntity
    model/PrescriptionPayload.kt  versioned named-field prescription payload
    model/ActualSetPayload.kt     versioned named-field actual-set payload
    model/ExerciseCategory.kt     the closed set of training-purpose categories
  ui/components/            PrescriptionFormState, the shared prescription fields, CategoryDot
                            CompactField.kt   every text field in the app, sized for its content
                            Chip.kt           SAMPLE / Done / Skipped, shared by week and library
                            PlanTargetDialog  week+day for a move, week-only for library adds
  ui/theme/SemanticColors.kt  colours that carry a meaning, kept out of the dynamic scheme
  ui/week/                  WeekScreen, WeekViewModel, WeekUiState, PrescriptionSummary
  ui/detail/                ExerciseDetailScreen + view model: what a workout is, and what to
                            do next with it
  ui/library/               LibraryPicker, ExerciseEditor (+ view models)
  ui/logger/                LoggerScreen, LoggerViewModel, SetDraft
  ui/timer/                 TimerScreen, TimerViewModel
  data/timer/
    TimerProgram.kt         sets, work, rest: what the timer is actually counting
    TimerState.kt           deadline-based state, and the pure pause/resume/sequencing logic
    TimerSnapshot.kt        what is persisted, and how a run is restored after a reboot
    TimerStore.kt           SharedPreferences: snapshot, cue bookkeeping, settings
    TimerController.kt      the single owner: start, pause, resume, cancel, cue delivery
    TimerService.kt         foreground service (specialUse) and the notification actions
    TimerAlarms.kt          one-shot exact alarms as the backstop, and their receiver
    TimerCuePlayer.kt       generated USAGE_ALARM tone and vibration
    TimerNotifications.kt   channels, the ongoing countdown, the finished alert
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

## How the timer works, and why

Checked against the current Android documentation on 2026-09-20; the version-sensitive parts are
foreground service types, background audio and exact alarms.

- **A countdown is a deadline on `SystemClock.elapsedRealtime()`**, never a decrementing counter.
  A counter drifts, stops when the process is frozen and cannot be rebuilt after the UI is
  recreated. Elapsed-realtime keeps running while the device sleeps and is immune to the wall
  clock being changed. Rotation, navigation and process death therefore cost nothing: the state is
  re-derived from the clock.
- **Reboots are detected with `Settings.Global.BOOT_COUNT`**, stored beside the deadline. After a
  restart the old deadline belongs to a clock that no longer exists, so a running countdown is
  reported as *interrupted* rather than resumed from a fabricated number. A paused countdown does
  survive a reboot, because its remaining time is a duration rather than a point in time.
- **Foreground service type `specialUse`.** A training countdown matches none of the defined
  categories. `shortService` is the obvious candidate and is wrong twice over: it is capped at
  three minutes, which is shorter than an ordinary hangboard rest, and Android 17 excludes it from
  background audio outright. `specialUse` has no runtime timeout, and the Play Console declaration
  it normally requires does not apply to an app that is never published. The manifest carries the
  required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation.
- **The service is started from a tap while the app is visible**, which is what grants it
  while-in-use capability. On Android 17 an app in the background may only touch audio through a
  while-in-use-capable foreground service — with one waiver, for `USAGE_ALARM` streams from an app
  holding exact-alarm permission. The cues use exactly those attributes and the app holds
  `USE_EXACT_ALARM`, so the waiver covers the case that matters most: a cue owed after the process
  has been killed.
- **`USE_EXACT_ALARM` rather than `SCHEDULE_EXACT_ALARM`.** A countdown that warns ten seconds
  before a hang ends is an alarm-clock-like function; the permission is granted at install and
  cannot be revoked out from under a running countdown.
- **The service never calls `stopService` on itself from the controller.** Stopping a service whose
  `startForegroundService` has not yet reached `startForeground` breaks the platform's promise and
  it kills the process — easy to hit with a one-second countdown or a start the user immediately
  cancels. The service watches the state and stands itself down instead, and `startForeground` is
  always the first thing `onStartCommand` does.
- **Keeping the screen on is a window flag, not a wake lock.** `View.keepScreenOn` applies only
  while the app's window is visible, so it cannot leave the display on once the phone is put down,
  and it is tied to a countdown actually running rather than to the timer tab being open — parking
  on that tab should not burn the screen. Pause, cancel or the end releases it at once.
- **A foreground service does not keep the CPU awake.** A partial wake lock does, held only while
  a countdown is actually running and bounded by its remaining time plus ten seconds, so a bug
  cannot leave it held.
- **Two one-shot exact alarms per run, not a repeating alarm.** The countdown itself is driven in
  process; the alarms exist only so a frozen or killed app still sounds at the warning and at the
  end. Both are on the elapsed-realtime clock, the same clock as the deadline.
- **The cue plan is fixed when a run starts**, so changing the settings mid-countdown cannot
  change what the run in progress will do. Cues that would fall outside the countdown are dropped,
  and two cues landing on the same moment collapse into the more specific one — on a two minute
  set, three-quarters done *is* thirty seconds left, and it sounds once.
- **Quarter cues are for work intervals only.** A rest interval needs to know how much is left,
  not where its middle was.
- **Only the thirty-second warning and the end are backed by exact alarms.** The finer cues are
  company while training, not a reason to wake a dead process, and eight exact alarms per rest
  would be a poor trade for a phone's battery.
- **Every cue passes through one delivery gate.** Durable bookkeeping records which cues a run has
  already had, so the in-process path and the alarm path cannot both sound. The state moves on
  whichever path wins, so a cue rung by the other one still ends the countdown rather than
  stranding it.
- **Audio behaviour with other apps playing.** Focus is requested as transient-may-duck, so music
  dips for the beep rather than stopping. The notification channels are deliberately silent: the
  cue is played explicitly, and a channel sound would double every beep.

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
| Build a release APK | `./gradlew :app:assembleRelease` |
| Install the release build (keeps data) | `./gradlew :app:installRelease` |
| Install a built APK by hand | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Launch | `adb shell am start -n com.yokodake.melete/.MainActivity` |
| Make audio violations loud instead of silent | `adb shell cmd audio set-hardening throw` |

APKs: `app/build/outputs/apk/debug/app-debug.apk`,
`app/build/outputs/apk/release/app-release.apk`.

The release build is signed with the debug key, so it installs **over** a debug build as an update
and the training record survives. It is not debuggable, so `adb run-as` — the only way to read the
database off the phone without root — works against debug builds only. Put a debug build back on
first if the data needs inspecting.

**`./gradlew connectedDebugAndroidTest` uninstalls the app when it finishes**, and uninstalling
deletes `/data/user/0/com.yokodake.melete` — the whole training record on that device. Use
`scripts/device-tests.sh` instead: it installs both APKs with `install -r`, which keeps app data,
and drives `am instrument` directly. Reach for the Gradle task only on a device whose data does not
matter.

## Checks actually run

Sixth round (logging as one act, density, release build):

- **104 unit tests passing.** 10 of them a new `SetTableTest` covering the commit rules directly:
  every set carrying its own load is enough; a max load stands for the sets that have none but
  never overrides one that says something of its own; one missing load blocks the table; an
  unticked set is neither required to say what it weighed nor written; nothing ticked means
  nothing to log; an unmeasured exercise needs only a tick; a unilateral row falls back per side.
  5 more in `ExerciseRemovalTest` on the three-way delete decision, including that one logged set
  outranks forty planned copies.
- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` all clean.
- The **release build is installed and running on the Pixel 9**, confirmed non-debuggable
  (`flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE ]`, no `DEBUGGABLE`),
  installed as an update over the debug build with the training record intact.
- The unilateral write path was checked against the real database pulled off the phone: a
  4-set unilateral workout stores 8 rows, `LEFT`/`RIGHT` correctly paired and ordered by
  `orderIndex`.
- **Not verified:** none of the logging changes have been driven end to end on the device by the
  author of them. The screens have been looked at in screenshots; marking done, reopening a logged
  workout and correcting it have not been exercised beyond the unit tests.

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

Phase 3:

- `./gradlew :app:testDebugUnitTest` — 55 tests passing, 28 of them the timer: the cue plan for
  each length and phase, collisions collapsing to the more specific cue, each family switching off
  on its own, and the arithmetic: remaining
  time never negative, pause and resume preserving remaining time without shifting the deadline, a
  warning already given not repeated after resuming, a warning at least as long as the countdown
  dropped rather than fired at the start, a running countdown after a reboot reported interrupted,
  a paused one surviving a reboot, and a run that expired while the process was gone coming back
  finished.
- `scripts/device-tests.sh` — 31 instrumented tests passing on the Pixel 9, 11 of them timer
  delivery: a countdown sounding exactly once, a late backstop alarm for the same run not sounding
  again, warning and end delivered once each and in order, cancelling leaving nothing pending,
  pause and resume not repeating the warning, a run surviving the controller that owned it, and an
  alarm delivering the cue when the countdown itself never got there.
- The cue was played through the real `TimerCuePlayer` with
  `adb shell cmd audio set-hardening throw` in force: focus was granted and no
  `AudioHardening` entries appeared in logcat, so the `USAGE_ALARM` attributes are accepted rather
  than silently dropped.
- A crash found by these tests and fixed: starting the foreground service and then stopping it
  before it reached `startForeground` killed the process with
  `ForegroundServiceDidNotStartInTimeException`.
- A sixty-three second work interval was run on the Pixel 9 **with the screen off**. The platform
  logged audio going active eight times, spaced 15.8s, 1.5s, 14.2s, 12.7s, 1.0s, 1.0s, 1.1s — an
  exact match for quarter, half, thirty-seconds-left, three-quarters, three, two, one and the end,
  with 47.4s measured from the quarter mark to the end against 47.25s planned. The user confirmed
  hearing the cues.

First round of use feedback:

- `./gradlew :app:testDebugUnitTest --rerun-tasks` — 59 tests passing, including four new timer
  ones: a cue family switched on mid-run still sounds a moment that is ahead, one whose moment has
  already gone by is written off rather than fired late, cues switched off while paused stay off
  across the resume, and a countdown's exercise label surviving pause, resume, finish and the
  process being killed.
- `./gradlew :app:testDebugUnitTest --rerun-tasks` — 33 instrumented tests passed on the Pixel 9,
  including `migrate2To3…`.

Second round:

- 71 unit tests passing, 12 of them a new `TimerProgramTest` that walks whole programs the way the
  controller walks them: five timed sets alternating with four rests and stopping, a reps program
  waiting at every set, a bare rest, sets with no rest running straight into each other, five
  distinct run ids across five intervals, and a program resuming on the right set after the
  process dies.
- Two real bugs were found by those tests before the code ran on a phone: the no-argument
  `TimerProgram` was invalid and threw from every state that defaulted it, and a program with no
  rest configured stopped after its first set instead of running them back to back.
- The Android command is `cmd audio set-hardening throw`, cleared with `cmd audio
  clear-hardening`. An earlier note here said `set-enable-hardening`, which is not a command: it
  fails silently, so a run "under hardening" that used it proved nothing.

Third round:

- 81 unit tests passing, 22 of them `TimerProgramTest`. The new ones pin every preparation rule:
  only the first set of a rested program is led into, a program with no rest gives every set its
  five seconds, a rest shorter than the preparation is still followed by one, a bare rest and a
  set of reps never get one, skipping in either direction always does, and resuming mid-set
  carries on untouched while resuming a nearly-over rest turns into a preparation under a new id.
- Three instrumented tests were adjusted and two added, for the preparation that now leads a work
  start. **Still not run on a device.**

## Known limitations

- **Removing a placement while keeping its log is not offered.** `actual_sets.occurrenceId` is a
  RESTRICT foreign key, so the occurrence is what the evidence hangs from and cannot be deleted
  out from under it. The choice presented is therefore "keep it" or "delete it and the sets".
  Accepted deliberately: the user's rule is to keep what was done, and a trained card *is* that
  record, so there is nothing to detach it from.
- Reordering is move-up / move-down from the menu. Drag-and-drop was explicitly optional, and
  gating reordering behind an explicit "update week" mode — with move-up at the top of a day
  carrying into the previous day — is asked for and not yet built.
- **The logger's draft is lost if you leave without marking done.** Ticks, loads and the note are
  all held in memory until the one write. Consistent, and the same button accounts for everything
  the screen has to say, but there is no autosave.
- Logged loads no longer prefill from the previous session. They start empty so that the
  "every set filled, or a max load" rule means something; previous results stay visible above as
  the reference.
- An exercise that has been kept rather than deleted cannot be un-kept from the UI;
  `restoreExercise` exists in the repository with nothing calling it yet.

- No modules, duration capture, dashboard or export yet; those tables and screens are deliberately
  not created speculatively.
- Surviving the app being **killed** is explicitly not a requirement, and as of the fourth round
  nothing tries to: the countdown makes no sound while the process is dead, and reopening the app
  recovers the true remaining time from the persisted deadline.
- **Still owed:** behaviour while another app is playing audio (the user is checking this against
  Spotify); forced Doze; and end-to-end confirmation that the screen stays awake for a whole
  countdown. On the last one, the window flag was confirmed engaged —
  `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager/displayId:0'` attributed to the app appears in
  `dumpsys power` while a countdown runs — but the observation that the screen outlived the
  thirty-second timeout was confounded, because the countdown under test may have finished first.
  The flag is doing what it should; that it holds for a full rest is unconfirmed. A first forced-Doze attempt was inconclusive because the countdown
  under test never started, so nothing can be claimed about it yet. Surviving the app being
  *killed* is explicitly not a requirement: the foreground service is what keeps the countdown
  alive in the background, and that is what was verified.
- Only one program runs at a time. Starting another from a workout asks before calling the first
  one off.
- A program's set count comes from the prescription, and a prescription of four sets means four
  timer sets. Nothing reconciles that against what was actually logged; the timer still records
  nothing, by design.
- Starting a one-off *work* timer from the logger now also leads with the five-second preparation.
  That follows from "always five seconds before a set" but is a behaviour change to a path that
  was not about programs.
- Next moves one interval, not one set: from a set to its rest, from a rest to the next set.
  Previous restarts the interval on screen, unless it is pressed within
  `TimerTransitions.RESTART_WINDOW_MS` (one second) of that interval starting, in which case it
  goes to the one before. A set of reps has no elapsed time to measure, so it has no window and
  previous always steps back.
- A reps program waiting on `AwaitingSet` waits forever. There is no timeout, because there is no
  honest length for a set of repetitions — but it does mean a forgotten timer sits in the
  notification shade until it is cancelled.
- An occurrence that already has a date cannot be moved to another day, and a recorded set cannot
  be re-dated. Both need phase 4's explicit distinction between rescheduling remaining work and
  correcting a historical date.
- Target durations and rest are entered in seconds in the library form. The *timer* is typed as
  minutes and seconds; the prescription form has not caught up.
- Switching a cue family on part-way through a countdown writes off the moments that have already
  gone by rather than firing them late. Switching one on in the last seconds of a rest therefore
  does nothing, which is the intended answer rather than a missed cue.
- A countdown's label is a snapshotted name, deliberately not a link to the occurrence: a timer
  reaching zero must never be able to touch what was logged. Renaming an exercise mid-rest leaves
  the old name on the running countdown.
- The library detail screen has no *Start the timer* button; only a planned copy in a week does.
  A free-standing countdown is started from the timer tab.
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

**Phase 4B — modules as reusable scheduling units.** Deferred while the logger was made usable;
the user has been training against the app in the meantime and the feedback has been worth more
than the next phase.

Owed before or alongside it:

- **Gating the planner behind an edit mode**, so cards only move when you have said you are
  reorganising, with move-up/down crossing day boundaries. Explicitly asked for, explicitly
  deferred.
- The 4A acceptance scenarios end to end. The instrumented suite (34 tests) passes on the Pixel 9
  and covers the schema-4 migration, but it exercises the database, not the screens.

## Superseded next step

**More timer work**, which the user has said they will come back to: the work/rest sequence of
phase 7C is the obvious next piece, and the create-timer screen should be used in a real session
before it is changed again.

Then **phase 4**, for reordering within a day and for the explicit distinction between rescheduling
remaining work and correcting a historical training date.

### Superseded — kept for the record

**Phase 3 — the timer.** Start from the phase 3 prompt in `training-app-coding-prompts.md`
together with `docs/project-brief.md`. It needs a single countdown for work or rest started from an
exercise, monotonic deadlines with explicit running/paused/finished state, a persistent
notification through a user-started foreground service, an advance warning that is not duplicated
across pause, resume or recreation, and stale active timing marked interrupted after a reboot
rather than resumed from an invalid deadline. Check the current official Android documentation for
foreground-service types and background execution limits before choosing the approach, and record
the choice here. Timer completion must never create a performed set.
