# Melete — project brief

Native Android training app for one person. Not published, no account, no backend. Everything
essential works offline. This file is the durable copy of the brief: another agent (or a future
session) should be able to continue from this file plus `build-status.md` without chat history.

## Product and user

An experienced software engineer with little time for hobby programming, training roughly 2–3
times weekly: outdoor bouldering, finger strength, compound lifts, flexibility, mobility and core.

The central problem is **logging friction**. The app the user logged consistently in presented the
planned exercises with a button to press. During training, confirmation must be quick; library
creation may use more detailed forms. Avoid mandatory effort ratings, excessive taps, completion
ceremonies and warm-up-specific machinery.

## Main interaction

- The main tab is **This week**, not Today. One vertical list: unscheduled items for the selected
  week at the top, then Monday through Sunday.
- Exercises appear grouped inside modules; standalone exercises are supported by the same model.
- Today is highlighted and the list opens near it, while unscheduled items stay easy to reach.
- Previous/next week navigation.
- Tap an exercise *before* doing it to use its timer, or *after* to log results. No "start workout"
  step is required before recording a set.
- **Logging is one act.** The logger is a draft table — ticking, unticking and typing cost nothing
  — and one button writes the whole workout in a single transaction. Pressing it again on a logged
  workout replaces what was written, so correcting a log is the same gesture as making one.
- Show the prescription and previous actuals at the point of logging, clearly distinguished.
  Prefill sensible values, but never silently record a suggestion as performed work.

## User-created exercises and modules

Exercises are created in the app. No individual exercise is hardcoded in Kotlin. The generic
creation form covers:

- Name; mode: repetitions, timed sets, repeaters, or duration-only activity. The mode decides
  which numbers a plan carries: a repeater states its pulses and has no set length, a timed set
  states a length and has no pulses.
- Optional numeric measurement with a unit (initially kg). No measurement is valid (stretch,
  bodyweight).
- Unilateral flag — unilateral sets are labelled *per side* and executed left, switch, right.
- An optional category, from a closed set: open / board / structured climbing, finger training,
  strength and conditioning, flexibility, other activity. Each carries a colour, shown as a dot
  before the name. Absent is a valid answer.
- Default prescription: sets, target reps or duration, rest, side-switch rest, an optional repeater
  shape, an optional target effort, and an optional planned duration. It deliberately fixes no load: the
  weight is what the day decides and what the logger records.

Exercise identity and its default prescription are distinct in storage but may share one screen.
The generic set logger records L/R separately with easy reuse of identical values. No compulsory
warm-up tracking.

A **routine** is a saved, named circuit: ordered library exercises, each with its own copied
prescription, plus a round count and its own rests. It is an *execution* pattern — how work is
performed — and a scheduled copy of one is a container plus a real exercise occurrence per station.

A **module** is a named, ordered group of exercises that can be copied into a week and moved or
replaced as a unit. It is an *organisational* group, distinct from a circuit, and may contain one.
Its training-purpose/category identity may be shared by base/taper variants. Editing a scheduled
copy never alters its source template.

An **activity** is duration-only work with no set structure: a climbing session, a class, a run. It
can be a reusable library entry, or typed in as a one-off that creates an occurrence and no library
row. A one-off's exercise identity is derived from its name, so repeated sessions group in history
and can be associated with a library definition later.

## Data invariants

Kotlin, Jetpack Compose, Room. Named JSON fields for variable prescription/measurement payloads;
stable identities, dates, ordering and relationships live in ordinary columns. A small conventional
app structure, not a generic framework.

1. Scheduling copies template contents **by value**, retaining template IDs for lineage. Template
   edits or renames must not change existing scheduled or logged snapshots. This holds for an
   exercise's default prescription, for a routine's stations, and for a scheduled circuit, which
   also keeps a versioned snapshot of the structure it was cut from.
2. Prescription records and actual-set records are separate. An actual may reference a
   prescription, but an unplanned actual is valid. Every actual belongs to an exercise occurrence
   and a session container.
3. Planned four sets with six actual sets is valid. Zero actuals does **not** imply skipped —
   occurrence/session state is explicit. Conversely, an occurrence marked completed is evidence of
   training even with no sets at all, which is what a duration-only activity records; everything
   that protects history asks about completion as well as sets.
4. Historical actuals can be corrected. Immutable template snapshots do not mean an uncorrectable
   diary. Full event sourcing is unnecessary.
5. Relationships use stable exercise IDs, never names. Exports and snapshots keep human-readable
   names. Variations may be separate exercises in v1.
6. Named fields have canonical meanings and units. Total load and load relative to bodyweight must
   never silently become the same quantity; assistance is relative load below zero. Payload/export versions and metric definitions are kept. No arbitrary
   schema designer.
7. Effort is optional in prescriptions and actuals, on the five-point verbal scale; there is
   no RIR. Absence is stored as absence, never as zero.
8. Exercise comments belong to the exercise **occurrence**, not to each set and not to the library
   definition.
9. The training local date is persisted separately from event timestamps, so travel or logging past
   midnight cannot regroup history. A placement carries **one** date and its sets are filed under
   that same date: moving trained work re-dates its actuals and rehomes the day's session, and
   logged work cannot become unscheduled.
10. Upgrades preserve the training record. Schema 10 is the baseline (closing phase 5B; 11 added
    benchmarks): every
    schema change after it needs a data-preserving migration and a test, and neither build has a
    destructive fallback. Backup and plan files of format version 1 stay readable (backups are now format 2). Debug is a
    separate app (`com.yokodake.melete.debug`) whose data is disposable, but it takes the same
    upgrade path. See [the build guide](debug-release.md).

## Session and duration defaults

These are implementation defaults, not screens the user must fill in.

- A training-session container for a day is created or reused automatically. Splitting into a
  second session, or merging a mistaken split, is possible later in history. Modules are not
  session counts: three modules done together are one session.
- For the overview, actual elapsed training duration (including rest) is recorded separately from
  work time derived from completed timed sets. Duration is optional and may be approximate. It is
  stored in seconds on the occurrence and displayed as minutes/hours. Planned time is never assumed
  to have been performed; missing duration is not evidence of zero duration.
- An empty duration field asks for an estimate rather than meaning zero. The estimate is derived
  from the sequence the timer would actually run, so the planner and the timer cannot disagree, and
  it answers *nothing* rather than a guess where the plan says nothing to go on. A saved value
  carries a manual/inferred flag; history is never recomputed when a default or a formula changes.
- Each duration entry is attributed to exactly one dashboard category, so nothing is double
  counted — a session total is never summed with its components. A circuit's elapsed time is
  computed once from its own sequence and divided between its exercises, every generated segment
  allocated exactly once; the circuit container contributes no duration of its own. Optional timing
  may suggest a value; the user can always correct it. Duration entry stays out of the per-set
  logging path, behind an optional control.
- One completed exercise occurrence is one count. A circuit exercise counts once however many
  rounds it took; the circuit container, modules, timer runs, rounds and repeater pulses count
  nothing.

## Motivation and scope

A broad dashboard belongs to the first usable app: selected-period session and hour totals,
weekly/monthly stacked bars by category, and a category breakdown. It is evidence of accumulated
work — no adherence headline, no streak-loss messaging, no targets that punish a deload. Empty or
partial periods are never presented as failure.

Out of scope: account, cloud sync, backend, social features, web interface, AI API, publication
workflow, general macrocycle engine.

## Working agreement

- Read existing code before changing it. Make routine decisions; document consequential defaults
  briefly. Ask only when an answer actually blocks useful implementation.
- Run the checks available in the environment. Never claim a phone, emulator, build or
  background-audio check passed unless it actually ran.
- Check current official Android documentation for version-sensitive platform APIs, especially
  timer/background behaviour.
- Tests cover important invariants: persistence, snapshot isolation, actual/prescription
  independence, timer state, restore and aggregation — not large suites restating the UI.
- Keep `docs/project-brief.md` and `docs/build-status.md` current. End each phase with a change
  summary, the checks actually run, and concrete phone checks.

## Phase sequence

Revised 2026-09-28. Completed phases are marked; `docs/build-status.md` has the detail.

1. Runnable foundation: week layout, persistent local data. ✅
2. Create an exercise and log it with minimal friction. ✅
3. The timer (countdown, background, advance warning, notification). ✅
4A. Library and planning organisation. ✅
5A. Informal activities and duration. ✅
Timer A. Unilateral execution. ✅
Timer B. Repeaters. ✅
Timer C. Supersets/circuits and compact review. ✅
4B. Modules for planning. ✅
5B. Daily notes and trackers, backup/export and restore, plan import; **the stable baseline**:
    records and backup compatibility are preserved from here on. ✅
UI pass (part of 1.0): Menu · Calendar · Timer · Dashboard tabs, week swipe, folding modules, library search and pages, direct
    timing/logging from the library, last logged result, anchored save bars. ✅
Timer polish: the running screen's hierarchy — current exercise and phase, time or ALLEZ!,
    progress, what is next, transport — without changing timer semantics. Next, before 6A.
6A. Overview: motivating totals, category breakdown, weekly and monthly bars.
6B. History and basic graphs: drill into totals, browse exercise logs, plot load over time and
    load against reps.
6C. Month view: a calendar above a daily list; selecting a date jumps to it. Toggle planned work;
    include diary entries.
7A. Benchmarks: named reference tests with latest and best results, recorded apart from training.
    Brought forward for the baseline week. ✅
7B. Profile and settings: optional bodyweight history, Track bodyweight.
8. Progression analysis: refined comparisons — fixed reps or duration, variations, standalone
   against circuit, selected time windows.
9. Remote and web: remote backups first; a dashboard and web-authored plan imports as separate
   increments.
10. Appearance: a coherent visual polish pass across the app.

Later, only when useful: strength progression charts; a work-time-only filter; module filters and
explainable scheduling warnings; draft preservation; promoting a one-off activity to a library
entry.

From the 5B baseline on, database and stored-format compatibility are required: a schema change
ships with its migration, and old backups and plan files keep working. (Before it, a clean model
was preferred to compatibility code, and the debug build reset instead of migrating.)
