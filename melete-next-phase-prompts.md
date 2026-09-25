# Melete — revised coding prompts

Updated 22 September 2026. These replace the remaining phases of the original plan. Use the shared instructions plus **one phase at a time** with any coding agent working in the repository.

## Order

1. **5A:** informal activities and duration.
2. **Timer A:** unilateral execution.
3. **Timer B:** repeaters.
4. **Timer C:** supersets/circuits and compact review.
5. **4B:** modules for planning.
6. **5B:** daily notes/metrics and backup/restore.
7. **6:** motivating dashboard.

Later: strength progression charts, work-time-only filters, module filters/recovery warnings, and draft preservation. The planner edit mode was being implemented when this plan was written; inspect its current state and preserve it.

## Shared instructions — include with each prompt

Work in the existing native Android Kotlin/Compose/Room project. This is personal software used during real training. Prioritise sensible interactions and low friction over generality. Inspect code and repository instructions; implement only the selected phase. Make routine technical decisions yourself, asking only about consequential unresolved UX choices. Preserve real records with proper migrations and preserve unrelated changes.

**First, fix the documentation inconsistency.** The September build-status document mixes current behaviour with superseded architecture and limitations. Reconcile `docs/project-brief.md` and `docs/build-status.md` against code and the decisions below. Separate historical changes from the current state. In particular, remove obsolete claims about exact-alarm backstops, alarm audio/audio focus, per-set writes, separate planned/performed dates, forbidden historical date changes, and missing features already implemented. Do not change working code to match stale documentation. Report a material code/requirement mismatch rather than silently choosing an old behaviour.

Preserve these established decisions:

- Exercise detail opens as information, with logging and timer actions. The logger itself has no countdown. Timer execution never silently commits workout history.
- The logger is a draft table. Planned rows begin checked. **Mark done** writes the workout in one transaction and closes; **Save changes** replaces the existing log without duplication. Draft persistence is deferred.
- Keep current load-per-set logging, including L/R and the compact max-load fallback. Explicit row loads override the fallback; missing required load is explained on save. Effort uses the existing optional five-point verbal scale; do not introduce extra RPE/RIR inputs or prescribe loads again.
- A completed timed set means its prescribed time was performed. Derive work time from confirmed sets and their captured timing prescription; no new actual-time or actual-rep fields to fill in. Repeaters use the same logger and load per set, with no pulse-by-pulse logging.
- One occurrence date: moving trained work re-dates its actuals and rehomes the daily session. Logged work cannot become unscheduled. Unlogged past plans stay where placed until the user changes them.
- Library scheduling chooses a week; day assignment happens in the planner. Duplicate creates a fresh unscheduled plan at the top of the same week, without actuals. Respect the planner edit mode for rearrangement.
- Preserve current deliberate deletion: unreferenced definitions can go; unlogged plans may be removed; logged work retains necessary identity. Deleting logged work is allowed with an explicit consequence/count. No replacement audit trail. Skipped work remains available for later analysis when the user keeps it.
- Templates supply defaults; scheduled prescriptions are independent copies. Stable exercise identity groups history. New routine/template edits must not rewrite existing logs or scheduled copies.
- The timer already supports work/rest programmes, preparation, transport controls and live cue settings. Extend it. Preserve locked-screen operation and music mixing, one active programme, monotonic deadlines and honest recovery. Guaranteed cues after process death are outside scope; do not reintroduce exact alarms for this.
- Dashboard count = completed exercise occurrences, not days, modules, timer runs, rounds or repeater pulses. The daily session table is an implementation detail.

Use focused checks for sequencing, persistence, migration, copying, logging and aggregation. Use the repository's data-preserving device-test workflow; do not uninstall or reset the user's app to test. Before applying schema changes to the real phone, ensure a recoverable database backup using the existing project workflow. Say which checks actually ran and which need a phone. End with a short result and a few concrete manual checks. Update current docs after each phase.

## 5A — Informal activities and duration

Implement two ways to plan/log a duration-only activity, reusing existing support where possible:

- **Other activity:** its own activity-name/type field, separate from comment; optional duration and effort. It creates an occurrence, not a one-off library entry. It can be added to a week or logged directly with a date.
- **Reusable library activity:** user-created entries such as open/outdoor climbing, silks or flexibility class, using the same simple duration/effort/comment logger without a set table. Climbing warrants convenient reusable entries, not a different storage engine or forced sample data.

Keep one-off names structured and stable enough to associate with a library definition later. A promotion UI is deferred. Completing a duration-only activity must work with no duration and count once without fabricated set records; review existing “has actuals” checks so these logs are protected like other completed work.

Add optional duration to prescriptions and logs. An empty prescription field uses an automatic estimate where possible; entering a value overrides it. Use timed work plus appropriate intervening rests; for reps, use a documented modest per-rep or per-set assumption. Account for unilateral execution. If no useful estimate exists, allow null.

In the logger, put duration behind an optional control, except in the simple activity logger. Show the inferred/default value in grey when the input is empty. Typing a value makes it manual; clearing returns to inference. Save the resolved numeric duration in the same field, plus a manual/inferred boolean. Explicit prescription estimates inherited into a log are inferred there unless the logging field is overridden. Show saved duration in the log viewer. Do not recompute old logs when defaults or formulas change; do not backfill missing historical time silently.

Keep total training time (including rests) distinct from work/stretch time derived from completed timed sets. Preserve the timing prescription needed for that derivation; no extra logging inputs or work-time chart yet.

Verify: one-off run leaves no library clutter; a reusable class can be scheduled repeatedly; duration can remain absent; overriding/clearing behaves correctly; saves/re-edits do not duplicate activities; historical duration stays fixed after prescription edits.

## Timer A — Unilateral execution

Extend the current programme engine to support both sides for standalone unilateral exercises. Each set executes **left, switch, right**, then set rest before the next set. Default to left first. Bilateral behaviour stays unchanged.

- Timed: left work countdown → side-switch countdown → right work countdown.
- Reps: wait for **Left done** → side-switch countdown → wait for **Right done**.
- Side-switch duration is a prescription field, default **15 seconds**, editable beside rest settings. Zero is allowed. No separate per-direction settings.
- Show exercise, set and side clearly on screen and in the notification. Reuse preparation, pause/resume, previous/next, cancellation and cues. Treat switching as rest; use existing preparation rules for short/zero transitions. Keep controls' interval semantics consistent.

One prescribed unilateral set includes both sides; logging remains the existing L/R load rows. Do not add logging interactions to the timer. Update duration estimation to reflect the sequence actually generated, including preparation where relevant and no trailing rest.

Verify both timed/reps paths, two sets, zero/short switching, pause/resume and transport near a side boundary, and notification actions. Check background cues on the phone where available.

## Timer B — Repeaters

Add an optional repeater mode to timed prescriptions: **timed reps per set, work duration per rep, rest between reps, number of sets, rest between sets**. Hide repeater-specific fields unless enabled. Existing timed sets retain their current behaviour.

Example: three sets, each containing six 7-second efforts with 3 seconds between reps, and 180 seconds between sets.

- Rep rest exists only between reps. After the last rep, use set rest instead, with no extra rep rest. No trailing rest after the last set.
- Unilateral: perform all reps on the left → side-switch rest → all reps on the right → set rest. Both sides together form one set.
- Do not insert five-second preparation between ordinary repeater pulses; that would invalidate the protocol. Keep initial preparation and sensible preparation after manual transport/resume into work. Specify these boundary rules in docs and tests.
- Clearly display set, rep and side as applicable; reuse the existing service, controls and cue settings. Ensure short intervals have useful cues without duplicate overlapping sounds.
- Update total-duration and work-duration calculations from the captured prescription. Work time excludes all rests.

**Do not redesign logging:** same load-per-set table, same L/R handling, same max-load fallback and Save action. No editable actual rep count or actual timing, no per-pulse records. A confirmed set means the prescribed repeater sequence was done. Store/reference its prescription for later interpretation.

Verify the example sequence, one rep, one set, zero rests, bilateral/unilateral, pause/skip/cancel and correct duration totals. Stop before circuits.

## Timer C — Supersets/circuits and compact review

Add saved, named routines containing ordered library exercises with independent copied prescriptions. Inspect how defaults and scheduled copies work in the current code; extend that model rather than sharing mutable library prescriptions. Editing an entry in a circuit must not change the library default, standalone plans or another scheduled circuit.

Use the familiar week-first scheduling flow. A scheduled circuit contains real exercise occurrences, so logging is available even without running the timer. A circuit is an execution pattern, distinct from a future organisational module.

V1 execution:

- All entries use the same circuit round count. Each turn executes one set per exercise; do not multiply rounds by its standalone default set count. Repeater reps remain inside that turn.
- Unilateral entries execute both sides consecutively, left then right. No “all exercises left, then all right” mode.
- Timed steps advance automatically; ordinary rep steps wait for Set done. Reuse unilateral switching and repeater behaviour.
- Configure rest between exercises and rest between rounds. These replace standalone set-rest allowances in circuit execution; rep rest and side-switch rest remain. Round rest replaces transition rest after the final exercise. No trailing rest after the final round.
- Before starting, show order, rounds and a compact timing summary. During execution, show exercise, round, side and repeater rep where applicable. Preserve notification controls and music/background behaviour.

Logging opens one review screen: a list of **expandable exercise rows**, consistently for timed work and reps. Each collapsed row has a completion checkmark and, when load is tracked, a small max-load input using existing fallback semantics. Expand for effort, per-set/side loads, partial set completion and comments. Checking a row selects its prescribed sets; unchecking clears them, with partial completion visible. One Save commits all reviewed exercise logs atomically; reopening edits/replaces rather than duplicates. Timer progress may prefill a draft but never writes history by itself.

Capture exercise identity plus standalone/circuit context, stable routine identity, a snapshot/version of its structure and entry position. This supports future progression filters without making duplicate library exercises. Each completed entry occurrence counts once, regardless of rounds; the circuit container contributes no extra count.

Compute elapsed duration once from the circuit sequence, never by summing standalone duration estimates. For later category charts, allocate each generated segment exactly once: work/rep-rest/side-switch to its exercise, transition/round rest to the preceding exercise. Store each exercise's suggested share as its editable log duration with the existing provenance flag. Do not also add a parent duration to dashboard totals. Work-only duration remains the sum of confirmed timed work.

Verify mixed timed/reps, unilateral and repeater entries; no compounded set counts/rests; quick review and expanded overrides; partial completion; repeat-save idempotence; snapshot isolation; context retention; and totals with no double counting. Do not build progression charts yet.

## 4B — Modules for planning

Add reusable named groups of exercises, optionally containing a circuit as a single execution item. Reuse existing library pickers and copied prescriptions. A module is an organisational group, not an additional logger or timer programme.

Allow creating, editing, duplicating and deleting templates; ordering entries; and adding a copy to any week's unscheduled section. Show it as a persistent named group in the planner. Support moving the group, editing the scheduled copy, moving entries out, and ungrouping without losing occurrences. Preserve standalone exercises and circuit entry relationships; do not permit recursive groups.

Retain template lineage and scheduled group membership for possible later analysis. Keep existing user-controlled date/deletion semantics: moving trained work corrects its date; deleting logged work requires explicit confirmation; skipped entries can be retained. No replacement audit trail or separate original-plan history. Template edits never propagate into existing copies. A module never increases completed-workout counts or adds duration on top of its contents.

Include simple optional planning metadata: purpose, estimated duration, equipment, target system, effort/CNS cost and skin cost. These are user estimates; no recovery engine or mandatory metadata form. Keep exercise categories authoritative for dashboard breakdowns.

Verify copy isolation, group moves, ungrouping, mixed standalone/circuit content, retained skipped work, template deletion preserving logs, and unchanged occurrence counts/durations. Use the current planner edit mode and keep the week visually compact.

## 5B — Daily notes, metrics, export and restore

Add a lightweight date-based diary: free text plus a few optional metrics (initial defaults: energy and finger discomfort, each on a clearly labelled five-point scale). Definitions, field types and scale labels are stored as data; no metric-builder UI. Diary entries never count as workouts or become mandatory during logging.

Provide complete, human-readable, versioned export and restore using Android's file picker/share flows. Include library definitions, one-off activities, templates, module/circuit snapshots and relationships, prescriptions, actuals, completion/skipped states, dates, effort labels, duration/provenance and notes/metric definitions. Preserve stable IDs and deleted-but-referenced definitions. Exclude active timer state from resumable training history.

Validate the full file before modifying data. Restore replaces the record atomically after explicit confirmation and a successful pre-restore backup; no merge engine. Corrupt/unsupported files leave live data intact. Never uninstall the user's app to test restoration.

Verify export → clean test database → restore with mixed activities, circuits, modules, skipped work, manual/inferred/missing duration, unilateral loads and diary notes. Check counts, identities and relationships, not just whether parsing succeeded.

## 6 — Motivating overview dashboard

Build a spacious overview with 4-week, 12-week, year and custom ranges; large **completed-workout count** and training-hour totals; stacked weekly/monthly bars coloured by exercise category; and a category doughnut/legend. Keep bars central and allow tapping a bucket to inspect its records. Show exact range dates, empty buckets and the current partial period neutrally.

- One completed exercise occurrence = one count. Each completed circuit exercise counts once, independent of rounds or pulses. Modules, circuit parents, timer runs, skipped plans and diary notes add nothing.
- Aggregate saved log durations once. Missing duration contributes no fabricated hours but does not suppress the workout count. Use saved manual/inferred values alike; provenance is available internally without cluttering the dashboard.
- Category and duration totals must reconcile. Circuit rest allocation already belongs to exercise durations; do not add it again.
- Count/history use the occurrence's current training date. Correcting a log updates the overview. No adherence headline, punitive streak or invented goal. Rolling totals can decrease as old records leave the selected window; don't promise monotonicity.

Use synthetic data only in previews/tests, including a full year and empty/partial periods. Verify counts, bucket boundaries, corrected dates, missing durations and mixed circuit categories. Defer strength progression charts and the optional work-only/stretch-time filter; retain the data already captured for them.

## Later, only when useful

- **Progression charts:** load over time at comparable prescription/volume; load versus reps or timed duration. Filter by exercise, side, load meaning and standalone/circuit context. Never silently mix those conditions.
- **Work-only time:** derive completed prescribed timed work, including repeater pulses and both completed sides; filter out rests without new logging inputs.
- **Convenience:** draft preservation, one-off-to-library promotion, module filters and explainable scheduling warnings. These are not prerequisites for the phases above.

## Owed, not yet scheduled

Added 23 September 2026, from using the app. Neither belongs to a phase; both are small and
should be picked up whenever the surrounding screen is next open.

- **An edit mode for the week view.** Cards should only move once you have said you are
  reorganising, rather than being one long press away at all times. Move up and down must cross
  day boundaries — move-up at the top of a day carries into the day before, and past the top of
  Monday into the week's unscheduled area. Today the only reordering is within one slot, and
  changing a day means the move dialog. Asked for since phase 4A and deferred every round since.
  **Done 2026-09-26:** *Edit* / *Done* in the week's header, ↑ ↓ per top-level card, crossing days.

- **Create an exercise from inside the circuit editor.** The *Add an exercise* dialog in the
  routine editor lists the library and nothing else, so building a circuit around a movement you
  have not defined yet means leaving the editor, creating it, and coming back to start again. It
  needs the same *New exercise* route the library picker has, returning to the circuit with the
  new exercise added. **Done 2026-09-23:** the dialog has *+ New exercise*; saving the editor
  hands the new id back through the circuit editor's saved state and it becomes the next station.
