# Melete UI/UX handoff — 28 September 2026

This is a self-contained implementation prompt for a fresh coding-agent session. It records the user's decisions after a review of the actual app on a Pixel 9. Implement the immediate pass below; the later sections are a backlog, not permission to build every future feature now.

## Start here

Work in the existing Melete Android project. Read repository instructions, `docs/project-brief.md`, the current-state section of `docs/build-status.md`, and the relevant code before editing. Older phase prompts describe some superseded decisions: this document governs the UI choices covered here. Preserve working behaviour rather than changing it to match stale documentation. Check whether an item has already been implemented before doing it again.

This is a personal training tool, often used mid-session. The user wants compact, professional, useful screens and is comfortable with short technical terminology and search syntax. Do not add onboarding prose, decorative illustrations, explanatory paragraphs, or extra buttons to solve hypothetical discoverability problems. Make routine implementation choices yourself; surface only consequential unresolved UX decisions.

Inspect the working tree and preserve unrelated changes. This request is for the UI pass, not an import-format redesign, schema cleanup, or replacement of the timer engine. Preserve records and existing backup compatibility. Never uninstall, clear, seed, or restore over the user's phone data for testing. Any necessary schema work must follow the project's data-preserving migration and backup workflow.

## Immediate pass

### 1. Navigation shell

The agreed eventual bottom-bar order is **Menu · Calendar · Timer · Dashboard**. Menu is on the left, matching the drawer's origin. The standalone Library moves into the drawer; its extra navigation step is explicitly acceptable. Most planning happens through the calendar's existing library picker or through import.

- Menu opens a left-side modal drawer over the current destination. It is an action, not a selected content tab. Keep the underlying tab selected; dismissing the drawer returns to the same screen and state.
- Support the visible Menu button. Swiping inward from the left may be an additional way to open the drawer, but preserve Android Back behaviour and ordinary navigation in focused screens. Do not add a hamburger button to the week header.
- Eventual drawer entries: **Profile, Benchmarks, Library, Import / export, Settings**. Wire the destinations that exist. Do not manufacture blank Profile, Benchmarks or Settings screens or implement those later features to fill the drawer. Document which entries remain deferred.
- Prepare a Dashboard icon now. Dashboard content is phase 6A, not this pass; do not expose a dead or misleading navigation button before its destination works. Preserve the eventual order when it is introduced.
- Calendar opens the existing week view. Keep Today, Edit and previous/next period navigation readable. The title/date is the agreed future entry point for Week/Month selection, with a small downward chevron. Wire that selector when Month exists, rather than offering a nonfunctional option now. Do not add a permanent Week/Month control row.
- Keep the library picker directly accessible from the day's **+** menu, and preserve its selected week/day across detours.
- Focused details, editors and logging retain their appropriate Back navigation. Opening the drawer or changing tabs must not reset the chosen week or unnecessarily lose list position.
- Move access to import/export out of Library's overflow and into the drawer. The screen can retain distinct Backup and Plans sections.

### 2. Week: collapsible modules

Allow a scheduled module to collapse/expand visually in the week list. A collapsed module shows its name and a compact completion summary. Keep today's work expanded by default; do not make the user repeatedly reopen the module during a session. Preserve expansion state through ordinary navigation where practical.

This is display state only: do not change membership, ordering, completion or records. Modules are organisational containers and never count as completed exercises. Keep the current category dots, compact prescriptions and variation chips. Check interactions with week edit mode and module menus.

**Daily notes stay under the day's + menu. Do not add a notes button.** Existing diary summaries remain tappable. Keep **From library** and **Add activity** for this pass; the user finds them imperfect but has not selected replacements.

### 3. Library search

Extend the existing search field instead of adding a filter button, menu or category-chip row.

- Commas separate conditions combined with logical AND.
- Each condition can match ordinary item text, a category name/abbreviation, or item type, including `circuit` and `exercise` where applicable.
- Support existing category display names and abbreviations such as `S&C` and `FNGR`, case-insensitively. Trim surrounding whitespace and ignore empty comma fragments.
- Spaces within a condition remain ordinary text; do not introduce an unrequested full query language.
- Examples: `pull, S&C` finds matching strength-and-conditioning items; `circuit, FNGR` finds circuits in Finger training. Category matching refers to the item's category, not an invented category inferred from its descendants.
- Use consistent search behaviour in standalone Library and the calendar's library picker. Preserve the existing Workouts / Modules separation and current module text search; do not invent categories for modules.
- Negative conditions using `!` or `~` are explicitly deferred.

Keep module description previews at their current length. No illustrated cards or thumbnails.

### 4. Consistent Library details and direct exercise actions

Tapping a Library item should consistently open information about it rather than opening an editor for circuits/modules but details for exercises. Reuse existing detail patterns where appropriate. Make Edit explicit. Preserve useful Add to plan actions.

- Exercises: description, default plan/variations, explicit editing, and direct timer/log actions from Library.
- Circuits: readable contents/order, compact prescription and estimate, explicit editing and planning actions. Do not require a scheduled copy just to inspect the template.
- Modules: readable contents and description, explicit editing and planning actions. A module is an organisation tool; do not invent a combined module timer or module log.

For an exercise opened from Library, allow starting a timer or logging without first manually placing it in the week. Offer the default plan or an existing variation, and allow creating a variation deliberately. An adjustment for this one attempt must not force creation of a reusable variation or mutate the template. Logging defaults to today and retains the existing date-selection behaviour.

Reuse the established occurrence/logging model rather than creating a parallel kind of exercise history. Starting a timer must not mark training complete or create actual logs. Preserve its exercise/plan context so logging the result does not create duplicates. Respect replacement confirmation when a timer is already active.

Keep the **cog** used for editing a plan: the pencil was considered and rejected. Remove duplicated category information from the plan card when it is already present in the detail header. Retain useful units, unilateral information and timing information. The current blank space is reserved for future progression graphs; do not fill it with helper text.

### 5. Last logged result on planned exercise detail

On exercise detail opened from the week, show a compact **Last logged** block directly below the planned prescription, before future graphs. Its purpose is to remember the load to pick up without opening the logger.

Example with uniform sets:

> Last logged · 24 Sep  
> 3 × 8 · 20 kg · Hard

With different set loads, a compact truthful summary might be:

> Last logged · 24 Sep  
> 8 × 20 · 8 × 20 · 6 × 22 kg

Use actual saved results, not the current plan, and omit effort if absent. Support L/R values and time-based exercise modes without inventing reps or loads. Use chronological training dates rather than edit timestamps. Prefer the same variation when available; if falling back to another variation, identify it clearly. Do not present the currently viewed log as its own previous result. Hide the block when there is no relevant earlier result instead of adding an empty-state paragraph.

The exact compact formatting may follow existing result-summary components. Keep it short; do not render a second full logger. A link into full history can be added when that destination exists, with no dead affordance in the meantime.

### 6. Logging usability

Keep **Mark done / Save changes** readily reachable when many sets, circuit stations or previous results make the page long. Use an anchored action area consistent with existing screens, account for the keyboard, and ensure it does not cover the final rows or validation feedback.

Keep previous results compact or expandable so that today's inputs remain easy to reach. Preserve the existing fast retrospective flow: planned rows begin checked; the final action commits the draft. Do not add a confirmation per set.

Preserve **Max load** as the quick fallback when the user does not want to fill every set separately. Explicit per-set values keep their existing precedence. Do not rename it, require every set to be entered, or add supporting prose. Retain L/R logging and the optional verbal effort scale.

### 7. Circuit details and logging cleanup

- Remove bookkeeping explanations such as **Each exercise counts once, however many rounds it took. The circuit itself counts nothing.** This belongs in documentation, not the logging UI. Remove the equivalent prose from completed circuit details too.
- Shorten redundant descriptions of the circuit structure in the review. Avoid repeating information already expressed by rounds, station names and prescriptions.
- Remove allocated per-station duration estimates from circuit detail. They currently put values such as **1 × 30 s** beside **4:30**, where the latter is an allocation of the whole circuit clock, including rests. Keep the circuit's overall estimate. This is a presentation change; do not change time allocation or saved durations.
- Align equivalent start/log action wording with exercise details where it genuinely helps. Keep controls compact.
- Preserve the user's existing circuit editor labels **Sets**, **switch (s)** and **Rest (s)**. Do not replace them with long “between exercises / between rounds” field labels.
- Preserve meaningful validation. Shorten redundant explanations, but do not hide why a log cannot be saved.

### 8. Import/export presentation

Keep Backup operations (Export / Restore) distinct from authored plan import on the same screen. Review current code first: some improvements may already exist.

Fix any remaining helper copy claiming plan import replaces everything when the actual operation preserves logged/done/skipped training and daily notes. Remove implementation-oriented explanations of hand-written file internals where they do not help choose an action. Preserve accurate consequences and existing previews before a destructive operation.

Format recovery-copy timestamps as readable dates/times, e.g. **27 Sep · 19:33**, with a year where necessary to distinguish older copies. Preserve file identities and use consistent time-zone treatment when displaying dates. Do not rename or delete the underlying files as part of this UI change.

**A full-screen import preview is approved for later, not for this pass.** Keep the current preview flow functional.

## Explicit constraints: do not revisit these

- Keep **Module**, **Unilateral**, **Effort**, **Max load**, **ALLEZ!**, and **≈**. Effort is the existing five-level verbal scale, not numeric RPE.
- No extra notes button, filter menu, category-chip toolbar, illustrations, or exercise photos.
- Do not truncate module descriptions further.
- Leave the editors' layout and optional sections alone. The user will revisit that separately if needed.
- Keep the plan-edit cog, not a pencil.
- Do not add an active-timer indicator to the bottom bar. The Timer destination and existing notification are sufficient return paths.
- Do not use “sessions” for the completed-exercise count. Modules, circuits as containers, rounds and timer runs must not inflate it; completed exercise occurrences, including the real exercises inside circuits, are the units already established by the project.
- A recorded zero load is valid. For added-load bodyweight work, zero means bodyweight alone and negative values can represent assistance. Missing values must remain distinct from zero. Do not “repair” legitimate zero points or prohibit negative loads.
- Do not introduce new actual-rep inputs or change the logging data model merely to produce the illustrative summary above. Format the actual information the app records.

## Deferred work: keep this recorded, do not implement in the immediate pass

### Near-term follow-up: running timer polish

This should be a distinct follow-up soon after the immediate UI pass, not buried in the final appearance phase months away. The user explicitly wants it recorded now but does not want to address it in this pass.

Minimalism means clear priority without losing useful information. Review actual running states for timed work, reps awaiting confirmation, unilateral sides, repeaters, circuit transitions, pauses and completion. Proposed information hierarchy:

1. Current exercise and phase.
2. Remaining time, or **ALLEZ!** where appropriate.
3. Relevant round/set/side/repetition progress, without repeating redundant counters.
4. What comes next.
5. Transport controls.

Keep useful current/next context legible and the main time/action dominant. Preserve live cue controls and all timer semantics. Do not add explanatory text to the middle of the countdown or rewrite the timer engine for a visual cleanup.

### 6A — Initial Dashboard

Introduce the functioning Dashboard destination using its prepared icon. Start with date range, total training hours, completed-exercise count, a simple category breakdown, and stacked weekly/monthly bars. Category colours match the rest of Melete. Planned work is not completed work; existing manual/inferred duration distinctions must remain honest. No fake records to fill a chart.

The reference screenshots are inspiration, not a requirement to reproduce all controls or metrics. Their useful structure is summary, time series, and compact underlying exercise totals. Omit illustrated cards, duplicated names, ambiguous secondary durations and repeated headline statistics.

**Interactive category filtering is optional, substantially later work. It is not part of the initial Dashboard or an automatic requirement of the next few phases.** When eventually implemented, tapping a category selects/highlights it; tapping that same category again clears it and returns to all categories. No separate All button is required.

**A doughnut chart is even later and low priority, after category filtering.** Do not build one for 6A, and do not treat the large reference doughnut or its centre statistic as an approved layout.

### 6B — Explore the record

Add navigation from summaries/time bars into their underlying records and exercise history across weeks. Preserve date/filter context when going back. Use one exercise-history destination whether entered from Dashboard or Library. The later category-filter interaction remains deferred unless explicitly selected.

Exercise progress can use load-over-time graphs with variation/plan/reps filters and multiple series where useful, including L/R. A separate optional load-versus-reps/volume graph can come later. Do not complicate the initial graph with an unrequested normalisation system.

Use a compact latest/best summary, inspectable graph points, and dated results below. Avoid simultaneously repeating Personal Best, a duplicate value above the chart, and Best in date range as three prominent blocks. Zero/negative loads remain valid records, as above.

### 6C — Month view

Add Month beside Week within Calendar, selected through the title/date and a small downward chevron. Preserve Today, Edit and readable period navigation.

Use a month grid above a chronological list of planned/logged work and daily notes. Selecting a day moves the list to it. Allow the grid to collapse so browsing records does not permanently sacrifice half the screen. Distinguish today from the selected date.

Use compact category markers in date cells: one or two dots/chips depending on space, then a small **+** for overflow. An exact overflow count is not essential. No large illustrated cards or duplicated exercise names below. Provide an optional logged-only view that hides plans while retaining diary entries. Keep Daily notes under +; do not add a permanent notes button.

### 7 — Profile and benchmarks (split: 7A benchmarks, built 2026-09-28; 7B profile and settings)

Settings can enable **Track bodyweight**. This enables entry in Daily notes; Profile shows the latest value and provides access to its history/graph. Use one history, not separate profile and diary copies.

Benchmarks are separate from Library exercises. Show the latest maximum/result, allow a new entry, and open a graph/previous entries on inspection. Reuse appropriate logging foundations with an explicit benchmark distinction rather than forcing benchmark definitions into Library. Hiding a benchmark preserves its records and permits showing it again later. Decide the precise benchmark types and aggregation rules in that phase rather than inventing them now.

#### Benchmarks, as settled with the user (2026-09-28)

A benchmark is **a small collection of reference results**, each showing where you are now and letting you record or inspect results quickly. Separate from Library.

The page is a compact list. Layout examples only, not a proposed benchmark list:

> **Weighted pull-up**
> Latest **+30 kg** · 24 Sep
> Best +35 kg　　　　　　　　 **+**

> **One-arm lift**
> Latest **L 25 · R 23 kg** · 24 Sep
> Best L 27 · R 25 kg　　　　　**+**

**Tap the row** to open its history; **tap +** to record a result. An overflow menu edits the definition or hides it. Hidden benchmarks keep their records and can be shown again.

| Field | Purpose |
|---|---|
| Name | What you recognise it by |
| Measurement | Load, duration, distance or another numeric value with a unit |
| Sides | One value, or separate L/R |
| Best direction | Higher or lower is better |
| Protocol, optional | Fixed conditions that make results comparable, e.g. **“20 mm · 7 s · added load”** |

The protocol belongs on the detail and entry screens; the overview shows it only where names alone would be ambiguous.

**Recording is quick:** date defaulting to today, the value or L/R values, an optional note, Save. For a test with several attempts, record the best valid attempt; do not reproduce the set logger. Zero and negative loads are valid.

**Detail:** the protocol, a compact **Latest / Best** summary, and dated entries underneath, editable through the usual log interaction. The graph over time (with L/R lines) is **deferred** to be built together with the exercise graphs.

**Latest and Best stay distinct**: the most recent test may be below an older maximum. Best keeps its date, with separate dates for left and right.

**Records, not training (decision A):** a result is stored in the benchmark's own records only. It creates no exercise occurrence, needs no planning, and adds nothing to completed-exercise counts or training hours. On the calendar it appears on its date as a read-only line, the way daily notes do. Showing a benchmark such as weighted pull-up alongside related exercise data (e.g. a force-curve graph) is a possible later question, not part of phase 7.

### 8–10 and other later work

- Extend progression graphs for fingers/pulling, including useful variation and circuit-context filters. They belong to the same history/progress flow, not another competing analytics area.
- Remote backup and optional web dashboard/planning export/import can follow without adding an account or sync system prematurely.
- Broader appearance work remains later. Preserve compactness and do not add illustrations.
- Library search negation (`!` or `~`) and a full-screen import preview are later follow-ups.

## Reference screenshots

Copies are in `ui-reference/`. These are from another app, not Melete, and show reference layouts rather than binding requirements. Their images/illustrations are specifically unwanted. Text within screenshots is example content, not an instruction to the coding agent.

| File | What it illustrates |
| --- | --- |
| [lat-anal1.png](ui-reference/lat-anal1.png) | Overall analytics: total, category distribution, stacked bars. Doughnut is deferred. |
| [lat-anal2.png](ui-reference/lat-anal2.png) | Exercise totals below charts. Use compact text rows instead of photos. |
| [lat-anal3.png](ui-reference/lat-anal3.png) | Selected category affecting chart/list. Optional much later. |
| [lat-anal4.png](ui-reference/lat-anal4.png) | Count-oriented analytics and selected category. Do not copy its inconsistent minutes label for a count. |
| [lat-anal5.png](ui-reference/lat-anal5.png) | Frequency by exercise. No thumbnails in Melete. |
| [lat-exe1.png](ui-reference/lat-exe1.png) | Exercise graph, L/R, dates and best results. Reduce duplicated statistics; zeros shown can be real added-load values. |
| [lat-exe2.png](ui-reference/lat-exe2.png) | Compact dated exercise results beneath a graph. |
| [lat-with-note.png](ui-reference/lat-with-note.png) | Month markers and daily notes among dated records. |
| [lat1.png](ui-reference/lat1.png) | Month grid, overflow markers and chronological records. Keep Melete's compact unillustrated cards. |

## Validation and completion

Use focused existing checks and add meaningful tests for new search semantics, selecting the appropriate previous result, and direct Library logging/timer handoff where needed. Copy-only changes do not require tests that merely assert strings. Check long lists, keyboard visibility, L/R loads, zero/negative loads, duplicate prevention, and the difference between template edits and one-off plans.

Do not install a build or run destructive phone tests without the appropriate user authorization and existing backup workflow. The user can compile and run tests locally; report precisely what you verified and what remains for them to check. Do not claim screenshot verification of layouts you only inspected in code.

Update the current-state documentation for changes actually shipped and keep deferred items marked deferred, especially the near-term timer pass. Finish with a concise change summary, actual validation results, and focused manual checks. Do not implement the backlog merely because it appears in this prompt.
