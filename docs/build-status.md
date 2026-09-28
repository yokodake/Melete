# Build status

Last updated: 2026-09-28, phase 7A: benchmarks (schema 11, backup format 2, plan format 1), after
the UI pass. Separate debug and release apps. See [the side-by-side build guide](debug-release.md).

This file has two halves. **Current state** describes the app as it is today and is the part to
trust; **How it got here** is a dated record of the work, kept because the reasoning behind a
decision is worth more than the decision, and is *not* a description of current behaviour. Where
the two ever disagree, the code and the first half win.

---

# Current state

## What is built

| Phase | State |
| --- | --- |
| 1 — runnable foundation | ✅ |
| 2 — create an exercise and log it | ✅ |
| 3 — the timer | ✅ |
| 4A — library and planning organisation | ✅ |
| 5A — informal activities and duration | ✅ |
| Timer A — unilateral execution | ✅ |
| Timer B — repeaters | ✅ |
| Timer C — supersets/circuits and compact review | ✅ |
| 4B — modules for planning | ✅ built; instrumented tests pass on the phone |
| 5B — diary, export/restore, plan import; stable baseline | ✅ instrumented tests pass on the phone; the hand checklist is still to tick |
| UI pass (part of 1.0, no schema change) | ✅ checked by hand |
| 7A — benchmarks (brought forward for baseline week) | ✅ built; unit tests and the schema/backup/benchmark device tests pass; screens still to be checked by hand |
| 6A — dashboard | ✅ built; unit tests and its query test pass; screens still to be checked by hand |
| Timer polish (follow-up, after phase 6) | not started — recorded in `ui-ux-handoff.md` |
| 6B — history and basic graphs | not started |
| 6C — month view | not started |
| 7B — profile and settings (bodyweight tracking) | not started |
| 8 — progression analysis | not started |
| 9 — remote and web | not started |
| 10 — appearance | not started |

Schema version **11** (10 was the **baseline**; `MIGRATION_10_11` added the benchmark tables):
every schema change comes with a migration and a test, and neither build has a destructive
fallback — see *Upgrades preserve the record* below. Backup format **2** (1 plus benchmarks);
backup format **1** and plan format **1** stay readable in every later version.
Prescription payload version **3**, actual-set payload version **2**, circuit
structure snapshot version **1**.

## Dashboard (6A)

The **Dashboard** tab. The measure (Hours or Exercises) is a small ▾ menu at the top right. The title's range (▾) picks **4 weeks · 12 weeks** (default) **· 6 months ·
12 months · all time**, each ending today with the current partial week or month included; all
time starts at the first completed exercise. Nothing is averaged. Beside the dates, **‹ • ›** pages
back and forth by whole ranges (no gaps, no overlap; ‹ stops when nothing earlier is recorded)
and • — filled on the current range, hollow away from it — returns to the range ending today.
Choosing another length returns to the current range; all time does not page.

- **Summary**: "24 h · 42 exercises" and the dates covered. Hours are recorded durations only,
  manual and inferred alike, with no separate estimate; a completed exercise with no duration
  counts and adds no time.
- **Hours or Exercises** decides what the bars, the shares and the exercise rows measure and sort
  by.
- **Stacked bars** by category, weekly through six months and monthly beyond (all time by its real
  span), empty periods shown. Segments follow the fixed category order and colours, 2px apart,
  each at least 4dp of colour — borrowed from that bar's largest segment, so every bar keeps its
  true height (`DashboardStats.segmentHeights`); a
  tap names a bar's period and totals above the plot (the latest by default).
- **Categories**: dot, name, hours · count · share of the chosen metric; "No category" is a row.
- **Exercises**: each exercise's hours and count, "≈" when some of its time was worked out.

What counts is `CompletedExercise` from `TrainingRepository.observeCompleted`: completed exercise
occurrences by training date, circuit stations included (their saved shares of the circuit's time),
never containers, modules, planned or skipped work, benchmarks or the diary. The arithmetic is
`DashboardStats` (unit-tested). The category palette was not run through the dataviz validator —
no Node on this machine — and stays the user's; the category rows name every colour. Filtering by
category and a doughnut are deferred, as agreed.

## Benchmarks (7A)

**Home → Benchmarks**: a small collection of reference tests, separate from the Library. Each row
reads "Latest **+30 kg** · 24 Sep" over "Best +35 kg", with a **+** to record a result; a tap
opens its history, a long press edits or hides it. *New* creates one. A name shared by two
benchmarks shows its protocol to tell them apart; otherwise the protocol is on the detail and the
entry dialog only. A **Hidden (N) ▾** row at the foot of the list opens the hidden ones below it;
they keep every result and can be shown again.

A definition is a name, a measurement (load, duration, distance, other) with a unit, for a load
whether it is total or **added to bodyweight** (signed: assistance below zero), one value or
**left and right**, whether **higher or lower** is better, an optional protocol ("20 mm · 7 s ·
added load") and an optional **goal** as written ("150% BW", "face to knees"). A **text** benchmark
records words ("touching heels"): it shows its latest result and has no best. A load result may
carry the **bodyweight %** reported with it, stored as written and shown as "(128% BW)". A
benchmark can be deleted only while it has no results.

**Plan files** can carry a `benchmarks` section (definitions and results; see
[plan-format.md](plan-format.md)), imported with *Add* under Settings → Import / export. Results ignore the
From today scope and are never added twice, so re-importing changes nothing; Replace plans never
removes benchmarks.

**Recording** is one dialog: the date (today, *Change*), the value or L and R, an optional note,
Save — the best valid attempt, not the set logger. Zero and negative added loads are results. The
detail page shows the protocol, **Latest** and **Best** (kept apart: the latest test may be below
an older best; each side's best keeps its own date), and the dated results, each corrected or
deleted after a **long press** (a tap does nothing, so scrolling cannot change a record). The
protocol and the goal share a line; the subtitle says the measure and unit, and "lower is better"
only when it is. The graph over time is **deferred** to the exercise graphs.

**A record apart from training (decision A):** a result is written to `benchmark_results` only —
no occurrence, session or set, nothing counted or totalled. The calendar shows it on its date as a
read-only line under the day (a red ⚑, then name · value), like the diary; a tap opens the benchmark. Each
result keeps the unit, load meaning and sidedness it was recorded under, so editing a definition
never rewrites an old result, and only results in the current unit and meaning compete for Best
(`BenchmarkStanding`). Results are in backups (format 2) and survive restore; a plan import never
touches them.

## Navigation

The bottom bar is **Home · Calendar · Timer · Dashboard**, four ordinary tabs that each keep
their state. The app opens on **Home**; Back from another tab returns there.

**Home** (`ui/home`): Profile and Settings are unlabelled icons in its top bar (described for
accessibility). Profile is inert until 7B. **Settings** (`ui/settings`):

- **Track bodyweight** — a switch for the diary's own *Bodyweight* tracker (number, kg, stable id
  `bodyweight`): on adds it or brings it back, off retires it and keeps every value. One history,
  the diary's, which 7B's Profile will read.
- **Benchmark reminders** — Off, or after 3, 6 (default) or 12 months: when Home's Benchmarks tile
  reminds.
- **Appearance** — a greyed placeholder, *System*; not built.
- **Keep screen awake** — *While timing* (default) or *Always*, while the app is in front.
- **Import / export** — its permanent place.
- **About** — *Melete Debug* in red, or *Melete*; version, build number, build type, package.

Preferences are `AppSettings` (SharedPreferences): how the app behaves, never the record, so they
are not in backups and a restore leaves them alone. Bodyweight is the exception by design — it is
part of the record. Home, below its top bar:

- A **Today** card — "Today · Mon 28 Sep ›", "2 / 5 completed" (or "Nothing planned"), one block
  per exercise (a plain bar past twelve), "1 skipped" when there is any, and "≈ 35 min remaining"
  only while something is left and every remaining exercise has an estimate (a circuit's
  exercises take their share of its one clock). Only the fill is coloured: a fixed quiet blue
  (`progressColor`) under way, the done green once all is done; the text stays neutral. It counts
  today's exercise occurrences — circuit and module members, never the containers; skipped work
  leaves the target; work logged today counts, undated work does not until it is dated; benchmarks
  and the diary never. A tap opens Calendar on the current week, scrolled to today
  (`GO_TO_TODAY`). The arithmetic is `TodaySummary`, unit-tested.
- **Import plan**, right under the card, only when nothing is left planned — no unstarted
  exercise dated today or later, and none undated in this week or a later one (an empty today is
  not an empty plan; `observeHasUpcomingPlan`). It opens Import / export with the plan file picker
  already up.
- **Daily note ›**, a compact row opening today's diary page, new or existing. When today has an
  entry, it shows under the label — the note, then the tracked values on the next line, up to five
  lines — so it is plain that opening it updates rather than starts one. 32dp of space follows.
- **Library** and **Benchmarks** as two equal, low tiles side by side (icon, label, a subtle
  background). The Benchmarks tile carries at most one quiet reminder line, "Max hang — 20 mm HC ·
  8 months ago": the visible benchmark whose latest result is oldest, once that is six months or
  more. A tap opens the list; a long press offers *Hide reminder for a month* (a display
  preference, not part of the record). **Dashboard** is an empty placeholder until 6A. (A left
drawer was tried first and dropped on the user's preference.) On Calendar, a horizontal swipe
turns the week — left for the next, right for the previous — past a 72 dp threshold. The
Week/Month selector on the week title (a small chevron) is **deferred** to 6C.

## The week

One vertical list: the week's unscheduled items on top, then Monday to Sunday. Today is
highlighted and the list opens near it — when the week changes, not every time the screen is
returned to, so coming back from a workout keeps the place. Previous/next week and a *Today*
action.

A scheduled **module folds** under its header (▸ / ▾). Folded, it shows its name and "4
exercises" or "2 of 4 exercises logged" (circuit stations counted, containers not), or the Done
chip. Today's modules start open, every other slot's start folded; a module opened or closed by
hand stays that way while the week screen lives. Display only: nothing about membership, order or
records changes.

Each day's `+` offers two things:

- **From library** opens the picker for that slot, with the library's own two tabs —
  *Workouts* (exercises and circuits together, alphabetical, a circuit carrying a small *Circuit*
  label) and *Modules* — and a bottom button that creates whichever kind the tab lists. An
  exercise copies its definition and prescription into the slot (asking which plan first, when it
  has variations); a circuit copies in as a container plus one real occurrence per station; a
  module copies in as a named group plus each of its entries as that real work, first saying
  which unavailable entries it will leave out and offering to edit it instead. The slot is fixed
  for the life of the picker, across switching tabs and detours to create something.
- **Other activity**, typed in by name with optional minutes — no library entry is created.

**The Library** (from the drawer) has the same two tabs, *Workouts* and *Modules*, with one
search over the tab on screen — the same search as the picker's. A tap opens what the item is:
an exercise's page, a circuit's contents (stations in order, each with its per-set plan, the
rounds, switch and rest, and the whole circuit's estimate) or a module's description and
contents. Each has an explicit **Edit** in its top bar and **Add to plan**; a long press still
offers *Add to plan*, edit, duplicate and remove. In the picker, a row's **Open** leads to the same
pages.

**Search**: commas separate conditions, all of which must match. A condition matches an item's
own text (name; a circuit's stations), its own category by any part of its name or the start of
its abbreviation, case-insensitive (`finger`, `FN`, `S&C`), or its kind as a whole word
(`circuit`, `exercise`). `!circuit` leaves circuits out. `pull, S&C`; `circuit, FNGR`;
`!circuit, fn`. Spaces are part of the text. Modules match by text only. Any other negation is
**deferred**.

**Start timer is one step**, from the library and the week alike: a *Start timer* dialog listing
the plans to start from — *Planned* (the week's copy) or *Default* (the library's), then each
variation with its chip — each with its plan in one line and its notes shortened, and **Custom**
last, which opens the prescription fields seeded from the plan chosen so far. The first is
selected; Start runs the choice; Cancel changes nothing. A running timer is asked about only after Start (declining starts nothing). The edit is
for this attempt only: a planned copy in the week is timed as edited but its plan and log are
never rewritten; from the library, Start puts one copy of that plan into this week's unscheduled
area and runs the timer linked to it (a timer records nothing), so the timer's *Log* opens that
same copy. Pressing Start again with the same plan reuses the copy; with another, the untouched
old copy goes. *Log exercise* from the library opens that timer's copy when there is one, else a
copy of the default dated today (with the usual *Change*), which is removed again if the logger is
left without saving (`discardUnloggedOccurrence`, which refuses anything logged, skipped,
completed or grouped). A copy made for a timer that is never logged stays in Unscheduled as a plan. *New workout* opens the exercise editor with **Exercise |
Circuit** at the top, and the rest of the form follows that choice — switching replaces the editor
rather than stacking one on the other. Editing an existing exercise or circuit never offers the
switch.

A card is tapped to open what it is, and long-pressed for its menu: move to any week and day (or
back to unscheduled), duplicate, remove. A circuit is one card and moves, removes and completes as
a unit.

**Edit mode** (*Edit* / *Done* in the week's header) puts ↑ ↓ beside every card. Only top-level
cards — a standalone exercise, a whole circuit, a whole module — take a place in a day's order;
what is inside a circuit or module keeps its own. Up past the top of a day lands at the bottom of
the day before, and above Monday in the week's unscheduled area; down past the bottom lands at the
top of the next day. Crossing a day is a real move: logged sets go with it, and trained work is
refused the unscheduled area. A copy cut from a
variation carries the variation's tag as a chip beside its name.

A **module** is an outlined group under its name, holding the ordinary cards of its members —
each opened, moved and logged as it would be on its own, with one extra menu item, *Take out of
module*. The group's own menu moves it (with the members still in its slot, taking their logs to
the new date), ungroups it (every member stays exactly where it is), or removes it. Moving a member
on its own takes it out of the group, and a member that ends up on another day — logged there, say
— is shown there rather than inside a group that says it is elsewhere.

Removing is refused once anything has been recorded, where "recorded" means a logged set **or** a
completed occurrence — a duration-only activity writes no sets and is still a workout that
happened. The stronger answer exists and names what it would destroy.

## Exercises, activities and circuits

- **An exercise** is user-defined: name, mode, optional measurement with explicit load semantics,
  unilateral flag, notes, description, category, and a default prescription. Nothing is hardcoded.
  There are four modes — **repetitions**, **timed sets**, **repeaters** and **activity** — and the
  mode is what decides which numbers a plan carries: a repeater states its pulses and has no set
  length, a timed set states a length and has no pulses. Neither can quietly hold the other's
  numbers, because `PrescriptionFormState.toPayload` takes the mode as an argument.
- **A prescription** fixes the shape of the work: sets, target reps or duration or pulses, rest,
  side-switch rest, target effort, and an optional planned duration. It deliberately does
  not fix a load.
- **A one-off activity** is an occurrence with no library row. Its exercise id is derived from its
  name (`core/OneOffActivity`), so two sessions called the same thing already group in history and
  could be promoted to a definition later. Going for a run once leaves no clutter behind.
- **A variation** is a named alternative plan for one library exercise — `A`, `PWR`, `END`: a tag
  of up to four capitals or digits (unique per exercise, folded to capitals as typed), its own
  prescription, and optional notes long enough for "first 3 reps at 40% of 5RM, then RIR 1–2". It
  is the same exercise, so history groups across variations. The exercise shows *Default plan*
  when it has none and *Plans* — the default first, then each variation with its chip — when it
  does, each edited by its own cog; *+ Add a variation* sits under them. A copy snapshots the tag
  and copies the plan by value; the variation's notes are read live, like the exercise's own.
  Editing or deleting a variation never reaches a copy already in a week.
- **A routine** is a saved, named circuit: ordered library exercises, each with its own copied
  prescription, plus a round count, a rest between exercises and a rest between rounds. Listed
  among the library's workouts. A station's form asks only what a circuit does not override: its
  target, side switch, pulses and effort — not sets, set rest or total time, which the circuit
  replaces with one set per round, its own rests and one clock for the whole thing.
- **A module** is a saved, named group of planned work: ordered exercises (each with its own
  copied plan, optionally cut from a variation) and whole saved circuits, plus an optional
  purpose / description. Organisational only: it is not trained, timed or counted, and adds no time of
  its own. The library's *Modules* tab. Template edits never reach scheduled copies; removing a
  template that was ever scheduled leaves a tombstone for their lineage. An entry whose exercise or
  circuit is gone or retired says *Unavailable — replace or remove* in the editor, with *Replace*,
  and scheduling leaves it out. The circuit picker offers *New circuit*, which comes back as the
  next entry with the module as it was left.
- **Adding to a plan from the library** — a row's menu, or *Add to plan* in an exercise's own menu
  — asks which plan when there are variations, then which week, from a list that scrolls without
  end.

## Logging

Opening a workout shows what it is first: description, the plan, the facts that change how a set is
performed, and — once logged — how long it took. The bottom bar offers the only two things worth
doing next.

A planned exercise's page shows **Last logged** under its plan: "Last logged · 24 Sep" over
"3 × 8 · 20 kg · Hard", or "8 × 20 · 8 × 20 · 6 × 22 kg" when sets differ, "L 20 / R 22.5 kg" for
uneven sides, "5 sets · 10 kg" for timed work (no reps invented), duration and effort for an
activity (`ui/detail/LastLogged.kt`). It is the most recent completed or logged occurrence of the
same exercise on or before this one's date (today's when unscheduled), by training date, never
this occurrence itself; the same variation is preferred, and a fallback to another plan shows
that plan's chip (or "default plan"). No earlier result, no block. The plan card no longer
repeats the category the header shows.

**The logger is a draft table.** Planned rows begin ticked; ticking, unticking and typing cost
nothing. *Mark done* writes the whole workout in one transaction, sets the occurrence completed and
closes; pressing it again on a logged workout *replaces* what was written, so correcting a log is
the same gesture as making one and the button says *Save changes*. The button sits in a bar
anchored at the foot of the screen — above the keyboard, with the reason it cannot save yet — in
the logger and the circuit review alike. Previous results are one compact line each, the latest
shown and the rest behind *N more*.

- Load is per set, with an L/R pair for unilateral work and a max-load fallback that fills rows
  saying nothing of their own but never overrides one that does.
- Effort is the optional five-point verbal scale, in plans and in logs alike. RIR was removed
  (2026-09-23): one way to say how hard is enough, and a stored `rir` is now ignored on read.
- **Time taken** sits behind a control (and is simply present for an activity). Empty is not zero:
  the estimate shows in grey and is what will be saved, marked *inferred*; typing makes the number
  the user's own; clearing hands the question back. Saved history is never recomputed when a
  default or a formula changes.
- A duration-only activity has no set table at all. It records that it happened, optionally how
  long and how it felt, and counts once — with no fabricated set records.

**A circuit opens as information**, exactly as an exercise does: one line of rounds, switch,
rest and the whole circuit's estimate, then the stations in order with their per-set plans, and
the only two things worth doing next — *Start timer* and *Log circuit*. Stations show no time of
their own: their allocated share includes the rests and read as a contradiction beside "30 s"
(the allocation itself is unchanged and still what the review infers). Going straight into the
review meant a circuit could only be answered and never read. Neither page explains counting any
more; that lives in *Counting* below.

**A circuit is logged in one review**: a list of expandable rows, one per exercise. Collapsed, a
row is a tick, a name and a max load; expanded, it has the same set table, per-side loads, partial
completion, effort, duration and comment as the single-exercise logger, because it is the same
record. One Save commits every station atomically. The review starts nothing: the detail screen
owns that button, so there is one place a countdown begins.

## The timer

One program at a time. Starting another from a workout or a circuit asks before calling the first
one off.

A **program** is a list of entries flattened once into a list of fully described steps
(`ProgramSequencer`). Every question — which exercise, which set, which round, which side, which
pulse — is answered by the step itself, so sequencing, skipping forward and going back are one
operation on an index.

What it can express:

- **Sets**: work, rest, work, rest, with no trailing rest after the last set.
- **Reps**: untimed. The timer waits at `AwaitingSet`; the athlete says the set is done and the
  rest starts by itself. That action is on the notification as well as the screen.
- **Unilateral**: one prescribed set is *left → switch → right*, then the set rest. Left first. The
  side-switch rest is a prescription field, default 15 s, and zero is allowed.
- **Repeaters**: one timed set is a series of pulses — so many reps of so many seconds with so many
  between. Rep rest falls between pulses only; after the last pulse the set rest takes over. For a
  unilateral repeater, all pulses on the left, then the switch, then all on the right.
- **Circuits**: one set of each exercise per round, so many rounds. A station's own set count and
  set rest are ignored in a circuit; the transition and round rests replace them, and rep rest and
  side-switch rest still apply. No trailing rest after the final round.

**Preparation** is five seconds before timed work. It runs at the start, and whenever a set is
reached by hand (next, previous, resuming a nearly-over rest). It does *not* run when a rest of at
least five seconds flows into the set on its own — the tail of the rest is the getting-ready, which
is what the amber is for. It is never inserted **between the pulses of a repeater**, because five
seconds there would not be the protocol any more; the boundaries around a pulse sequence (its first
pulse, the side switch, the set rest) keep the ordinary rule.

Cues: a heads-up at thirty seconds, a 3-2-1 tick, and quarter/half/three-quarter marks through a
work interval of a minute or more. Each family switches on its own, including mid-countdown. Cues
that would fall outside the countdown are dropped and two landing on the same moment collapse into
the more specific one, so a three-second rep rest sounds *two, one, go* rather than a pile-up.

The screen and the notification say the same sentence about where you are —
`positionDetail()` — naming the set or round, the circuit station, the side and the repeater rep,
each only when it distinguishes something.

**The timer tab builds all of it by hand**, not just the shapes it could before. Three modes —
timed sets, reps, repeaters — plus a *both sides* switch with its own side-switch field. The
create screen's `TimerCreateMode` is deliberately not `WorkKind`: a repeater *is* timed work, and
the difference is the shape of the set rather than whether a clock counts it. The numbers are
remembered between runs, opening on the classic six sevens with threes between, because this
screen is used mid-session with chalk on your hands.

## Duration

Two different quantities, kept apart:

- **Total training time** includes the rests and the getting-ready. It is what the planner
  estimates and what the logger records.
- **Work time** is the sum of the timed work alone (`TimerProgram.workOnlySeconds`). The timing
  prescription is preserved so it can be derived later; no chart is built yet.

The estimate comes from the sequence the timer would actually run, so what the planner promises and
what the timer does cannot drift. Documented assumptions, and only these: **3 seconds per rep**,
and **30 seconds for a set whose rep count is not even stated**. A timed exercise with no target
length and an activity with no duration have **no** estimate, and null is the answer rather than a
fabricated zero.

For a circuit the time is computed once from the circuit and then divided between its exercises
(`estimatedSecondsByEntry`), allocating every generated segment exactly once: work, rep rest and
side switch to the exercise being performed; transition and round rest to the exercise they follow.
The shares sum back to the whole, which is what will keep a dashboard from counting a circuit and
its parts both. The container itself carries no duration.

## The diary

A day's diary is a few lines and its trackers, on a page of its own (like the logger) opened from
**Daily notes** in the day's **+** menu. *Save* at the bottom writes it and goes back. A day with an
entry shows it in one line under the heading, and tapping that line opens the page too.

Trackers are data, edited under **⋮ → Edit daily trackers** on that page; coming back keeps what was
being written. Each has a name and a kind:

- **Scale**: a whole number between a lowest and highest value the user picks (defaults 0–5).
- **Number**: a decimal, with an optional unit such as kg.
- **Checkmark**: done or not.
- **Comment**: a short text.

New databases start with **Energy** and **Finger discomfort**, both 0–5. Trackers can be renamed,
changed to another kind, rescaled, reordered and retired — **from now on only**. Every recorded
value carries the tracker as it was that day (name, kind, scale, unit), so a past 3 out of 5 stays
3 out of 5 after the scale becomes 1–10, and a retired tracker still shows on the days that
recorded it ("No longer tracked"). Opening a past day shows such a value as recorded; clearing it
returns the field to today's definition. Every tracker is optional, *Not set* included; a day emptied completely is removed rather
than kept blank. The diary has its own repository and tables; nothing counts it, and logging never
asks for it.

## Backup and restore

*Import / export* is in Settings (and Home offers *Import plan* when nothing is planned), with *Backup* (Export, Restore) and *Plans* (Import a plan)
kept apart. Recovery copies are listed by date, "27 Sep · 19:33", with the year once it is not
this one; the stamp in the file name is local time and is read back as such, and the files are
never renamed. **Export** writes the whole record to a file chosen
with Android's picker: one human-readable JSON document (`format: "melete-backup"`,
`formatVersion: 2`; version 1 files still read, with no benchmarks) holding every table with its
stable ids — library, variations, circuits,
modules, everything scheduled with its snapshots, every set, sessions, trackers and the diary.
Tombstoned definitions are included, because history hangs from them. Dates are `2026-09-21`,
enums are names, and plans, set payloads and circuit snapshots are embedded as the JSON objects
they are stored as. The running timer lives outside the database and is never in it.

**Restore** reads the chosen file and validates it whole before anything changes: format and
version, unique ids, every reference the foreign keys enforce, one session per date and ordinal,
readable dates, plans and payloads, diary ratings on their scale. A file that fails says why and
nothing is touched. A sound one shows what it holds beside what is on the phone and asks; on yes,
the current record is written to a safety copy inside the app, then replaced in one transaction.
Safety copies are listed on the same screen with *Restore*, which is the undo — on a release build
they cannot be reached any other way. There is no merge.

## Plan import

**Import a plan…** on the same screen reads a hand-written plan file (`format: "melete-plan"`,
spec in [`plan-format.md`](plan-format.md)): exercises with variations, circuits, modules and weeks
written out literally, everything referred to by name. A module or circuit placed in a week can
override what it copies for that week only — `"plans"` for a module's own exercise entries,
`"rounds"` for a circuit (`setCircuitRounds`, `overrideModulePlan`) — so one FA module or one
intervals circuit serves every week of a ramp. **An import never deletes a record of
training**: anything logged, done or skipped stays (a circuit or module holding any of it is kept
whole), and so does the diary. Picking a file shows a preview before anything is written, with
two choices:

- **Add** keeps everything. A definition in the file replaces the library item of the same name
  from now on (planned and logged copies keep their snapshots); variations are matched by tag and
  never removed; weeks are planned after what the days hold. References resolve against the file,
  then the library.
- **Replace plans** saves a recovery copy, clears planned work that has not happened, retires or
  deletes library items the file does not name (hidden when they have history), and builds from
  the file. Items the file does name keep their ids, so their history stays attached.
- **From today** (default) or **Include past**: whether items dated before today are planned, and
  whether replacing clears unhappened plans before today.

The preview lists what is added and updated, and what is left out and why (a field the mode does
not read, an activity's sides or load). A file with any problem is refused whole with every
problem named; the import is one transaction through the same repository calls the editors make.
The test library is itself a plan file (`app/src/androidTest/assets/test-library.json`), with
the weeks of 21 and 28 September; `LibrarySeed` imports it and then logs a few of the first
week's days, since a plan file holds no logs.

## Counting

One completed exercise occurrence is one count. A circuit exercise counts once however many rounds
it took; the circuit container counts nothing; a duration-only activity counts once with no sets.
The daily session table is an implementation detail and is not a count of anything.

## Architecture as built

```
com.yokodake.melete
  MeleteApplication.kt      Application + AppContainer (manual DI, no framework)
  MainActivity.kt           single activity, Compose, edge-to-edge
  core/WeekMath.kt          Monday-based ISO week arithmetic and labels (pure, unit-tested)
  core/Planning.kt          reordering within a slot (pure)
  core/OneOffActivity.kt    derived, stable identity for a typed-in activity
  ui/Navigation.kt          type-safe routes + NavHost
  data/
    MeleteDatabase.kt       Room database v1, exportSchema = true, debug-only destructive reset
    MeleteConverters.kt     enum <-> String converters (stored names are part of the format)
    TrainingRepository.kt   week, library, scheduling, logging, activities, routines, circuits
    dao/TrainingDao.kt      TrainingDao, LibraryDao, LoggingDao, RoutineDao
    entity/Entities.kt      ExerciseEntity, PrescriptionEntity, ExerciseOccurrenceEntity,
                            TrainingSessionEntity, ActualSetEntity, RoutineEntity,
                            RoutineEntryEntity, CircuitInstanceEntity
    model/PrescriptionPayload.kt  versioned prescription payload (+ RepeaterPrescription)
    model/ActualSetPayload.kt     versioned actual-set payload
    model/CircuitSnapshot.kt      the routine structure a scheduled circuit was cut from
    model/ExerciseCategory.kt     the closed set of training-purpose categories
  ui/components/            PrescriptionFormState and the shared prescription fields, CategoryDot
                            ChoiceField.kt    one-of-N as a single line, used by every dropdown
                            CompactField.kt   every text field in the app, sized for its content
                            Chip.kt           SAMPLE / Done / Skipped, shared by week and library
                            PlanTargetDialog  week+day for a move, week-only for library adds
  ui/theme/SemanticColors.kt  colours that carry a meaning, kept out of the dynamic scheme
  ui/week/                  WeekScreen, WeekViewModel, WeekUiState (WeekItem), PrescriptionSummary
  ui/detail/                ExerciseDetailScreen + view model
  ui/library/               LibraryPicker, ExerciseEditor (+ view models)
  ui/logger/                LoggerScreen, LoggerViewModel, SetDraft, SetTable
  ui/routine/               RoutineListScreen, RoutineEditorScreen (+ view models)
  ui/circuit/               CircuitDetailScreen (what it is), CircuitReviewScreen (logging it)
  ui/timer/                 TimerScreen, TimerViewModel
  data/timer/
    TimerProgram.kt         entries, steps and the sequencer: what the timer is actually counting
    PrescriptionProgram.kt  plan -> program, and DurationEstimate
    TimerState.kt           deadline-based state, and the pure pause/resume/sequencing logic
    TimerSnapshot.kt        what is persisted, and how a run is restored after a reboot
    TimerStore.kt           SharedPreferences: snapshot and settings
    TimerController.kt      the single owner: start, pause, resume, cancel, cue delivery
    TimerService.kt         foreground service (specialUse) and the notification actions
    TimerCuePlayer.kt       generated USAGE_MEDIA tone and vibration
    TimerNotifications.kt   channels, the ongoing countdown, the finished alert
```

## Important choices

- **Week identity.** Monday–Sunday (ISO-8601), identified by its Monday's `LocalDate`.
  `exercise_occurrences.weekStartEpochDay` is that Monday; `trainingDateEpochDay` is the local
  training date, or `NULL` for an unscheduled item inside the week. Local dates are epoch days,
  event timestamps are epoch milliseconds — two columns, never derived from each other.
- **One date per placement.** A placement carries one date and its sets are filed under that same
  date. Moving a placement re-dates its sets and re-homes them into the new day's session, so the
  two cannot drift apart. Trained work cannot be made unscheduled: it happened on a day. Unlogged
  cards never move by themselves, which is what makes the planner worth reading backwards.
- **Prescription payload as versioned JSON.** `prescriptions.payloadJson` holds a named-field
  document with `payloadVersion` alongside it. Absent values are `null`, never `0`. Decoding uses
  `ignoreUnknownKeys`. A measurement carries `value`, `unit` and an explicit `meaning`: **total
  load** (everything lifted, never negative) or **added load** (relative to bodyweight, signed —
  below zero is assistance). There is no separate assistance meaning: one signed scale lets a move
  from assisted to weighted read as one line of progress, and the heaviest set is always the
  largest number.
- **A plan lives on its owner.** An exercise's default, a variation, a circuit station, a module
  entry and a scheduled copy each hold their own plan as a JSON column (`prescriptionJson`,
  `defaultPrescriptionJson`). Scheduling copies the text, so nothing is shared, nothing needs
  cleaning up, and editing one copy cannot reach another. Logged sets carry no reference to a plan:
  what was planned is the occurrence's. (Until schema 6 plans lived in a `prescriptions` table of
  never-mutated rows that owners and sets pointed at; deleting an owner left its rows behind.)
- **Occurrences snapshot their template.** Name, mode, unilateral flag, measurement unit and
  meaning, category. `exerciseId` is lineage only and carries **no** foreign key, so a renamed or
  deleted library entry cannot rewrite or delete history.
- **A scheduled circuit is a container plus real occurrences.** The container
  (`circuit_instances`) holds the execution shape and a JSON snapshot of the routine version it was
  cut from; the stations are ordinary `exercise_occurrences` with `circuitInstanceId` and
  `circuitPosition`. That is what makes a circuit loggable without running the timer, and what
  keeps the count honest — the container is not an occurrence and counts nothing.
- **Actual sets are identified by id, never by set number.** `orderIndex` is a position; `id` is
  identity.
- **Actual payloads are independent of prescriptions.** An unplanned set is ordinary, and six
  performed sets against four planned ones need no special case.
- **Duration lives on the occurrence, not on a set.** A duration-only activity has no sets, and a
  circuit exercise's share of the clock belongs to the exercise rather than to any one round.
  `loggedDurationManual` is provenance, not formatting: an inferred value that has been saved is
  still what the workout says it took, so changing a default later leaves it alone.
- **Effort lives on the set, except where there are no sets.** `exercise_occurrences.loggedEffort`
  is read only when an occurrence has no sets, so the two can never disagree about one workout.
- **Sessions are implicit.** The first write of a training date creates that day's
  `training_sessions` row and every later one that day reuses it.
- **Logging is one transaction.** `TrainingRepository.saveLogs` takes a list of
  `OccurrenceLogWrite`, so a single exercise and a whole circuit review go through the same path.
  Replacing rather than appending is what makes saving twice a correction. Unticking clears a
  station's sets and drops it back to planned; a deliberate skip is left alone.
- **Deletion is as complete as the history allows.** Nothing refers to it → the row and its default
  prescription go. Planned but never trained → those copies go too. One logged set *or one
  completed copy* → tombstone, and it leaves the library only. The count is re-taken inside the
  transaction.
- **Removing a routine never touches a scheduled copy.** Those are real occurrences and real logs;
  a template going away is a statement about what you plan next. A routine that has been scheduled
  becomes a tombstone, because its id is what a circuit log points at.
- **Upgrades preserve the record (since the 5B baseline).** Schema 10 is the oldest schema any
  installed app can have. A schema change bumps `MeleteMigrations.CURRENT`, exports the new
  `N.json` beside the old ones (never delete one), and adds a `Migration(N - 1, N)` to
  `MeleteMigrations.ALL`. `MigrationTest` builds a database at every exported version from the
  baseline and migrates it to the current one, and opens a baseline database through the app's own
  builder with its rows intact; `SchemaBaselineTest` pins each released schema's identity, so an
  entity changed without a version bump fails instead of silently rewriting `10.json`. **Neither
  build has a destructive fallback** — debug wiped itself on schema changes until 5B, which hid a
  missing migration until release; now Melete Debug fails on open exactly as release would.
  Before the baseline, migrations were deliberately skipped: the five that carried v1 to v6 were
  removed while every record was disposable.
- **Backups stay readable.** `androidTest/assets/backup-v1.json` is a real export frozen at the
  baseline, holding every kind of row. `BackupFixtureTest` checks it still reads and validates;
  `BackupCompatibilityTest` restores it and checks it exports back as the same record. The fixture
  is never edited: a change that breaks it must be made to read old files instead, and a new
  format version gets its own fixture beside it. The test library does the same for plan files.
- **No sample data.** `DevSampleData`, the `isSampleData` column on four entities, its DAO delete
  queries, the debug menu and the SAMPLE chips are all gone. Seeding a library is the importer's
  job, and a flag threaded through the schema to mark rows as disposable stopped earning its keep
  the moment the whole database became disposable.
- **Manual DI.** `AppContainer` on the `Application`.
- **Text chevrons instead of Material icons.** `material-icons-core` is frozen at 1.7.8 and is not
  part of the current Compose BOM, so navigation uses `‹` / `›` / `⋮` / `+` glyphs with
  `contentDescription` semantics.

## How the timer works, and why

Checked against the current Android documentation on 2026-09-20; the version-sensitive parts are
foreground service types and background audio.

- **A countdown is a deadline on `SystemClock.elapsedRealtime()`**, never a decrementing counter.
  A counter drifts, stops when the process is frozen and cannot be rebuilt after the UI is
  recreated. Elapsed-realtime keeps running while the device sleeps and is immune to the wall clock
  being changed. Rotation, navigation and process death cost nothing: the state is re-derived.
- **Reboots are detected with `Settings.Global.BOOT_COUNT`**, stored beside the deadline. After a
  restart a running countdown is reported as *interrupted* rather than resumed from a meaningless
  deadline. A paused one survives, because its remaining time is a duration.
- **Scope: locked, not killed.** The timer must survive the phone being locked and other apps being
  in front. It is explicitly *not* required to survive the app being killed. **There are no exact
  alarms and no `USE_EXACT_ALARM` permission**; they existed only for the killed case and were
  removed with everything they forced — the durable at-most-once cue bookkeeping, the two racing
  delivery paths, the cue mutex. A cue that has sounded is remembered in
  `TimerState.Running.delivered`, which empties by itself at each interval boundary.
- **The deadline is still persisted.** A killed process makes no sound, but reopening the app
  recomputes from the monotonic clock and either shows the true remaining time or says the
  countdown ended while the app was not running, rather than pretending it alerted.
- **Foreground service type `specialUse`.** A training countdown matches none of the defined
  categories. `shortService` is capped at three minutes — shorter than a hangboard rest — and
  Android 17 excludes it from background audio outright. `specialUse` has no runtime timeout, and
  the Play Console declaration it normally requires does not apply to an app that is never
  published. The manifest carries the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation.
- **The service is started from a tap while the app is visible**, which is what grants it
  while-in-use capability.
- **The service never calls `stopService` on itself from the controller.** Stopping a service whose
  `startForegroundService` has not reached `startForeground` breaks the platform's promise and it
  kills the process. The service watches the state and stands itself down instead.
- **Keeping the screen on is a window flag, not a wake lock**, tied to a countdown actually running
  rather than to the timer tab being open.
- **A foreground service does not keep the CPU awake.** A partial wake lock does, held only while a
  countdown is running and bounded by its remaining time plus ten seconds.
- **The cue does not interrupt the music.** No audio focus is requested — asking at all, even as
  transient-may-duck, hands the music app the decision — and the tone plays as **`USAGE_MEDIA`**,
  not `USAGE_ALARM`: the platform fades media to zero on its own when an alarm-usage player starts,
  which is heard as the music stopping. The notification channels are silent, because the cue is
  played explicitly and a channel sound would double every beep. There is no ducking; ducking is
  the other app's decision and cannot be forced.
- **The cue plan is fixed when an interval starts**, so changing the settings mid-countdown cannot
  re-owe a moment that has already gone by.
- **Quarter cues are for work intervals only.** A rest needs to know how much is left, not where
  its middle was.

## Commands that work

`JAVA_HOME` must point at a JDK; Android Studio's bundled one works:
`C:\Program Files\Android\Android Studio\jbr`.

**Build numbers.** Every Gradle run that assembles, installs, bundles or packages the app — debug
or release, from the command line or Android Studio — takes the next number from one shared
counter in `build-number.properties` at the root (gitignored: this machine's count). Changing
`appVersionName` in `app/build.gradle.kts` starts it again at 0. Test-only runs and syncs leave it
alone. `versionCode` is `major·1 000 000 + minor·10 000 + build`, so it keeps rising across
versions; About shows "Version 1.0 · build N".

| Purpose | Command |
| --- | --- |
| Build the debug APK | `./gradlew :app:assembleDebug` |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests against Melete Debug | `scripts/device-tests.sh` |
| Instrumented tests via Gradle (debug data is disposable) | `./gradlew :app:connectedDebugAndroidTest` |
| Install Melete Debug alongside release | `./gradlew :app:installDebug` |
| Build a release APK | `./gradlew :app:assembleRelease` |
| Update the everyday release (requires compatible schema/migrations) | `./gradlew :app:installRelease` |
| Install a built APK by hand | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Launch release | `adb shell am start -n com.yokodake.melete/.MainActivity` |
| Launch debug | `adb shell am start -n com.yokodake.melete.debug/com.yokodake.melete.MainActivity` |
| Make audio violations loud instead of silent | `adb shell cmd audio set-hardening throw` |
| Copy the database off a debug build | `adb exec-out run-as com.yokodake.melete.debug cat databases/melete.db > melete.db` (**and the `-wal` and `-shm` beside it** — see below) |
| Seed Melete Debug with the test library, two weeks and a few logs | `adb shell am instrument -w -e seed library -e class com.yokodake.melete.LibrarySeed com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner` (add `-e planning false` for exercises only) |
| Add ~4 years of made-up training history to Melete Debug (refuses twice unless `-e force true`) | `adb shell am instrument -w -e seed history -e class com.yokodake.melete.HistorySeed com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner` |
| Sound the timer's finish cue on the phone (touches no data) | `adb shell am instrument -w -e action cue -e class com.yokodake.melete.DeviceActions com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner` |
| Try an import, or put back a recovery copy, in Melete Debug | `adb shell am instrument -w -e action import -e mode add\|replace -e scope today\|past [-e dry true] -e class com.yokodake.melete.DeviceActions com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner`; `-e action restore -e copy <file>` for a recovery copy |
| Put the test plan in the phone's Downloads | `adb push app/src/androidTest/assets/test-library.json /sdcard/Download/test-library.json` |

APKs: `app/build/outputs/apk/debug/app-debug.apk`,
`app/build/outputs/apk/release/app-release.apk`.

Release retains `com.yokodake.melete` and its existing signing key. Debug now uses
`com.yokodake.melete.debug`, the **Melete Debug** label and a grayscale icon. They install side by
side with separate private data. Installing debug does not replace release or grant access to
its database. Use in-app export for release backups; see [the build guide](debug-release.md)
for the first-install transition, test isolation and release migration requirements.

**A backup is three files, not one.** Room runs in WAL mode, so `melete.db` can be a day or more
behind while the recent sessions sit in `melete.db-wal`. Pull all of them:

```
for f in melete.db melete.db-wal melete.db-shm; do
  adb exec-out run-as com.yokodake.melete.debug cat "databases/$f" > "backups/$f"
done
```

Opening the set once with any SQLite client replays the WAL and leaves a self-contained `melete.db`.
Check it before trusting it: `PRAGMA user_version`, `PRAGMA integrity_check`, and a row count.
`backups/` is gitignored, because it is personal training data.

**In Git Bash, prefix adb commands that take a phone path with `MSYS_NO_PATHCONV=1`**, or
`/sdcard/...` is rewritten into a Windows path and `adb push` fails with `secure_mkdirs() failed`.
Gradle needs `JAVA_HOME` pointing at Android Studio's JBR (`/c/Program Files/Android/Android
Studio/jbr`).

**Debug test data is disposable.** Gradle-managed device tests may remove/reset the debug
installation. `scripts/device-tests.sh` checks the debug IDs before installing both APKs with
`install -r` and driving the debug runner directly. Seeding and device helpers can still modify
its data, but the everyday release is a separate app. Do not invoke a legacy pre-split
`com.yokodake.melete.test` runner still installed on a phone.

## Checks actually run

Phase 5A and timer A/B/C (written without a device; **no phone was connected for any of it**):

- **141 unit tests passing** (up from 104), all green.
  - `TimerSequenceTest` (22 new) walks the new shapes exactly as the controller walks them:
    unilateral timed and reps work running *left, switch, right* before the set rest; a
    zero-second side switch still getting the ordinary lead-in; transport landing on a side and
    saying which; the three-sets-of-six-sevens repeater example step by step, with no rep rest
    after the last pulse and no trailing rest after the last set; exactly one preparation in a
    whole repeater program; a unilateral repeater doing every pulse on one side before switching;
    one rep / one set / zero rests degenerating correctly; a circuit running one set of each
    exercise per round and *ignoring* its stations' own set counts and set rests; a circuit station
    that is itself unilateral or a repeater; every second of a circuit allocated to exactly one
    exercise, with the shares summing back to the whole; and the duration estimates, including
    that a plan with nothing to go on answers null. One of them records a crash the tests caught
    before any of this reached a phone: a circuit station whose exercise is timed but has no target
    length became a bare-rest entry, and stripping a circuit station's set rest off one of those
    produced an invalid program. Such a station now waits for you, as a set of reps does.
  - `DurationLoggingTest` (12 new) pins the provenance rules: an untouched field saves the estimate
    as *inferred*, typing makes it *manual*, clearing hands the question back, an explicit planned
    duration is inherited as inferred until the log overrides it, no estimate saves nothing rather
    than a zero, the prescription form round-trips the new fields, an empty field stays absent, a
    zero side switch is a real answer, a half-typed repeater is no repeater, and one-off identity
    ignores case and spacing but not a real difference.
  - `WeekUiStateTest` (4 new) on circuit grouping: a circuit is one card with its stations folded
    into it, it is done only once every station is, and a station whose circuit is missing still
    shows rather than disappearing.
  - The 25 existing `TimerProgramTest` cases pass **unchanged** against the rewritten sequencer,
    which is the evidence that the refactor did not move the single-exercise behaviour.
- **19 new instrumented tests written and compiling** (`ActivityAndCircuitTest`), plus
  `migrate4To5KeepsTheRecordAndAddsTimeActivitiesAndCircuits`. **None of them has been run**: no
  device was attached. They cover a one-off leaving no library entry, two one-offs with the same
  name sharing one identity, an activity completing with no duration and no fabricated sets, a
  completed activity being protected from deletion and unscheduling, a reusable class scheduled
  repeatedly, duration provenance, historical duration staying fixed after a prescription edit,
  saving twice correcting rather than duplicating, a circuit's snapshot, routine edits not reaching
  a scheduled copy, two copies not sharing prescriptions, one atomic save across stations with each
  counting once, unticking clearing a station, moving a circuit with its logs, and removing a
  routine leaving its copies alone.
- `./gradlew :app:assembleDebug`, `:app:compileDebugAndroidTestKotlin` and `:app:assembleRelease`
  all clean. The schema-5 JSON is exported (`app/schemas/.../5.json`).

Then on the Pixel 9 (2026-09-22):

- **54 instrumented tests passing**, the whole suite, in 58.5 s via `scripts/device-tests.sh`'s
  path (`adb install -r` of both APKs, then `am instrument`, so the training record survived the
  run). That is the 19 new `ActivityAndCircuitTest` cases, the new
  `migrate4To5KeepsTheRecordAndAddsTimeActivitiesAndCircuits`, and the 34 that already existed.
- **The 4→5 migration was applied to the real training record and verified against a backup taken
  immediately before it.** `user_version` 4 → 5, `integrity_check` ok, and every table the same
  size afterwards (6 exercises, 13 prescriptions, 4 occurrences, 3 sessions, 12 actual sets). The
  occurrence rows, the actual-set rows and the exercise rows compare **identical column for
  column** before and after; the three new tables are empty and every new column is unset, so
  nothing was backfilled with a guess. The app launched clean with no migration exception.
- **The backup needs the WAL.** `melete.db` alone was a day stale — Room runs in WAL mode, and a
  461 kB `melete.db-wal` held everything logged that day. Copying only the `.db`, which is what
  this file used to suggest, would have been a backup missing the most recent session. Pull
  `melete.db`, `melete.db-wal` and `melete.db-shm` together; opening the set once with any SQLite
  client replays the WAL and leaves a self-contained `.db`.

Then a first round of feedback on all of it, from using the app (2026-09-22):

- **Repeaters became a mode** rather than a switch on a timed prescription. It is a different
  movement to plan, to run and to read back — six sevens on and threes off is not "a 57-second
  hang" — so asking which of the two you are creating is a clearer question than asking for a
  duration and then taking it away again. The mode now decides which numbers a plan carries.
- **The timer tab grew repeaters and a both-sides switch.** They were reachable only from a
  prescription, which made the hand-built timer quietly the weaker of the two.
- **A circuit opens as information**, with *Start the circuit* and *Log the workout*, instead of
  dropping straight into the review.

**152 unit tests passing**, up from 141: `TimerCreateTest` (9) walks everything the create screen
can now express and checks a hand-built repeater is the same sequence as a prescribed one, and
`DurationLoggingTest` gained the check that each mode takes only the numbers it means. A new
migration test covers the category rename, the untouched `FLEXIBILITY` and a null staying null.

Then a second round, on the phone this time (2026-09-23):

- **The timer's create screen stopped moving.** The form shared the countdown's centred column, so
  every mode change re-centred the page. It owns its own layout now — chips pinned top, summary and
  Start pinned bottom, a weighted middle that absorbs the slack — and every element holds position
  to the pixel across all three modes and both toggle states. Every row is full width with the same
  label column, so the fields keep one left edge.
- **Dropdowns replaced chip rows** for how a set is measured and what the number means, behind one
  shared `ui/components/ChoiceField`. The arrow is a filled triangle in a 36dp box: Material's
  `TrailingIcon` carries a 48dp icon slot and was setting the height of every dropdown in the app,
  and the first hand-rolled replacement went too far the other way.
- **Seven categories, recoloured**, replacing the three the app shipped with. Open climbing, board
  climbing and structured climbing are violet, red and blue; finger training is raspberry; strength
  and conditioning keeps the old orange; flexibility is yellow; other activity is a soft green. Each
  has a light and a dark variant, because a fully saturated dot glares on a dark surface — and the
  pairs that are hue neighbours separate by darkness in one mode and by hue in the other.
- Two fixes found by looking: `lastWorkSeconds` had been zeroed by an earlier build storing a
  repeater program's empty `workSeconds`, so Timed sets opened at 0:00 with Start dead; and the
  category chips were a plain `Row`, which fitted three and crushed the sixth to one letter a line.

**Schema 6** renames two stored category constants. Enum constants are stored by name and
`MeleteConverters` reads an unknown one as *no category*, so without `MIGRATION_5_6` the upgrade
would have silently stripped the colour off every exercise and every snapshot that had one.
`OPEN` becomes `OPEN_CLIMBING` and `CONDITIONING` becomes `STRENGTH_CONDITIONING`; `FLEXIBILITY` is
unchanged and the four new categories start empty. That is a narrowing — an `OPEN` row that meant
an open *lifting* session now reads as climbing — so the library is worth a glance afterwards.

**Applied to the real record and verified**: `user_version` 5 to 6, `integrity_check` ok, all five
exercises and seven occurrences renamed with none lost, against a backup taken immediately before.

Then a clear-out (2026-09-23), once the record was confirmed disposable until October:

- **The migration chain went**, with its six tests and six exported schemas. See *No migration
  chain* above for what replaced it.
- **The old per-set logging path went.** The sixth round replaced it with the draft table and left
  it behind: `SetEntryBar` (a hundred-line composable), `PerformedSetRow`, `displayNumber`,
  `SetDraft` and its saved-state persistence, `confirmSet`, `undoLastSet`, `editSet`. None of it
  had a caller — a whole second logging mechanism sitting beside the real one, waiting to be
  helpfully reconnected.
- **Sample data went**, along with the `isSampleData` column and everything that read it.
- `TrainingPersistenceTest` was rewritten to write through the repository rather than seed, which
  makes it exercise the path the app actually takes.

**4,662 lines deleted against 868 added.** 144 unit tests pass, down from 152 because
`SetDraftTest` tested a class that no longer exists.

**Still owed on the phone** — everything in *What still needs a phone* from section 1 onwards. The
instrumented suite has not run since the timer and circuit work; it is now 49 tests rather than 54,
the migration suite having gone.

## Known limitations

- **Nothing in phase 5A or the timer extensions has been run on a device.** Everything above was
  checked by unit test, compiler and schema export only.
- **The logger's draft is lost if you leave without marking done.** Ticks, loads, the duration and
  the note are held in memory until the one write. Draft persistence is deferred by design — and
  `SetDraft`, the `SavedStateHandle` plumbing that used to persist the *old* per-set draft, has
  been deleted rather than left lying next to the table it no longer feeds.
- **The circuit review's draft is likewise in memory**, and is seeded once when the screen opens.
- **A one-off cannot be promoted to a library entry** from the UI. The identity is already stable
  and derived from the name, so the promotion is a later convenience rather than a migration.
- **An exercise that has been kept rather than deleted cannot be un-kept from the UI.**
  `restoreExercise` exists in the repository with nothing calling it.
- **Target durations, rest and the side switch are typed in seconds** in the prescription form; the
  planned duration is typed in minutes and the timer in minutes and seconds. The forms have not
  been unified.
- **A circuit's stations cannot be reordered after scheduling**, only in the routine. Reordering a
  scheduled circuit's stations would change what the snapshot says was run.
- **A circuit does not offer duplicate**, only move and remove.
- **A circuit station cannot be opened on its own.** Tapping the card opens the review, so a
  station's description and previous results are not reachable from inside a circuit.
- Only one program runs at a time. Starting another asks before calling the first one off.
- A program's volume comes from the prescription. Nothing reconciles it against what was actually
  logged; the timer still records nothing, by design.
- Next moves one interval, not one set. Previous restarts the interval on screen unless pressed
  within `TimerTransitions.RESTART_WINDOW_MS` (one second) of it starting. A set of reps has no
  elapsed time to measure, so it has no window and previous always steps back.
- A reps program waiting on `AwaitingSet` waits forever. There is no honest length for a set of
  repetitions, but it does mean a forgotten timer sits in the shade until cancelled.
- **Resuming a paused rest with under five seconds left becomes a preparation**, including for a
  three-second repeater rep rest. That follows from the resume rule and is a deliberate choice for
  a pause the user asked for, but it does lengthen that one gap.
- Surviving the app being **killed** is explicitly not a requirement; the countdown makes no sound
  while the process is dead.
- Switching a cue family on part-way through a countdown writes off moments that have already gone
  by rather than firing them late.
- A countdown's labels are snapshotted names, deliberately not live links: a timer reaching zero
  must never be able to touch what was logged.
- Starting a timer from the library leaves a planned copy in this week's Unscheduled if it is
  never logged (by choice: the copy is what the timer's *Log* records).
- The week's module folding lasts while the week screen lives; it is not saved across restarts.
- `today` is computed when the UI state is built, so an app left open across midnight keeps the old
  highlight until the state is rebuilt.
- The month abbreviation in week labels comes from the device locale. Unit tests pin `Locale.US`.
- No dashboard yet.
- **Plan files cannot delete or reorder.** Adding never removes a library item, a variation or
  anything planned; to start over, *Replace all*.
- **Export and restore go through the file picker, not a share sheet.**
- **A module's members cannot be reordered in the week.** Edit mode moves the module as a
  whole; the order inside it is the template's, changed in the module editor.
- **Circuit stations carry no variation.** A routine's stations copy the library default, as they
  always have; a variation can be chosen for a module's standalone exercises only.

## What still needs a phone

Section 0 is **done**, on 2026-09-22. Everything from section 1 onwards still needs a person with
the phone in their hands: the suite exercises the database, not the screens, the speaker or the
lock screen.

### 0. Install and upgrade — done

- [x] **Backed up** to `backups/pre-v5-20260922-175123/melete.db` (with the WAL; verified
      self-contained, `user_version = 4`, `integrity_check` ok, 6 exercises / 4 occurrences /
      12 sets).
- [x] **Installed the debug build over the release build** with `adb install -r`. Data preserved.
- [x] **Ran the instrumented suite**: 54 tests, all passing, in 58.5 s.
- [x] **Applied the 4→5 migration** by launching the app. No exception, `user_version` 4 → 5,
      `integrity_check` ok, every table the same size, and the occurrence, actual-set and exercise
      rows identical column for column against the backup. The new tables are empty and every new
      column is unset.
- [ ] **Look at it.** Open two or three past weeks by hand. Every card, its plan, its comment, its
      "Done" chip and its logged load as before, and **no time shown against any of them** — the
      row comparison says the data is intact, but only you can say the screen agrees.

### 1. Regressions in what already worked

The timer engine was rewritten underneath, so the old paths need re-walking even though their unit
tests pass unchanged.

- [ ] **A plain timed program.** 3 × 10 s with 60 s rest, from an exercise. Preparation only before
      the first set, work, rest, work, rest, work, **no rest after the last one**, then finished.
- [ ] **A reps program.** It waits at each set; *Set done* on the **notification** starts the rest.
- [ ] **A bare rest** started from the timer tab still counts down and ends.
- [ ] **Pause, resume, previous, next** mid-program. Previous within a second of an interval
      starting steps back; later, it restarts the interval.
- [ ] **Log an ordinary exercise** exactly as before: tick, type a load, mark done, reopen, correct
      a load, save again. The record must be corrected, not duplicated.

### 2. The new planning surfaces

- [ ] **The `+` menu** on a day heading and on the unscheduled heading offers exercise / other
      activity / circuit.
- [ ] **A one-off activity.** *Other activity* → "Outdoor bouldering", 120 minutes → mark done.
      It counts, the card shows **2 h**, and the library is still empty of it.
- [ ] **A one-off resists deletion.** Long-press it → remove. Refused, because it is a record.
- [ ] **Renaming a one-off** from its logger corrects the name on the card.
- [ ] **A reusable activity.** Create a library exercise in *Activity* mode, schedule it on two
      days, log both. One definition, two occurrences, no set table on either.
- [ ] **A repeater exercise.** Create one in *Repeaters* mode: the editor asks for reps, seconds on
      and seconds off, and never for a set length. Its card reads as its pulses.
- [ ] **An activity with no duration** still marks done and still counts.

### 3. Duration and its provenance

- [ ] **Inferred stays inferred.** Log an ordinary exercise without touching *Time taken*. Reopen:
      the field is still empty with a grey estimate, and the detail screen says
      "worked out from the plan".
- [ ] **Typed stays typed.** Type a number, save, reopen: it comes back as typed, with no
      "worked out from the plan".
- [ ] **Clearing goes back to inference**, and does not record a zero.
- [ ] **History does not move.** After saving a duration, edit that copy's prescription (double the
      sets). The saved time must be unchanged.
- [ ] **No estimate is an honest blank.** A timed exercise with no target duration offers no grey
      value and saves no time.

### 4. The timer extensions — the ear checks

These are the ones no test can make.

- [ ] **Unilateral, timed.** 2 sets × 10 s, both sides, 15 s switch. Watch for
      *left → switch → right → rest → left …*, and the screen naming the side.
- [ ] **Unilateral, reps.** Waits for left, counts the switch, waits for right.
- [ ] **Zero-second switch** still gives a five-second lead-in into the second side.
- [ ] **Repeaters, the real protocol.** 3 sets × 6 × 7 s on, 3 s off, 180 s between sets.
      **The critical check: no five-second gap between pulses.** Time a set by hand — it should
      take about 57 s, not about 87 s.
- [ ] **Short intervals still cue.** The 3 s rep rest sounds *two, one, go* — audible, and not a
      pile-up of overlapping beeps.
- [ ] **The timer tab builds a repeater by hand**, and it runs identically to a prescribed one.
- [ ] **The timer tab's *both sides* switch** produces left, switch, right, and the side-switch
      field appears only when it is on.
- [ ] **The create screen reopens on the numbers you last used**, including the pulses.
- [ ] **A repeater set rest** behaves like an ordinary rest, and the next set's first pulse is not
      led into.

### 5. Circuits

- [ ] **Build a routine** of three exercises — ideally one timed, one reps, one unilateral or a
      repeater. Check the "about N minutes" line changes as you change the rounds and rests.
- [ ] **Station plans are private.** Edit a station's numbers in the routine; the library default
      for that exercise is untouched.
- [ ] **Schedule it.** One card in the week with its stations listed inside it, not three loose
      exercises.
- [ ] **Tapping the card opens what it is**, not the log: rounds, rests, the stations in order with
      their plans and their share of the time, and two buttons at the bottom.
- [ ] **Edit the routine afterwards** (change rounds and a station's numbers). The scheduled copy
      must not change.
- [ ] **Run it on the timer**, phone locked. One set of each exercise per round; the notification
      names the exercise, the round and the side; no station runs its own four sets.
- [ ] **Review and save.** Collapsed rows, tick them, one *Mark done*. Each exercise counts **once**
      however many rounds; the circuit itself adds nothing.
- [ ] **Partial completion round-trips.** Untick one round of one exercise, save, reopen: it comes
      back showing that exercise as partly done, not fully.
- [ ] **Save again** — corrections, not duplicates.
- [ ] **Allocated time is sane.** The per-exercise minutes in the review add up to roughly the
      circuit total shown at the top, and no more.
- [ ] **A recorded circuit resists removal**, and the stronger answer names how many exercises it
      would destroy.
- [ ] **Move a recorded circuit** to another day: the stations and their sets move with it, and
      "anytime this week" is not offered.
- [ ] **Remove the routine** from the Circuits list. The scheduled copy and its log stay.

### 8. Diary, export and restore

- [ ] **Diary**: + → *Daily notes* on a day, write a line, set energy, *Save*; the line shows
      under the heading. Clear everything and save: it goes.
- [ ] **Trackers**: in the notes page, ⋮ → *Edit daily trackers*; add *Weight* (number, kg), a
      checkmark and a comment; reorder; back in the notes the draft is intact and the new ones are
      there. Change Energy's scale and retire one: the day's line still reads as recorded.
- [ ] **Export** to Downloads; open the file and read it — dates, names, plans as objects.
- [ ] **Restore that file**: the summary matches, *Replace* works, a safety copy appears below.
      Restore the safety copy: back as before.
- [ ] **A broken file** (edit one date by hand): refused with the reason, nothing changed.
- [ ] **Import a plan**: with the seed in place (two weeks planned, Monday, Tuesday and Friday of
      the first logged, Thursday skipped), import `test-library.json` from Downloads with *Add* —
      library items under *Updates*, none duplicated; the hike's warnings listed; *From today*
      leaves out the past items and counts them; the logged days untouched.
- [ ] **Replace plans**: the same file, *Replace plans* + *Include past* — the preview clears the
      planned items and keeps the 5 logged or skipped ones; after it, Monday, Tuesday, Thursday and
      Friday's pull-ups are still there with their sets, and everything else is freshly planned.
      Switch to *Replace all*: the counts to be deleted show; cancel. Import a file with a typo'd
      name: refused, naming it.

### 7. Variations and modules

- [ ] **Make a variation** from an exercise: *+ Add a variation*, type `pwr`, see `PWR`. A second
      `PWR` on the same exercise is refused with a message under the tag.
- [ ] **Plans on the exercise**: *Default plan* with no variations, *Plans* with them, each with
      its own cog; delete one from its dialog.
- [ ] **Add to plan** from the exercise's menu and from a library row: the plan question appears
      only for an exercise with variations, then the week list scrolls well past December.
- [ ] **The chip** shows on the week card, on the exercise opened from the week (with the
      variation's notes), and on the logger's planned card.
- [ ] **Make a module** with an exercise (pick a variation), a circuit and a new exercise created
      from the add dialog; write a description; save; reopen and see it all come back.
- [ ] **Schedule it** from the picker's *Modules* tab: one outlined group on that day, members
      inside in order, the circuit as its own card within.
- [ ] **Move the group**, **take one member out**, **ungroup**, **remove** — with and without a
      logged member (a logged one refuses *unscheduled* and asks before deleting).
- [ ] **Workouts | Modules** in the library and the picker, the *Circuit* label, and the bottom
      button following the tab. *New workout* → switch to *Circuit* → back leads out, not to the
      exercise form.
- [ ] **An unavailable module entry**: retire an exercise used in a module; the editor flags it
      with *Replace*, the list and picker warn, and scheduling asks before leaving it out.
- [ ] **Circuit fixes**: a station's form has no sets / rest / total time; the review has one row
      per round; an unscheduled circuit's review says *Logging for …* with *Change*.
- [ ] **Remove a completed activity** from the week: it offers *Delete activity and log*, and it
      works.

### 6. Background and audio

- [ ] Everything in sections 4 and 5 at least once **with Spotify playing**: the music keeps going
      and the cue is audible over it.
- [ ] At least one whole program **with the screen off**, confirming every cue is heard.
- [ ] The ongoing notification's *Pause* / *Resume* / *Set done* / *Cancel* buttons all work from
      the lock screen.

---

# How it got here

A dated record of the work and the reasoning. **Not a description of current behaviour** — several
entries below were later reversed, and where they were, the reversal is recorded too.

## Phase 1 — runnable foundation

- Kept the existing Android Studio toolchain (AGP 9.4.1 / Gradle 9.6 / Kotlin 2.2.10 /
  compileSdk 37 / minSdk 33 / Compose BOM 2026.02.01) and added Room, KSP and
  kotlinx.serialization to it.
- **This week** screen, minimal persistence (schema v1), explicit debug-only sample data.

Checks: `assembleDebug` clean; 17 unit tests; 4 Room persistence tests on a physical Pixel 9.

## Phase 2 — create an exercise and log it

- Exercise editor, library picker, logger, set entry bar with prefilled values and one-tap confirm.
- Schema v2 with `MIGRATION_1_2`: `training_sessions` and `actual_sets` added, plus
  `exercises.measurementMeaning`, `exercises.notes` and
  `exercise_occurrences.measurementMeaningSnapshot`.
- Navigation added (`navigation-compose`, type-safe routes).

Checks: 23 unit tests; 20 instrumented tests on the Pixel 9 covering snapshot isolation,
actual/prescription independence, set identity, session reuse and `migrate1To2…`. A full on-device
walkthrough confirmed `user_version = 2`, explicit `LEFT`/`RIGHT` sides and `rpe`/`rir` stored as
`null` rather than `0`.

*Superseded:* the set entry bar and per-set immediate writes were replaced in the sixth round of
feedback by the draft table and the single transaction.

## Phase 3 — the timer

A single countdown for work or rest, three cue families, a persistent notification, the screen held
awake while counting. Reaching zero never records a set.

Checks: 55 unit tests; 31 instrumented tests; a 63-second work interval run with the screen off,
the platform logging audio active eight times at exactly the planned spacings. A crash was found
and fixed: starting the foreground service and stopping it before `startForeground` killed the
process with `ForegroundServiceDidNotStartInTimeException`.

*Superseded:* the exact-alarm backstop, the durable cue bookkeeping and the `USAGE_ALARM` audio
attributes described in this phase were all removed in the fourth and fifth rounds below.

## Bottom navigation and the effort scale

Three tabs — week, library, timer — hidden inside focused flows. Effort became a five-point verbal
scale stored as its integer level; reps in reserve stayed a planning field.

## First round of use feedback (schema 3)

From `src/feedback.md`, after the app was used:

- **A workout opens as information, not a form.** `ui/detail/ExerciseDetailScreen`: what it is, how
  to do it, what is planned. Editing the definition is a button on that screen, never the screen
  you land on.
- **Exercises carry an explanation and a category.** `description` is read live so a correction
  reaches every copy; `category` *is* snapshotted, so re-categorising cannot recolour history.
- **The default prescription no longer prescribes load.**
- **Removing a planned workout costs intent**: long press on the week screen, and still refused
  once sets exist.
- **Done reads green**, from `SemanticColors.kt` rather than a scheme colour.
- **The timer, first pass**: work/rest stopped discarding typed durations, minutes-and-seconds
  input, cue families behind an overflow and switchable mid-run, green through work and amber for
  the last five seconds of a rest, a label carried from the exercise, and a prompt before replacing
  a running countdown.

## Second round: the timer counts sets

- `TimerProgram` holds sets, work and rest; `TimerTransitions` walks it, with no trailing rest.
- Reps stop at `TimerState.AwaitingSet` and wait.
- **One interval is one run id**, because the at-most-once bookkeeping is keyed by it — a single id
  would have sounded the first set's end and then stayed silent.

Two real bugs were found by the new tests before the code ran on a phone: the no-argument
`TimerProgram` was invalid and threw, and a program with no rest stopped after its first set.

## Third round: preparation and transport controls

- `TimerPhase.PREPARE` as a real countdown, before the first set and before any set reached by
  hand, but not when a long enough rest flows into it.
- Resuming a nearly-finished rest becomes a fresh preparation rather than a stretched rest, because
  the old rest has already sounded some of its cues.
- Previous / play-pause / next; cancel kept apart and below.
- `TimerProgram.steps` flattened a program into its intervals.

## Fourth round: the alarm backstop is gone

The user was explicit about scope: **locked, not killed**. Exact alarms existed only for the second.
Removing them removed `TimerAlarms.kt`, the `USE_EXACT_ALARM` permission, the durable
`markCueDelivered`/`clearCues` bookkeeping, `TimerController.onAlarm`, the cue mutex and the
countdown loop's generation counter — about 170 lines net, and the timer's hardest invariant
("exactly one of two racing deliverers wins, durably, across process death") stopped existing
rather than being maintained.

## Fifth round: the cue stops interrupting the music

The cue was stopping Spotify dead. Two causes, and fixing only the first did nothing audible:

1. **Audio focus**, even as transient-may-duck, hands the music app the decision. Nothing is
   requested now.
2. **`USAGE_ALARM`** — the platform fades media to zero on its own when an alarm-usage player
   starts. The cue now plays as `USAGE_MEDIA`.

Confirmed by ear on the Pixel 9 against Spotify, including locked; `AS.FadeOutManager` no longer
appears in logcat. There is no ducking: that is the other app's decision and cannot be forced.

Also this round: 33 instrumented tests run on the Pixel 9, including four rounds of timer work that
had never run on a device. One caught a real bug — `start(program, settings)` planned the first
interval from the argument while every later one read `store.cueSettings`, so a program started
with explicit settings quietly reverted after one set. The store is now the single source.

## Phase 4A — library and planning organisation (schema 4)

One nullable column, `exercises.deletedAtEpochMs`.

**Deletion is as complete as the history allows**, on the user's rule *keep the fact that I did it,
never the fact that I planned it*: nothing refers to it → deleted; planned but never logged → the
plans go too; one logged set → tombstone. The count is re-taken inside the transaction.

**One date, and the log follows it.** 4A first shipped a planned date and a performed date allowed
to disagree, reported as `Planned Mon · Logged Tue`. That was cut on the user's call. Deleted with
it: `PlannedOccurrence.performedDate`, `observePerformedDatesInWeek`, `PerformedDateRow`,
`Planning.dateMismatch` and the *Change the day it was done…* menu item.

Also: move to any week, duplicate, nudge within a slot, edit the local prescription, and a deletion
dialog that names how many sets it would destroy.

Checks: 93 unit tests; **34 instrumented tests passing on the Pixel 9**, including
`migrate3To4RetiresNothingAndKeepsEverything`.

## Sixth round: logging becomes one act

Used on the phone, and most of it was wrong. Five changes, all the user's calls.

**The tick was the write.** Ticking recorded a set and unticking deleted one, so the table was a
live database view wearing a form's clothes and *Mark done* had nothing to do. The table became a
draft, and marking done writes the whole workout in one transaction. `SetRow.done` became a plain
`Boolean` instead of a list of database ids, which is the whole change in one line.

- Planned rows start ticked; unticking is how you say otherwise.
- Nothing is loggable until every ticked set says what it weighed **or** a max load stands for all
  of them. The button stays enabled and says what is missing.
- The max load is a fallback, never an override, and is *deduced* from the rows.
- Unilateral rows fall back per side.

**Planning got smaller.** *Copy to…* became **Duplicate**. Adding from the library asks for a week
and nothing finer.

**Density.** `ui/components/CompactField.kt` rebuilds the text field from `BasicTextField` and
Material's own decoration box at 10dp × 6dp, because `OutlinedTextField` enforces a 56dp minimum
height and 16dp of padding that are not reachable through parameters. A set row went from 68dp to
48dp; effort and max load each went from a heading plus a full-width box to one line — about 175dp
saved with no font made smaller.

Also: the countdown left the logger entirely, the note is written in place, effort is a dropdown
starting from what the plan asked for, and a done card shows the heaviest set in bold.

Checks: 104 unit tests, 10 of them a new `SetTableTest` on the commit rules and 5 an
`ExerciseRemovalTest` on the three-way delete decision. The release build was installed on the
Pixel 9 as an update over the debug build with the record intact, and the log-and-correct path —
mark done, reopen, correct, save again — was walked through repeatedly by the user.

## Release builds

`release` is signed with the **debug key**, deliberately: sharing the signature is what lets a
release install over a debug build as an *update* rather than demanding an uninstall, which would
take the training history with it. `optimization { enable = false }`, so R8 does not run and cannot
strip Room or kotlinx.serialization reflection; turning minification on is the moment to re-test
the database paths.

## Phase 5A and timer A/B/C (2026-09-22)

Done in one pass, because the timer work and the duration work are the same arithmetic: the
estimate a plan shows has to be the sequence the timer runs.

**The timer program was reshaped rather than extended.** `TimerProgram` had three scalar numbers
and derived `(setIndex, phase)` pairs from them, which stops being unique the moment a set contains
two sides or six pulses. It now holds a list of `TimerEntry` and flattens once into a list of
`TimerStep`, each carrying its exercise, round, set, side and rep; `TimerState` carries a single
`stepIndex` instead of a set index and a phase to look up. All 25 existing `TimerProgramTest` cases
passed unchanged afterwards, which is what says the single-exercise behaviour did not move.

`TimerProgram(sets = …, work = …)` survives as a companion `operator invoke`, so the ordinary
single-exercise construction still reads the way it did while the primary constructor takes the
entry list a circuit needs.

**One function builds the program and the estimate.** `PrescriptionProgram.of` turns a plan into a
program; `DurationEstimate.forPrescription` asks that program how long it takes. There is no second
formula that could disagree, and the assumptions (3 s a rep, 30 s an unstated set) are arguments
rather than constants buried in a branch.

**Duration went on the occurrence**, with a provenance flag, because an activity has no sets to
hang it from and a circuit exercise's share belongs to the exercise. `loggedEffort` joined it for
the same reason, read only where there are no sets.

**A one-off activity's identity is derived from its name** rather than stored in a table, so
repeated sessions group with nothing to keep in step, and "promote this to a library entry" stays
possible later without rewriting history.

**A circuit is a container plus real occurrences**, so it is loggable without the timer and counts
nothing of its own. Its time is computed once and divided between its stations, every segment
allocated exactly once, which is the invariant a dashboard will depend on.

**Documentation reconciliation.** This file previously described exact alarms, a `TimerAlarms.kt`
that no longer exists, `USAGE_ALARM` audio with a focus request, per-set immediate writes, and
separate planned and performed dates — all of them contradicted by later sections of the same
document. Current behaviour and history are now separated, and the stale claims are gone from the
first half and recorded as superseded in the second.

## Phase 4B, variations and the unlimited week list (2026-09-25)

- **Variations** — `exercise_variations`, with the tag unique per exercise and the plan its own
  prescription row. Occurrences gained `variationId` and `variationTagSnapshot`. Scheduling takes
  an optional variation; removing an unused exercise frees its variations' plans with it.
- **Modules** — `modules`, `module_entries` and `module_instances`, with `moduleInstanceId` and a
  position on occurrences and circuit instances. Scheduling cuts each entry through the same code
  as scheduling it alone (`placeExercise`, `scheduleRoutine`), so a module is only the group.
  Moving, ungrouping, taking out, removing and the stronger delete-with-log are all in the
  repository; the week folds members into their group only while they share its slot.
- **Slot ordering counts containers.** `nextOrderIndex` and `firstOrderIndex` looked at occurrences
  only, so something added after an empty group landed in front of it; they now take circuit and
  module containers into account.
- **`deletePrescriptionIfUnused` checks routine entries**, which it never did — a latent foreign-key
  failure — as well as the new variation and module-entry owners.
- **A copied placement joins no group.** Duplicating copied `circuitInstanceId` along with
  everything else, which would have made a duplicate a station of its source's circuit; it and the
  new module fields are now cleared on copy.
- **The week list for *Add to plan* has no end.** A lazy list of `Int.MAX_VALUE` rows, starting
  last week.
- **The picker is its own screen** (`LibraryPickerScreen`) with a segmented control, rather than
  options threaded through the library tab's screen.
- Schema **v1 → v2**, destructive in debug by design; `1.json` replaced by `2.json`.
- **Module planning metadata cut back to a description** (schema **v3**, `3.json`). Estimated
  minutes, target system, equipment and the effort/CNS and skin costs were specified by the phase
  plan and went unused; a module now carries only an optional purpose / description, trimmed, and
  stored as absent when blank.
- **`LibrarySeed`** now also seeds variations, four circuits (ordinary, all one side at a time, a
  repeater with a station that must wait, a zero-transition superset) and five modules, unless run
  with `-e planning false`.

**Checks run:** 160 unit tests pass (4 new for module grouping, 4 for the tag rule). Both APKs
build. `ModulesAndVariationsTest` (14 instrumented tests: copy isolation, tag rules, template edits
not reaching copies, group moves carrying logs, ungrouping, member moves, trained-module
protection, template deletion keeping logs, container ordering) **pass on the Pixel 9**, in the full suite of 63 run with
`scripts/device-tests.sh` against a backup taken immediately before
(`backups/pre-v2-20260925-173611/`, schema 1, integrity ok, 25 exercises / 8 occurrences).
After the v3 change the suite ran again (backup `backups/pre-v3-20260925-175404/`): 62 of 63 pass,
all 14 module and variation tests among them. The one failure,
`aCueDoesNotTakeAudioFocusFromWhateverElseIsPlaying`, failed at its own precondition — a phone call
held audio focus, so the test could not take it to begin with. Not yet re-run outside a call.

## Workouts and modules; fixes from a review (2026-09-26)

- **The library is *Workouts | Modules*.** Exercises and circuits are one alphabetical list; the
  separate Circuits and Modules screens are gone, their rows reused. *New workout* chooses Exercise
  or Circuit at the top of the editor. The week's picker has the same two tabs.
- **Circuit rows and rounds agree.** A station's form no longer shows the sets, set rest and total
  time the circuit overrides, and the review seeds one row per **round** rather than per station
  set.
- **A completed activity can be removed.** The dialog judged "logged" by set count alone, offered a
  plain removal, and the repository refused it with a message pointing at a logger action that
  does not exist. It now counts completion too and offers *Delete activity and log*; the move
  dialog applies the same test before offering *unscheduled*.
- **Circuit review asks the date** for an unscheduled circuit, with the logger's own card and
  picker, now shared (`TrainingDate.kt`).
- **Unavailable module entries** are flagged, replaceable, warned about before scheduling, and
  left out consistently — retired exercises and circuits included, which scheduling used to copy.
- **Module editor → *New circuit***, handed back through `CREATED_CIRCUIT_ID` like a new exercise.
- **Rendering:** a manual duration showed as nothing (operator precedence); a missing circuit
  spun on "Loading…" forever, in the detail and the review.
- **Timer:** the previous button is announced as *Restart this interval* or *Previous interval* by
  the same rule the transition uses; the cue explanation describes the interval on screen while
  one is counting, not the create form's numbers.

- **Circuits look like exercises.** A circuit has its own category (schema **v4**, snapshotted onto
  scheduled copies), chosen with the same chips as an exercise. Library rows and week cards are
  built like an exercise's: the category dot and name, no *Circuit* chip, the same background, and
  one line underneath — "3 rounds · Pull-up → Plank → Push-up", with the library's estimate at
  its end rather than beside the name.
- **One editor header** (`EditorTopBar`) for the exercise, circuit and module editors: the same
  title size, *Save* in the same place — the exercise editor's bottom button is gone — and the
  Exercise | Circuit switch in the header, so swapping kinds no longer shifts the screen.
- **"Timer finished"** sits on the timer screen itself rather than in a card.

**Checks run:** 164 unit tests pass (4 new). All three APKs build. On the Pixel 9, 65 of 65
instrumented tests pass, including the audio-focus cue test outside a call, a retired exercise
being flagged and left out of its module, and a circuit's category surviving save, duplication and
recategorisation of its template. Backup before the v4 wipe: `backups/pre-v4-20260926-004622/`.

## Before phase 5B: edit mode, retired variations (2026-09-26)

- **The week's edit mode**, owed since 4A. One position per top-level card, renumbered 0, 1, 2… on
  every nudge, so a card can no longer jump past a circuit or land behind a module the way the old
  per-occurrence *Move up / Move down* did. The landing is a pure function (`Planning.nudge`);
  crossing a day goes through the existing move code. The long-press *Move up / Move down* are
  gone.
- **Variations are retired, not deleted, once anything was cut from them** (schema **v5**): no
  longer offered, the tag free again, and past copies still showing their notes. One nothing came
  from is still deleted outright.

- **"Move to…" is gone** from every card's menu: edit mode moves cards, across days, and the move
  dialog went with it.
- **Plans live on their owners** (schema **v6**). The `prescriptions` table is gone; each owner
  holds its plan as a JSON column and logged sets no longer point at a plan. Orphaned plan rows —
  left behind by every deleted occurrence, circuit and module, and every edit — cannot exist any
  more, and an export has one less table to carry.

**Checks run:** 166 unit tests pass (7 new for the nudge landing, replacing 4 for the old
single-slot reorder). All three APKs build. On the Pixel 9, before the schema-6 change, 71 of 72
instrumented tests passed; the one failure was a wrong expectation in the new `WeekEditTest` (a
second nudge carried a module on to Wednesday, as it should), since corrected. **The suite has not
run on schema 6** — the phone was away.

## Phase 5B, part one: diary, export and restore (2026-09-26)

- **The diary**: `diary_entries`, `metric_definitions` and `diary_metric_values` (schema **v7**),
  its own `DiaryRepository`, defaults seeded on first use; a one-line summary per day.
- **Daily trackers** (schema **v9**, replacing the metrics): `trackers` and `diary_values`, each
  tracker a scale with its own range, a number with a unit, a checkmark or a comment. Each value
  row snapshots its tracker's definition, and so does the backup (`DiaryValueRecord`), which
  validates a value against its own snapshot. *Daily notes* is a full page (`ui/diary`) from the
  day's + menu, *Save* anchored at the bottom and the tracker editor in its ⋮; the ✎ and the
  dialog are gone.
- **The plan card's cog** in the exercise detail is a drawn vector (`ic_edit_plan.xml`, in the
  navigation icons' style) instead of the ⚙ character, whose look depended on the font.
- **Export and restore** in `data/backup`, apart from the training repository and reading the
  tables directly: `BackupFormat` (records and the mapping both ways), `BackupValidator` (pure,
  every refusal in words), `BackupService` (export in one transaction, safety copy, atomic
  restore). The *Backup & restore* screen drives it with the system file picker.

**Checks run:** 172 unit tests pass — 6 new for the file format and validation, including every
kind of row surviving the round trip unchanged. All three APKs build. On the Pixel 9, **77 of 77**
instrumented tests pass (backup `backups/pre-v7-20260926-105028/`), among them the new
`WeekEditTest` (6) and `BackupRestoreTest` (5 tests: a mixed record — one-off activity, circuit, module, skipped work,
manual, inferred and missing durations, unilateral loads, a tombstone, variations, the diary —
exported, restored over other data, and exported again to the identical file; the restored week
read back; unreadable and invalid files changing nothing; the safety copy restoring the record).
After the trackers moved to schema 9, **81 of 81** pass (backup `backups/pre-v9-20260926-112247/`),
the new four in `DiaryTrackersTest`: a renamed, re-kinded tracker leaves its recorded days as they
were; a rescaled one keeps values outside its new range; a retired one stays on its days, ordered
last; a value its tracker cannot hold is never stored. 172 unit tests pass.

## Phase 5B, part two: plan import (2026-09-26)

- **Plan files** (`data/plan`): `PlanFormat` (the file, parsed strictly — unknown keys are errors —
  but with comments and trailing commas allowed), `PlanCheck` (pure: resolves every name against
  the file and a `LibraryIndex`, normalises each plan to what its mode reads exactly as the editor
  does, and returns problems, warnings, a preview and the resolved plan), `PlanImporter` (writes a
  resolved plan in one transaction through `TrainingRepository`; *Replace all* writes a safety
  copy and uses the new `BackupService.clearAll`).
- **Two formats, one screen**, rather than one format with optional ids as first proposed: the
  backup already restores exactly, and a plan written by hand wants names, units as typed and no
  snapshots. A backup opened as a plan is pointed to *Restore*.
- Decisions: the mode is chosen at import (*Add* / *Replace plans*); in *Add*, a file's definition
  **updates** the library item of the same name; weeks are literal, no repeat; it lives on the
  Backup & restore screen, so it works on release builds.
- **Revised the same day: an import never deletes a record of training.** *Replace all* (which
  wiped everything, logs included) became *Replace plans*: only planned cards with nothing logged,
  done or skipped go, library items the file does not name are retired or deleted through the new
  `TrainingRepository.retireExercise` (which, unlike `removeExercise`, leaves every copy alone)
  and the existing template removals. A **From today / Include past** choice (default from today)
  bounds both what is planned and what is cleared. Also removed a stale comment claiming a moved
  item leaves its sets behind: every move re-dates logged sets with the item.
- **The seed list is now a plan file**, `test-library.json`, and `LibrarySeed` imports it (`-e
  planning false` still leaves out circuits and modules).
- The spec is `docs/plan-format.md`.

**Checks run:** 182 unit tests pass, 10 new in `PlanCheckTest` (the test library resolves cleanly;
format and version refusals; comments allowed and misspelt keys refused; names matched across case
and spacing; references resolved file-then-library, file only when replacing; unlisted variations
still resolving; plans trimmed to their mode with warnings; every kind of mistake named; ambiguous
library names refused; weeks into ordered slots). `PlanImportTest` (5: everything lands where the
file says; adding updates by name and leaves planned copies alone; replacing starts over after a
safety copy; a file with a mistake changes nothing; a backup is pointed to Restore) passes 5 of 5
on the Pixel 9. Its first run caught a wrong expectation, not a bug: a module scheduled on a day
brings its circuit with it, so the week holds two circuits.

After the revision: 184 unit tests pass (2 more in `PlanCheckTest`: replacing matches definitions
but not references to the library; from today, the past is left out and counted but still
checked). `PlanImportTest` is now 6 — the replace test became "replacing clears only plans that
have not happened" (a logged and a skipped card kept with their sets, the library the file's, a
kept exercise's id unchanged) and "from today leaves the past alone" is new. On the Pixel 9 the
whole instrumented suite passes, **87 of 87**.

## Loads are signed; assistance is gone (2026-09-28)

- **`MeasurementMeaning.ASSISTANCE` removed.** Assistance is added load below zero: −15 kg is a
  band taking 15 kg off. This also fixes a bug where an assisted exercise's "heaviest set" was its
  *most* assisted, i.e. easiest, set; with one signed scale the largest number is the hardest.
- **Entering a negative:** on added-load exercises, a minus typed anywhere in a load field flips its
  sign (`loadInput`), and the max-load field in the logger and in circuit review has a **±** key
  (`SignKey`), since decimal keyboards may hide the minus. Total loads stay unsigned. Loads read
  "60 kg", "+10 kg", "−15 kg" (`PrescriptionSummary.load`, now shared with the logger). The
  exercise editor says what a negative means under *Load type*.
- **Schema v10, no migration**: stored data could name the removed meaning, so debug builds wipe
  on first open, and the test library (whose three assisted exercises are now `ADDED_LOAD`) is
  re-seeded. Plan files accept `TOTAL_LOAD` and `ADDED_LOAD`.

**Checks run:** 188 unit tests pass (`LoadInputTest`, 4 new: the sign flips and flips back,
total loads never go negative, the ± key, signed display; the payload test now round-trips a
negative added load).

## Phase 5B closed: the stable baseline (2026-09-28)

- **Upgrades preserve data.** The debug-only destructive fallback is gone; `MeleteDatabase.build`
  adds `MeleteMigrations.ALL` (empty at the baseline, schema 10) and nothing else. `MeleteMigrations`
  says what a schema change requires. `MigrationTest` (2) migrates every exported schema from the
  baseline and opens a baseline database with its rows; `SchemaBaselineTest` (2, JVM) pins the
  identity of every released schema.
- **Backups stay readable.** `backup-v1.json`, written once from the restore test's mixed record
  (now with two negative added-load sets, in the following week) by the gated
  `BackupRestoreTest.writeCompatibilityFixture`, is committed as the compatibility fixture.
  `BackupFixtureTest` (3, JVM) and `BackupCompatibilityTest` (1) keep it restoring.
- **Roadmap** replaced with the post-5B sequence: 6A overview, 6B history and basic graphs, 6C
  month view, 7 profile and benchmarks, 8 progression analysis, 9 remote and web, 10 appearance.

**Checks run:** 193 unit tests pass; on the Pixel 9 the instrumented suite passes, **92 of 92**.
Melete Debug, now without a fallback, opens its seeded schema-10 database.

## UI pass (2026-09-28)

From `docs/ui-ux-handoff.md`, as part of 1.0 — no version bump, no schema change, backups and plan
files untouched. Menu · Calendar · Timer · Dashboard tabs (Menu: Profile, Benchmarks, Library,
Import / export, Settings; the drawer first built was replaced); week swipe; folding
modules; comma search in the library and the picker; library pages for circuits and modules;
timing and logging an exercise straight from the library; *Last logged*; anchored save bars and
compact previous results; circuit pages without allocated station times or counting prose;
readable recovery-copy dates. Decisions taken with the user: a library attempt is a real
unscheduled copy (removed again if the logger is left unsaved); modules start folded except
today's; the Library is a focused screen with Back rather than a tab.

Checks: 209 unit tests pass after the second round (new: comma search, `FN` prefixes, `!circuit`, `LastLoggedPicker` choice and formatting — same
variation, dates over edit order, L/R, zero and negative added load, timed work, activities —
and recovery-copy labels). All 93 instrumented tests passed on the Pixel 9 after the first round,
including a new one pinning that an attempt copy is only ever discarded while untouched; the
second round (tabs, swipe, search tweaks) was not run on the device suite. The user checked the
first round's drawer, folding, attempt copies and save bar by hand.

**Still deferred**: the running-timer polish (next, before 6A), Profile / Benchmarks / Settings
content, Dashboard content (6A), Month and its title chevron (6C), search negation beyond
`!circuit`, the full-screen import preview.
## Phase 7A — benchmarks (2026-09-28)

Brought forward ahead of 6A because this is the user's baseline week for measuring them; 7 is
split into **7A benchmarks** (this) and **7B profile and settings**. The spec agreed with the user
is in `ui-ux-handoff.md`, including decision A: results are records, not training.

Schema **11**, the first change since the baseline: two new tables, `benchmarks` (with `goal`) and
`benchmark_results` (with `textValue` and `bodyweightPercent`) (foreign key RESTRICT, so a benchmark with results cannot be deleted from under
them), created by `MIGRATION_10_11` with nothing existing touched; `11.json` exported and pinned in
`SchemaBaselineTest`. Backup format **2** adds `benchmarks` (with nested results); a version 1
file reads as having none, and the version went up so an older app refuses a file holding
benchmarks rather than restoring it without them.

Checks: 218 unit tests pass (new `BenchmarkStandingTest`: latest by test date not entry order,
best keeps its date and the first date on a tie, lower-is-better, separate L/R bests and dates,
zero and assistance, other units excluded from best, formatting, the backup JSON round trip, a
version 1 file, and validator refusals). On the phone, with Melete Debug updated, 14 device tests
pass: `MigrationTest` (every schema migrates; a baseline database opens through the app's builder with
its rows, then records a zero benchmark), the new `BenchmarkTest` (recording creates no
occurrence, session or set; results keep their recorded terms; hiding keeps results; only an
unused benchmark deletes; correction; export and restore round trip), `BackupCompatibilityTest`
(the frozen version 1 fixture still restores and exports back the same) and `BackupRestoreTest`.
The full device suite was not run. **Release has not been updated**: installing it runs
`MIGRATION_10_11` on the real record, which the tests above cover; take an in-app export first.

Then, same day, before release: text results, goals and reported bodyweight % were folded into
schema 11 rather than a schema 12, since no release had it yet (11.json re-exported and re-pinned;
Melete Debug, which had opened the first draft, had its two empty benchmark tables dropped and
was set back to 10 by a one-off instrumentation step, then migrated). Plan files gained
`benchmarks`, which is how the user's historical results (five benchmarks, twenty results, kept
out of the repository in `backups/`) are loaded. 226 unit tests pass (new `PlanBenchmarkTest`);
on the phone `MigrationTest`, `BenchmarkTest` (now with an import-twice test), `PlanImportTest`,
`BackupCompatibilityTest` and `BackupRestoreTest` pass, 21 tests; Melete Debug opens its own
migrated database without error. `DeviceActions` gained `-e action cue`, which plays the timer's
finish cue on the phone and touches no data.

## Phase 6A — dashboard (2026-09-28)

Built to the handoff and the user's pinned details (metric-driven shares and ordering, recorded
time only, "≈" only on an exercise's row, current partial periods included, no averages, no
filtering or doughnut). Checks: 231 unit tests pass (new `DashboardStatsTest`: ranges and their
partial periods, weekly or monthly bars, all-time span, counting with and without durations,
circuit stations, shares and ordering by metric, no-category rows, the inferred mark, hour
formatting); `DashboardQueryTest` on the phone (completed only, in range, first completed date).
Melete Debug updated; not checked by eye.

## Next step

**Phase 6A — the overview.** Before the real plan goes into release: walk the hand checklist
above and tick what is confirmed, and follow *Starting from nothing* in
[`plan-format.md`](plan-format.md) to empty the release app of its test data.

Owed alongside it:

- **Everything in *What still needs a phone*.** Much of it has been used by hand on the phone
  (5A, the timer extensions, variations, modules) but the boxes have not been ticked; tick the
  ones confirmed. `LibrarySeed` refills a wiped debug database by importing the test
  library plan file.
- The 4A acceptance scenarios end to end. The instrumented suite exercises the database, not the
  screens.
