# Plan files

A plan file is a library and a block of weeks written by hand, imported from **Backup & restore →
Import a plan**. Everything is referred to by **name**: there are no ids, timestamps or snapshots
to maintain. (A *backup* is the other kind of file — the whole record with its ids, for putting a
phone back exactly. It goes through *Restore*.)

The test library, `app/src/androidTest/assets/test-library.json`, is a complete example.

## The file

```jsonc
{
  "format": "melete-plan",   // required, exactly this
  "version": 1,              // required
  "exercises": [],           // all four lists are optional
  "circuits": [],
  "modules": [],
  "weeks": []
}
```

JSON with two allowances for writing by hand: `// comments` and trailing commas. Keys are
exact and lowercase; **an unknown key is an error**, so a misspelt `"Monday"` or `"rest"` is
reported instead of silently ignored. Enum values (`mode`, `category`, `meaning`, `effort`) are
case-insensitive, and spaces or dashes count as underscores: `"very hard"` = `"VERY_HARD"`.

Names are matched ignoring case and repeated spaces, so `"pull-up"` finds `"Pull-up"`.

## Exercises

```jsonc
{
  "name": "Max hangs 20 mm",          // required, unique in the file
  "mode": "DURATION",                 // required
  "category": "FINGER_TRAINING",
  "unilateral": false,
  "unit": "kg",
  "meaning": "ADDED_LOAD",            // required when there is a unit
  "description": "Half crimp, both hands.",
  "notes": "Beastmaker 1000, middle edge",
  "plan": { "sets": 5, "seconds": 10, "restSeconds": 180, "effort": "HARD" },
  "variations": [
    { "tag": "END", "notes": "Speed first, then load.", "plan": { "sets": 6, "seconds": 10, "restSeconds": 90 } }
  ]
}
```

| Field | Values |
| --- | --- |
| `mode` | `REPETITIONS` (or `reps`) · `DURATION` (or `timed`) · `REPEATERS` · `ACTIVITY` |
| `category` | `OPEN_CLIMBING` · `BOARD_CLIMBING` · `STRUCTURED_CLIMBING` · `FINGER_TRAINING` · `STRENGTH_CONDITIONING` · `FLEXIBILITY` · `OTHER_ACTIVITY`; omit for none |
| `meaning` | `TOTAL_LOAD` · `ADDED_LOAD` · `ASSISTANCE` |
| `variations[].tag` | capitals or digits, at most 4 (`pwr` is read as `PWR`) |

An activity has no sides and no load: `unilateral`, `unit` and `meaning` are dropped with a
warning.

`variations` absent leaves the library's variations alone. Listed ones are added, or updated when
the exercise already has that tag. Variations the file does not list are never removed.

## Plans

Every `plan` — an exercise's default, a variation's, or one given where something is placed —
has the same fields, in the units you type them in:

| Field | Meaning | Read for |
| --- | --- | --- |
| `sets` | number of sets; default 1 | all but activities |
| `reps` | target reps | `REPETITIONS` |
| `seconds` | length of one timed set | `DURATION` |
| `repeater` | `{ "reps": 6, "workSeconds": 7, "restSeconds": 3 }` | `REPEATERS` |
| `restSeconds` | rest between sets | all but activities |
| `sideSwitchSeconds` | time to change sides; omit for the default, `0` = back to back | unilateral exercises |
| `plannedMinutes` | how long it should take, overruling the estimate | all but activities |
| `minutes` | the activity's length | `ACTIVITY` |
| `effort` | `VERY_EASY` · `EASY` · `MODERATE` · `HARD` · `VERY_HARD` | all |

A field the mode does not read is dropped, and the preview lists it under *left out*, so a file
cannot hold a plan the editor could not have made. There is deliberately no load: a plan fixes the
shape, the day decides the weight. An omitted value is stored as absent, never as 0.

## Circuits

```jsonc
{
  "name": "Pull + core",
  "category": "STRENGTH_CONDITIONING",
  "rounds": 3,                 // default 1
  "transitionSeconds": 30,     // between stations; default 0
  "roundRestSeconds": 120,     // after each round; default 0
  "stations": [
    { "exercise": "Pull-up", "plan": { "reps": 5 } },
    { "exercise": "Plank", "variation": "A" },
    { "exercise": "Push-up" }
  ]
}
```

A station is an exercise, with an optional `variation` and `plan`. Without a `plan` it takes the
variation's, else the exercise's default. `sets` in a station's plan is ignored: the rounds decide.

## Modules

```jsonc
{
  "name": "Fingers + core",
  "description": "Finger strength first, then pulling and core.",
  "entries": [
    { "exercise": "Max hangs 20 mm", "variation": "END" },
    { "circuit": "Pull + core" },
    { "exercise": "Couch stretch", "plan": { "sets": 3, "seconds": 60 } }
  ]
}
```

An entry is an exercise (with an optional `variation` and `plan`) or a `circuit`.

## Weeks

```jsonc
{
  "weekStart": "2026-10-05",    // a Monday, YYYY-MM-DD
  "unscheduled": [ { "exercise": "Couch stretch" } ],
  "monday":    [ { "module": "Fingers + core" } ],
  "wednesday": [ { "exercise": "Back squat", "variation": "STR" }, { "circuit": "Leg circuit" } ],
  "saturday":  [ { "activity": "Outdoor bouldering", "minutes": 240 } ]
}
```

- Days are `monday` … `sunday`, plus `unscheduled` for the week's undated area; all optional.
- Weeks are written out one by one; each `weekStart` once.
- An item is exactly one of:
  - `{ "exercise": "…" }`, with an optional `variation` and `plan` for this placement only
  - `{ "circuit": "…" }`
  - `{ "module": "…" }`
  - `{ "activity": "…", "minutes": 90 }`: a one-off, with no library entry; `minutes` optional
- Items go in the order written, after anything the day already holds.

## References

An `exercise`, `circuit` or `module` named anywhere resolves against the file first, then — when
adding — the library. So a file can plan weeks with nothing but names of things already saved.
When replacing, only the file counts, since the library is about to become the file's. Either
way, a definition in the file matches a library item of the same name and **keeps its identity**,
so its history still groups with it. A name two library items share cannot be used until one is
renamed.

## Importing

**An import never deletes a record of training.** Anything logged, marked done or marked skipped
stays exactly as it is — a circuit or module with any of that in it is kept whole — and so do the
daily notes and trackers.

Pick the file, then choose a mode:

- **Add** keeps everything. A definition in the file **replaces** the library item of the same
  name, from now on — weeks already planned and logged keep their own copies. Variations the file
  does not list stay. Weeks are planned after what is there.
- **Replace plans** clears planned work that has not happened, takes library items the file does
  not name out of the library (hidden rather than deleted when they have history; variations the
  file does not list likewise), then builds from the file. A recovery copy is saved first, listed
  on the same screen.

And how far back it reaches:

- **From today** (the default): nothing dated before today is planned — the preview counts what is
  left out — and replacing clears only from today on. A week's unscheduled area counts as today
  while the week has a day left.
- **Include past**: the file applies as written, and replacing clears unhappened plans in the past
  too.

Before anything is written you see what the file adds and updates, and what it leaves out and
why. A file with any problem is refused whole, with every problem listed, and the phone is left
exactly as it was; the import itself is one transaction.
