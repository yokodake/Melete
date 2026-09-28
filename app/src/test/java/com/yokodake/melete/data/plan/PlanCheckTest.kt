package com.yokodake.melete.data.plan

import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PlanCheckTest {

    private fun plan(json: String): PlanFile =
        PlanJson.decodeFromString(PlanFile.serializer(), """{ "format": "melete-plan", "version": 1, $json }""")

    private fun resolve(json: String, library: LibraryIndex = LibraryIndex.EMPTY) = PlanCheck.resolve(plan(json), library)

    private fun PlanResolution.hasProblem(part: String) = problems.any { part in it }

    private val library = LibraryIndex(
        exercises = mapOf(
            "pull-up" to IndexedExercise("ex-pull", "Pull-up", ExerciseMode.REPETITIONS, false, mapOf("A" to "var-a")),
        ),
        circuits = mapOf("pull + core" to "circ-1"),
        modules = mapOf("strength day" to "mod-1"),
    )

    @Test
    fun `the test library is a sound plan`() {
        val text = File("src/androidTest/assets/test-library.json").readText()
        val resolution = PlanCheck.resolve(PlanJson.decodeFromString(PlanFile.serializer(), text), LibraryIndex.EMPTY)
        assertEquals(emptyList<String>(), resolution.problems)
        assertEquals(24, resolution.preview.exercisesAdded.size)
        assertEquals(4, resolution.preview.circuitsAdded.size)
        assertEquals(5, resolution.preview.modulesAdded.size)
        assertEquals(6, resolution.preview.variations)
        // The deliberately contradictory hike is cleaned, and says so.
        assertTrue(resolution.warnings.any { it.startsWith("Hike") && "unilateral" in it })
        val hike = resolution.plan!!.exercises.single { it.draft.name.startsWith("Hike") }.draft
        assertEquals(false, hike.unilateral)
        assertNull(hike.measurementUnit)
        assertEquals(PrescriptionPayload(sets = 1, targetDurationSeconds = 20 * 60), hike.defaultPrescription)
    }

    @Test
    fun `a file that is not a plan, or from a newer app, is refused outright`() {
        assertTrue(PlanCheck.resolve(PlanFile(), LibraryIndex.EMPTY).hasProblem("does not say what it is"))
        assertTrue(PlanCheck.resolve(PlanFile(format = "other"), LibraryIndex.EMPTY).hasProblem("not a Melete plan"))
        assertTrue(
            PlanCheck.resolve(PlanFile(format = PLAN_FORMAT, version = 2), LibraryIndex.EMPTY).hasProblem("version 2")
        )
    }

    @Test
    fun `comments and trailing commas are fine, a misspelt key is not`() {
        plan(""" // a comment
            "exercises": [ { "name": "Run", "mode": "activity", }, ], """)
        val failure = runCatching { plan(""" "weeks": [ { "weekStart": "2026-10-05", "Monday": [] } ] """) }
        assertTrue(failure.exceptionOrNull() is SerializationException)
    }

    @Test
    fun `names match whatever their case and spacing`() {
        val resolution = resolve(
            """ "exercises": [ { "name": "  PULL-UP ", "mode": "reps" } ],
                "weeks": [ { "weekStart": "2026-10-05", "monday": [ { "circuit": "pull  +  core" } ] } ] """,
            library,
        )
        assertEquals(emptyList<String>(), resolution.problems)
        assertEquals(listOf("PULL-UP"), resolution.preview.exercisesUpdated)
        assertEquals("ex-pull", resolution.plan!!.exercises.single().existingId)
    }

    @Test
    fun `a reference resolves against the file, then the library - and only the file when replacing`() {
        val json = """ "weeks": [ { "weekStart": "2026-10-05",
            "monday": [ { "exercise": "Pull-up", "variation": "a" }, { "module": "Strength day" } ] } ] """
        assertEquals(emptyList<String>(), resolve(json, library).problems)
        val replacing = resolve(json)
        assertTrue(replacing.hasProblem("no exercise called \"Pull-up\""))
        assertTrue(replacing.hasProblem("no module called \"Strength day\""))
        assertNull(replacing.plan)
    }

    @Test
    fun `when replacing, definitions still match the library by name but references do not`() {
        val resolution = PlanCheck.resolve(
            plan(""" "exercises": [ { "name": "Pull-up", "mode": "REPS" } ],
                     "weeks": [ { "weekStart": "2026-10-05", "monday": [
                        { "exercise": "Pull-up", "variation": "A" }, { "circuit": "Pull + core" } ] } ] """),
            library,
            referencesFromLibrary = false,
        )
        // Kept by name, so its id and history stay; but its variation A is not in the file.
        assertTrue(resolution.hasProblem("has no variation A"))
        assertTrue(resolution.hasProblem("no circuit called \"Pull + core\""))
        assertEquals(listOf("Pull-up"), resolution.preview.exercisesUpdated)
    }

    @Test
    fun `from today, the past is left out and counted, but still checked`() {
        val json = """ "weeks": [
            { "weekStart": "2026-09-28", "friday": [ { "activity": "Old" } ], "unscheduled": [ { "activity": "Old too" } ] },
            { "weekStart": "2026-10-05", "monday": [ { "activity": "Early" } ], "wednesday": [ { "activity": "On time" } ],
              "unscheduled": [ { "activity": "This week" } ] } ] """
        val wednesday = LocalDate.of(2026, 10, 7)
        val resolution = PlanCheck.resolve(plan(json), LibraryIndex.EMPTY, cutoff = wednesday)
        assertEquals(3, resolution.preview.skippedPast)
        assertEquals(2, resolution.preview.planned)
        assertEquals(1, resolution.preview.weeks)
        assertEquals(
            listOf("This week", "On time"),
            resolution.plan!!.slots.flatMap { it.items }.map { (it as ResolvedItem.Activity).name },
        )
        val mistake = PlanCheck.resolve(
            plan(""" "weeks": [ { "weekStart": "2026-09-28", "monday": [ { "exercise": "Nowhere" } ] } ] """),
            LibraryIndex.EMPTY, cutoff = wednesday,
        )
        assertTrue(mistake.hasProblem("Nowhere"))
    }

    @Test
    fun `variations the file does not list stay, so references to them still resolve`() {
        val resolution = resolve(
            """ "exercises": [ { "name": "Pull-up", "mode": "REPETITIONS", "variations": [ { "tag": "b" } ] } ],
                "modules": [ { "name": "M", "entries": [
                    { "exercise": "Pull-up", "variation": "A" }, { "exercise": "Pull-up", "variation": "B" } ] } ] """,
            library,
        )
        assertEquals(emptyList<String>(), resolution.problems)
        assertTrue(resolve(""" "modules": [ { "name": "M", "entries": [ { "exercise": "Pull-up", "variation": "Z" } ] } ] """, library)
            .hasProblem("has no variation Z"))
    }

    @Test
    fun `a plan keeps only what the mode reads, and says what it dropped`() {
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val spec = PlanSpec(sets = 4, reps = 8, seconds = 10, restSeconds = 60, sideSwitchSeconds = 10, minutes = 5, effort = "very hard")

        val timed = PlanCheck.payloadFor(spec, ExerciseMode.DURATION, unilateral = false, "Hang", station = false, problems, warnings)
        assertEquals(
            PrescriptionPayload(sets = 4, targetDurationSeconds = 10, restSeconds = 60, effort = EffortLevel.VERY_HARD),
            timed,
        )
        assertTrue(warnings.any { "\"reps\"" in it } && warnings.any { "sideSwitchSeconds" in it } && warnings.any { "\"minutes\"" in it })

        val station = PlanCheck.payloadFor(spec, ExerciseMode.REPETITIONS, unilateral = true, "Row", station = true, problems, warnings)
        assertEquals(1, station.sets)
        assertEquals(8, station.targetReps)
        assertEquals(10, station.sideSwitchSeconds)

        val activity = PlanCheck.payloadFor(spec, ExerciseMode.ACTIVITY, unilateral = false, "Run", station = false, problems, warnings)
        assertEquals(PrescriptionPayload(sets = 1, targetDurationSeconds = 300, effort = EffortLevel.VERY_HARD), activity)
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `mistakes are all named, and nothing is planned while any remain`() {
        val resolution = resolve(
            """ "exercises": [
                    { "name": "Squat", "mode": "REPETITIONS", "unit": "kg" },
                    { "name": "Hang", "mode": "HOLD" },
                    { "name": "Rows", "mode": "REPETITIONS", "plan": { "effort": "brutal", "sets": -1 } },
                    { "name": "Reps", "mode": "REPEATERS", "plan": { "repeater": { "reps": 0, "workSeconds": 7 } },
                      "variations": [ { "tag": "TOOLONG" }, { "tag": "A" }, { "tag": "a" } ] }
                ],
                "circuits": [ { "name": "C", "rounds": 0, "stations": [ { "circuit": "C" } ] } ],
                "weeks": [
                    { "weekStart": "2026-10-06" },
                    { "weekStart": "2026-10-05", "monday": [ { "exercise": "Squat", "activity": "Run" } ] },
                    { "weekStart": "2026-10-05" }
                ] """,
        )
        assertNull(resolution.plan)
        listOf(
            "needs a \"meaning\"", "unknown mode \"HOLD\"", "unknown effort \"brutal\"", "cannot be negative",
            "at least 1", "not a tag", "two variations are tagged A", "rounds must be at least 1",
            "a circuit cannot go here", "must be a Monday", "names both exercise and activity", "listed twice",
        ).forEach { assertTrue("missing: $it in ${resolution.problems}", resolution.hasProblem(it)) }
    }

    @Test
    fun `a name the library holds twice is never guessed between`() {
        val resolution = resolve(
            """ "weeks": [ { "weekStart": "2026-10-05", "monday": [ { "exercise": "Pull-up" } ] } ] """,
            library.copy(ambiguous = setOf("pull-up")),
        )
        assertTrue(resolution.hasProblem("no exercise called"))
        assertTrue(resolve(""" "exercises": [ { "name": "Pull-up", "mode": "REPS" } ] """, library.copy(ambiguous = setOf("pull-up")))
            .hasProblem("More than one library item"))
    }

    @Test
    fun `weeks become slots in order, undated first`() {
        val resolution = resolve(
            """ "weeks": [ { "weekStart": "2026-10-05",
                    "thursday": [ { "activity": "Outdoor bouldering", "minutes": 120 } ],
                    "unscheduled": [ { "exercise": "Pull-up", "plan": { "sets": 2 } } ] } ] """,
            library,
        )
        val slots = assertNotNull(resolution.plan).let { resolution.plan!!.slots }
        assertEquals(listOf(null, LocalDate.of(2026, 10, 8)), slots.map { it.date })
        assertEquals(ResolvedItem.Activity("Outdoor bouldering", 120), slots[1].items.single())
        assertEquals(2, (slots[0].items.single() as ResolvedItem.Exercise).pick.plan?.sets)
        assertEquals(2, resolution.preview.planned)
        assertEquals(1, resolution.preview.weeks)
    }

    // ------------------------------------------------ per-placement overrides

    private val overrideLibrary = """
        "exercises": [
          { "name": "Cossack", "mode": "reps", "unilateral": true, "plan": { "sets": 2, "reps": 6 } },
          { "name": "Clamshell", "mode": "reps", "plan": { "sets": 2, "reps": 10 } },
          { "name": "Attempt", "mode": "reps", "plan": { "reps": 1 } }
        ],
        "circuits": [ { "name": "Intervals", "rounds": 2, "stations": [ { "exercise": "Attempt" } ] } ],
        "modules": [ { "name": "FA", "entries": [ { "exercise": "Cossack" }, { "exercise": "Clamshell" }, { "circuit": "Intervals" } ] } ],
    """

    @Test
    fun `a placement can give a module's exercises and a circuit's rounds for that week only`() {
        val resolution = resolve(
            overrideLibrary + """
            "weeks": [ { "weekStart": "2026-10-05",
              "wednesday": [
                { "module": "FA", "plans": { "cossack": { "sets": 4, "reps": 6 } } },
                { "circuit": "Intervals", "rounds": 3 }
              ] } ]
            """
        )
        assertEquals(emptyList<String>(), resolution.problems)
        val items = resolution.plan!!.slots.single().items
        val module = items[0] as ResolvedItem.Module
        assertEquals(4, module.plans.getValue("cossack").sets)
        assertEquals(3, (items[1] as ResolvedItem.Circuit).rounds)
        // The templates themselves are untouched.
        assertEquals(2, resolution.plan!!.circuits.single().rounds)
    }

    @Test
    fun `overrides must name what the placement holds, and only go on placements`() {
        val wrongExercise = resolve(
            overrideLibrary + """
            "weeks": [ { "weekStart": "2026-10-05", "monday": [ { "module": "FA", "plans": { "Attempt": { "reps": 2 } } } ] } ]
            """
        )
        // Attempt is only inside the module's circuit, not an entry of its own.
        assertTrue(wrongExercise.hasProblem("has no exercise Attempt of its own"))

        val misplaced = resolve(
            overrideLibrary + """
            "weeks": [ { "weekStart": "2026-10-05", "monday": [
              { "exercise": "Cossack", "rounds": 2 },
              { "circuit": "Intervals", "plans": { "Attempt": { "reps": 2 } } },
              { "circuit": "Intervals", "rounds": 0 }
            ] } ]
            """
        )
        assertTrue(misplaced.hasProblem("\"rounds\" only goes with a circuit"))
        assertTrue(misplaced.hasProblem("\"plans\" only goes with a module"))
        assertTrue(misplaced.hasProblem("needs at least 1 round"))

        val inTemplate = resolve(
            """
            "exercises": [ { "name": "Cossack", "mode": "reps" } ],
            "modules": [ { "name": "FA", "entries": [ { "exercise": "Cossack", "rounds": 2 } ] } ]
            """
        )
        assertTrue(inTemplate.hasProblem("go where it is placed in a week"))
    }
}
