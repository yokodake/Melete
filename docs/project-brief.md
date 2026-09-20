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
- Unfinished input is preserved; each confirmed actual is saved immediately.
- Show the prescription and previous actuals at the point of logging, clearly distinguished.
  Prefill sensible values, but never silently record a suggestion as performed work.

## User-created exercises and modules

Exercises are created in the app. No individual exercise is hardcoded in Kotlin. The generic
creation form covers:

- Name; mode: repetitions, duration, or duration-only activity.
- Optional numeric measurement with a unit (initially kg). No measurement is valid (stretch,
  bodyweight).
- Unilateral flag — unilateral sets are labelled *per side*.
- Default prescription: sets, target reps or duration, rest, optional measurement, optional
  RPE or RIR.

Exercise identity and its default prescription are distinct in storage but may share one screen.
The generic set logger records L/R separately with easy reuse of identical values. No compulsory
warm-up tracking.

A module is a named, ordered group of exercises that can be copied into a week and moved or
replaced as a unit. Its training-purpose/category identity may be shared by base/taper variants.
Editing a scheduled copy never alters its source template.

## Data invariants

Kotlin, Jetpack Compose, Room. Named JSON fields for variable prescription/measurement payloads;
stable identities, dates, ordering and relationships live in ordinary columns. A small conventional
app structure, not a generic framework.

1. Scheduling copies template contents **by value**, retaining template IDs for lineage. Template
   edits or renames must not change existing scheduled or logged snapshots.
2. Prescription records and actual-set records are separate. An actual may reference a
   prescription, but an unplanned actual is valid. Every actual belongs to an exercise occurrence
   and a session container.
3. Planned four sets with six actual sets is valid. Zero actuals does **not** imply skipped —
   occurrence/session state is explicit.
4. Historical actuals can be corrected. Immutable template snapshots do not mean an uncorrectable
   diary. Full event sourcing is unnecessary.
5. Relationships use stable exercise IDs, never names. Exports and snapshots keep human-readable
   names. Variations may be separate exercises in v1.
6. Named fields have canonical meanings and units. Added load and assistance must never silently
   become the same quantity. Payload/export versions and metric definitions are kept. No arbitrary
   schema designer.
7. RPE/RIR are optional in prescriptions and actuals. Absence is stored as absence, never as zero.
8. Exercise comments belong to the exercise **occurrence**, not to each set and not to the library
   definition.
9. The training local date is persisted separately from event timestamps, so travel or logging past
   midnight cannot regroup history.
10. Migrations preserve existing data. A destructive reset is never the normal upgrade path.

## Session and duration defaults

These are implementation defaults, not screens the user must fill in.

- A training-session container for a day is created or reused automatically. Splitting into a
  second session, or merging a mistaken split, is possible later in history. Modules are not
  session counts: three modules done together are one session.
- For the overview, actual elapsed training duration (including rest) is recorded separately from
  set duration. Duration is optional and may be approximate. It is stored in seconds and displayed
  as minutes/hours. Planned time is never assumed to have been performed; missing duration is not
  evidence of zero duration.
- Each duration entry is attributed to exactly one dashboard category, so nothing is double
  counted — a session total is never summed with its components. A minimal ledger of
  non-overlapping category entries per session is enough. Optional timing may suggest a value; the
  user can always correct it. Duration entry stays out of the per-set logging path.

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

1. Runnable foundation: week layout, persistent local data. ← *phase 1*
2. Create an exercise and log it with minimal friction.
3. The timer (countdown, background, advance warning, notification).
4. Modules and the full week workflow.
5. Trustworthy record: activities, notes, backup/export and restore.
6. Motivating overview dashboard (November scope ends here).
7. Optional follow-ups, one at a time: 7A module selection and spacing warnings, 7B strength
   progress charts, 7C timer convenience.
