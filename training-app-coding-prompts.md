# Personal training app — coding prompts

## How to use this file

These prompts are tool-independent: use them with a coding agent that can work in your Android repository. They do not assume access to the conversation that produced them.

Start a new coding conversation with the **Shared brief**, followed by **one phase prompt**. In later conversations, provide the shared brief again or point the agent to its saved repository copy. Do not ask it to implement all phases at once. Each phase ends with a working increment and a short phone check. If something feels cumbersome during a workout, use the adjustment prompt at the end before proceeding.

November target: phases 1–6, including the overview dashboard. Phase 7 is optional later work. This is an implementation sequence, not an estimate of calendar duration.

## Shared brief — provide with every phase

You are implementing a native Android training app for one person. Work in the existing repository, inspect its instructions and current implementation, and complete only the requested phase. If no repository exists, phase 1 establishes it.

### Product and user

The user is an experienced software engineer who has little time for hobby programming and wants you to do the implementation. They train roughly 2–3 times weekly: outdoor bouldering, finger strength, compound lifts, flexibility, mobility and core. The app is for personal use and will not be published.

The central problem is logging friction. The user logged consistently in an app that presented the planned exercises with a button to press. During training, confirmation should be quick; library creation can use more detailed forms. Avoid mandatory effort ratings, excessive taps, completion ceremonies and warm-up-specific machinery.

### Main interaction

The main tab is **This week**, not Today. It is one vertical list: unscheduled items for the selected week at the top, followed by Monday through Sunday. Exercises appear grouped inside modules, with standalone exercises supported. Highlight today and initially position near it while keeping unscheduled items easy to reach. Allow previous/next week navigation.

Tap an exercise before doing it to use its timer, or afterward to log results. Do not require a Start workout action before recording a set. Preserve unfinished inputs and save each confirmed actual immediately. Show the prescription and previous actuals at the point of logging, clearly distinguished. Prefill sensible values, but never silently record suggested values as performed work.

### User-created exercises and modules

Exercises are created inside the app. Do not hardcode individual exercises into Kotlin classes. A generic creation form supports:

- Name; mode: repetitions, duration, or duration-only activity.
- Optional numeric measurement with a unit, initially kg; no measurement is valid for a stretch or bodyweight exercise.
- Unilateral flag.
- Default prescription: number of sets, target reps or duration, rest duration, optional measurement value, optional RPE or RIR.

The exercise identity and its default prescription are distinct in storage but can share one screen. Label unilateral sets as sets per side. A generic set logger records L/R separately, with easy reuse of identical values. No compulsory warm-up tracking.

A module is a named, ordered group of exercises that can be copied into a week and moved or replaced as a unit. The module's training-purpose/category identity may be shared by base/taper variants. A standalone exercise uses the same underlying occurrence/logging model. Users can edit the scheduled copy without altering its source template.

### Data invariants

Use Kotlin, Jetpack Compose and Room, with named JSON fields for variable prescription/measurement payloads. Keep stable identities, dates, ordering and relationships in ordinary columns. Prefer a small conventional app structure over a generic framework.

- Scheduling copies template contents by value, retaining template IDs for lineage. Template edits or renames must not silently change existing scheduled or logged snapshots.
- Prescription records and actual-set records are separate. Actuals may reference a prescription, but an unplanned actual is valid. Every actual still belongs to an exercise occurrence and a training session/container.
- Planned four sets and actual six sets is valid. Zero actuals does not automatically mean skipped. Support explicit occurrence/session state where needed.
- Historical actuals can be corrected. Immutable template snapshots do not mean an uncorrectable training diary. Full event sourcing is unnecessary.
- Use stable exercise IDs rather than names as relationships. Preserve human-readable names in exports/snapshots. Variations may be separate exercises in v1.
- Named fields have canonical meanings and units. For example added load and assistance must not silently become the same quantity. Keep payload/export versions and metric definitions. Do not build an arbitrary schema designer.
- RPE/RIR are optional in prescriptions and actuals. Store absence as absence, not zero.
- Exercise comments belong to the exercise occurrence, not to each set or the global exercise definition.
- Persist training local date separately from event timestamps so travel/midnight do not unexpectedly regroup history.
- Use migrations that preserve existing data; no destructive reset as the normal upgrade path.

### Session and duration defaults

These are implementation defaults, not extra screens the user must fill out. Create or reuse a training-session container for a day's activity automatically. Allow splitting into a second session or merging a mistaken split in history later; modules are not session counts. Completing three modules together should not inherently produce three sessions.

For the overview, record actual elapsed training duration, including rest, separately from set duration. Duration is optional and can be entered or confirmed approximately. Store it in a canonical unit such as seconds and display minutes/hours. Never assume planned time was performed. Missing duration is not zero-duration evidence.

Attribute each duration entry to one dashboard category to avoid double counting. Do not sum both a session total and its component durations. A minimal duration ledger of non-overlapping category entries per session is acceptable. Optional timing can suggest a value, but users must be able to correct it. Keep entering duration out of the per-set logging path.

### Motivation and scope

A broad dashboard is part of the first usable app: selected-period session and hour totals, weekly/monthly stacked bars by category, and a category breakdown. It is evidence of accumulated work. Avoid headline adherence percentages, streak-loss messaging or targets that punish a deload. Empty or partial periods must not be presented as failure.

No account, cloud sync, backend, social features, web interface, AI API, publication workflow or general macrocycle engine in the initial scope. Everything essential works offline.

### Working agreement

Read the existing code before changing it. Make routine implementation decisions yourself and document consequential defaults briefly. Ask only when a missing answer actually blocks useful implementation. Do not reopen settled stack/product decisions.

Implement and run the checks available in the environment. Do not claim a phone, emulator, build or background-audio check passed unless it actually ran. If Android tooling is unavailable, complete what can be done and state the precise remaining check. Check current official Android documentation when selecting version-sensitive platform APIs, especially timer/background behaviour.

Keep tests focused on important invariants: persistence, snapshot isolation, actual/prescription independence, timer state, restore and aggregation. Avoid large suites that merely repeat UI implementation. Preserve unrelated changes.

Maintain a concise `docs/project-brief.md` from this brief and `docs/build-status.md` with completed phases, important choices, commands that work, known limitations and the next step. These allow another coding agent to continue without chat history. End each phase with a short change summary, checks actually run, and a few concrete phone checks. Stop at the requested phase.

## Phase 1 — runnable foundation

Implement phase 1 using the shared brief.

Goal: installable Android app with the basic week layout and persistent local data.

1. Inspect the repository/environment. If needed, create the smallest conventional Kotlin/Compose/Room Android project. Use a coherent supported toolchain; preserve a working existing setup.
2. Build the vertical This week screen: unscheduled section, Monday–Sunday, today highlight, previous/next week controls. Keep it usable on a normal phone with the keyboard and system insets.
3. Add minimal persistent entities sufficient for a sample exercise occurrence and its date/week placement. Do not implement every future table or screen speculatively.
4. Provide explicitly marked development sample data through an explicit seed/debug action; never reinsert it each launch or mix synthetic history into real records automatically.
5. Create the two project documentation files requested in the shared brief, recording data invariants and deferred work.

Acceptance: the app builds if tooling is available; a sample item appears in the chosen week; it remains after closing and reopening; changing weeks works. Provide exact build/install instructions appropriate to the repository. No designer, timer or dashboard yet.

## Phase 2 — create an exercise and log it with minimal friction

Implement phase 2 on the existing app using the shared brief. Preserve phase 1 behaviour.

Goal: the user can build their own exercise library, place exercise copies in a week and use this for real logging.

1. Add exercise creation/editing for the generic fields in the shared brief. Reps/duration and optional measurement fields should produce the relevant logger without exercise-specific code. Include explicit load semantics for loaded movements using a simple choice, not a complex unit system. Offer optional variation notes/metadata without requiring them.
2. Add a library picker that copies an exercise's prescription into either the week's unscheduled area or a date. Permit editing that copy. If an unscheduled item is logged, assign it to today by default, with a date override for backfilling.
3. Implement actual sets, optional prescription references, per-set measurements, side, optional RPE/RIR and an occurrence-level comment. Set identity must not depend only on a displayed set number.
4. Show recent actual results for the same stable exercise, distinct from today's prescription. Prefill values and make confirm/edit/add/undo quick. For unilateral work, record each side explicitly and reuse values without silently marking the other side complete.
5. Save confirmed sets immediately. Restore the current draft after navigation/recreation where practical. Do not require ending a session to persist results. Allow logged records to be corrected.
6. Support quick explicit occurrence completion/skip without making completion mandatory for saving sets. Create/reuse the date's session container behind the scenes.

Acceptance examples: create dumbbell rows with four sets per side, kg, 30-second rest and optional target RPE; log different weights for L/R; create an unloaded timed stretch without code changes; plan four sets and log six; log an unplanned set; change a library default and verify last week's copy/actuals stay unchanged; reopen and see saved results.

Provide a short real-workout check focused on number of interactions. No timer yet: the user can use an external timer while testing the logger.

## Phase 3 — the timer the user actually needs

Implement phase 3 using the shared brief and existing exercise/logger screens.

Goal: start a countdown from an exercise, switch apps or lock the phone, see remaining time quickly and hear a warning before it ends.

1. Add a single active countdown for work or rest, initialized from the prescription and adjustable before starting. Support pause/resume, cancel, finish and configurable advance warning (for example 30 or 10 seconds). Handle warnings longer than the countdown sensibly.
2. Use monotonic deadlines and explicit running/paused/finished state, not a decrementing counter as the source of truth. Persist enough state to recover coherently. After reboot, mark stale active timing interrupted rather than using an invalid old monotonic deadline.
3. Provide a persistent notification with remaining-time access and appropriate controls. Use a user-started foreground service/background implementation appropriate to current Android requirements. Verify official documentation and explain the selected approach briefly in project docs.
4. Ensure warnings/completion cues are not duplicated after pause, resume or recreation. Timer completion must not create a performed set. Offer logging afterward and a quick start-rest action after confirming a set.
5. Validate actual cue delivery, not just on-screen arithmetic. Do not use repeated exact-while-idle alarms as the per-interval engine. Do not assume a foreground service alone guarantees CPU wakefulness. Handle any required permissions/settings with a useful fallback and concise UI.
6. Check the chosen audio behaviour when another app is playing audio; document the behaviour and any untested device limitations.

Acceptance: timing remains correct across navigation, rotation/recreation and screen lock; pause/resume does not shift or duplicate warnings incorrectly; cancellation removes pending cues; logging remains independent; notification shows/accesses remaining time. Test background and forced-idle behaviour on a device/emulator if available, distinguishing arithmetic recovery from guaranteed audio delivery after process interruption.

Keep scope to one countdown plus convenient work/rest actions. Do not build a general protocol language or automatic interval-sequence editor.

## Phase 4 — modules and the full week workflow

Implement phase 4 using the shared brief. Reuse the existing exercise occurrences, logger and timer.

Goal: the user builds reusable modules and arranges their week without rewriting exercises.

1. Add a simple module editor: name, training-purpose/category, ordered exercises with default prescriptions. Reuse the exercise library picker and prescription editor. A small initial category list is enough; retain stable category identity.
2. Copy modules into a chosen day or the week's unscheduled area, preserving snapshots and lineage. Show all exercise rows under module headings. Allow standalone exercise rows alongside modules.
3. Move modules or standalone occurrences to a different day/week; edit their local prescriptions; reorder; replace with another library module. Buttons/menus are sufficient—drag-and-drop is optional polish.
4. Preserve already logged actuals when rescheduling or replacing planned work. If an occurrence has actuals, offer a sensible explicit distinction between moving remaining planned work and changing the historical training date; never silently rewrite history.
5. Preserve replaced/skipped items in history with their status and replacement relationship rather than deleting evidence. Future template edits never automatically propagate.
6. Add minimal duration capture per performed module/standalone activity with a single chart category. Label suggestions clearly and allow approximate actual values. Do not sum a session total with its components. Missing time must not become fabricated time.

Acceptance: schedule fingers and mobility together, move mobility, edit this week's sets only, replace a planned module, log part of a module and move remaining work without losing actuals. Multiple modules in one training session count as one session. Timer/logger remain accessible directly from exercise rows.

No fatigue engine, macrocycle builder, advanced filtering or batch programme propagation yet.

## Phase 5 — trustworthy record, activities, notes and restore

Implement phase 5 using the shared brief. This is required before relying on the app as the only copy of training records.

1. Add quick unstructured activity logging for climbing, running or a social gym visit: a reusable duration-only exercise inside a one-entry module, optional comment and optional post-session strain 1–5. Display hours/minutes but use canonical duration units in storage; never put duration in a load field. Allow it to replace a planned module.
2. Make previous weeks and exercise history easy to inspect and correct. Distinguish missing logging from explicitly skipped or replaced plans. Provide basic merge/split controls if session auto-grouping needs correction; do not force these into everyday logging.
3. Add a calendar-date note and a few optional daily metrics, e.g. energy 1–5, finger discomfort 1–5, and free text. Store definitions and scale labels as data using shared field validation; no metric-builder UI. Keep daily notes separate from exercise comments.
4. Implement complete versioned backup/export through a normal Android file/share workflow and restore through a file picker. Include definitions, identities, templates, snapshots, prescriptions, actuals, states, session grouping, durations, categories and text. The exported record must be understandable without this codebase. Avoid exporting transient active-timer state as resumable training.
5. Validate a restore before modifying live data. Use an atomic replace restore with an explicit confirmation and a pre-restore backup; no merge engine needed. Reject unsupported/corrupt files safely without partial replacement.
6. Add focused round-trip and migration checks. Never introduce destructive migration fallback for the real record.

Acceptance: export a mixed record, restore into a clean test database, and verify identities, counts, relationships, units and comments survive. A bad restore leaves current data intact. Unstructured climbing appears in history without fake sets or strength-load values. Process interruption cannot erase confirmed sets.

## Phase 6 — motivating overview dashboard (November scope)

Implement phase 6 using the shared brief. This is an overview of accumulated work, not advanced strength analytics.

Visual reference described in words: a dark, spacious mobile analytics screen with a date-range selector, a large training-hours total, a coloured category doughnut, and a broad stacked bar chart over weeks/months. The reference used a year overview with one coloured stack per month. Create a clean original implementation of this arrangement; the screenshot is not required to implement it.

1. Add a Dashboard tab with 4-week, 12-week, year and custom ranges. Show the actual selected dates and allow navigation to the adjacent period. Document exact range semantics and keep them consistent.
2. Show large performed-session and recorded-hour totals. Count sessions with actual activity, not planned modules, timer runs, daily notes or skipped plans. Include partially logged performed sessions. Avoid double counting modules within a session.
3. Make stacked bars the centrepiece: weekly buckets for shorter ranges, monthly for long ranges, colours by training category, consistent across all charts. Include empty buckets. Tapping a bucket shows its totals and underlying sessions. Label the current partial bucket neutrally.
4. Add a category doughnut/breakdown with hours and readable legend. Use the same exclusive duration entries as the bars. Show uncategorized duration honestly if present. Handle zero data gracefully.
5. Missing durations contribute to session counts but not fabricated hours. Label the hours as recorded time and show a quiet missing-duration count/link when useful. Do not treat unknown durations as evidence of no training.
6. No adherence headline, punitive streak, compulsory goal or comparison against the previous period. Totals refer to the selected period; do not pretend rolling-window totals are mathematically monotonic. The motivating feature is visible accumulated work.
7. Use deterministic synthetic data only in previews/tests, including a full year, empty months, mixed categories and partial weeks. Never insert fake achievements into the user's database.

Acceptance: bar totals, category totals and headline hours reconcile; date boundaries and year changes work; corrections update charts; two modules in one session do not become two sessions; missing time does not erase a performed session; a full-year view is legible on a phone. Provide a screenshot/emulator preview if tooling permits.

Do not add force curves, training recommendations or a web dashboard in this phase.

## Phase 7 — optional follow-up features, one at a time

Use the shared brief. Implement only the selected subphase below; do not implement all three together. If none is selected, ask which one to build.

### 7A — module selection and spacing warnings

Add module attributes for estimated duration, equipment, target system and coarse effort/skin/finger-demand tags. Make filtering useful for choosing something feasible today. Treat these as user-authored estimates, not physiological measurements. Add a small editable spacing rule for high finger-demand sessions, considering actual activities as well as future plans. Unstructured climbing can have an optional finger-demand tag; overall strain alone does not establish finger demand. Warnings explain which records triggered them, can be dismissed and never forbid scheduling. Missing data is not evidence of recovery. Test boundary cases and moved/replaced plans. No automatic programme generation.

### 7B — strength progress charts

Add opt-in charts for selected finger/pulling exercises: load over time under an explicit comparable-volume filter, and load versus reps with best-in-window or latest-per-rep options. For timed hangs use duration instead of pretending seconds are reps. Label these as performance relationships, not measured biomechanical force curves. Preserve side, load meaning, variation and any bodyweight context needed for comparison; do not combine added load with total load or assistance. Show the source sets on tap and explain the filter. Actuals are the source, never prescriptions. No estimated-max formula unless separately requested.

### 7C — timer convenience

After observing the single timer in real use, add the smallest useful automatic work/rest sequence for prescribed timed sets, including unilateral side cues if needed. Keep actual confirmation separate from elapsed timing. Reuse the reliable timer state owner, support interruption and edits, and avoid a general protocol programming language. Test transitions, pause/resume, cancellation and background cues without duplicating actuals.

## Adjustment prompt — use after a real workout

Use the shared brief and inspect the current app. My observed friction is: [describe what actually happened and what felt slow].

Fix this interaction with the smallest coherent change. First trace the current flow and identify the unnecessary work it imposes on me. Preserve existing training data and unrelated behaviour. Implement the improvement, verify the affected path and update build-status notes. Do not advance to the next phase or redesign the whole app. End with a short before/after description and one concrete phone check.

## Handoff prompt — switch coding agents without restarting

Read `docs/project-brief.md`, `docs/build-status.md`, repository instructions and the existing implementation. Continue phase [number/name] from the actual repository state. Do not recreate already working features based on this prompt file. First reconcile any mismatch between the status notes and code, then finish the requested phase. Preserve user changes and recorded-data compatibility. Report only material blockers, completed behaviour and verification evidence.
