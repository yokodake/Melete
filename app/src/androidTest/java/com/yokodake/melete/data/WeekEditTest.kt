package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.week.WeekItem
import com.yokodake.melete.ui.week.WeekUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The week's edit mode against a real database: one card, one place, across days.
 *
 * Asserted through [WeekUiState] — the same folding the screen draws — so "where did it go" is
 * answered exactly as the user would see it.
 */
@RunWith(AndroidJUnit4::class)
class WeekEditTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var repository: TrainingRepository

    private val monday = LocalDate.of(2026, 9, 21)
    private val tuesday = monday.plusDays(1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        repository = TrainingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun exercise(name: String) = repository.createExercise(
        ExerciseDraft(
            name = name,
            mode = ExerciseMode.REPETITIONS,
            unilateral = false,
            measurementUnit = null,
            measurementMeaning = null,
            notes = null,
            description = null,
            category = null,
            defaultPrescription = PrescriptionPayload(sets = 3, targetReps = 5),
        )
    )

    private suspend fun state() = WeekUiState.build(
        weekStart = monday,
        today = monday,
        occurrences = repository.observeWeek(monday).first(),
        circuits = repository.observeWeekCircuits(monday).first(),
        modules = repository.observeWeekModules(monday).first(),
    )

    private fun List<WeekItem>.names() = map {
        when (it) {
            is WeekItem.Single -> it.occurrence.name
            is WeekItem.Circuit -> it.circuit.name
            is WeekItem.Module -> it.module.name
        }
    }

    private suspend fun day(date: LocalDate) =
        state().days.single { it.date == date }.items.names()

    private suspend fun unscheduled() = state().unscheduled.names()

    private suspend fun ref(name: String): PlanItemRef {
        val all = (state().unscheduled + state().days.flatMap { it.items })
        return when (val item = all.first { listOf(it).names().single() == name }) {
            is WeekItem.Single -> PlanItemRef(PlanItemKind.EXERCISE, item.occurrence.id)
            is WeekItem.Circuit -> PlanItemRef(PlanItemKind.CIRCUIT, item.circuit.id)
            is WeekItem.Module -> PlanItemRef(PlanItemKind.MODULE, item.module.id)
        }
    }

    /** Monday: Warm-up, then a circuit of two, then a module of two, then Cool-down. */
    private suspend fun busyMonday() {
        val warm = exercise("Warm-up")
        val row = exercise("Row")
        val pull = exercise("Pull-up")
        val hang = exercise("Hang")
        val stretch = exercise("Stretch")
        val cool = exercise("Cool-down")
        val circuit = repository.createRoutine(
            RoutineDraft(
                name = "Pull circuit", rounds = 3, transitionSeconds = 30, roundRestSeconds = 120,
                entries = listOf(
                    RoutineEntryDraft(row, PrescriptionPayload(sets = 1, targetReps = 8)),
                    RoutineEntryDraft(pull, PrescriptionPayload(sets = 1, targetReps = 5)),
                ),
            )
        )
        val module = repository.createModule(
            ModuleDraft(
                name = "Fingers",
                description = null,
                entries = listOf(
                    ModuleEntryDraft(exerciseId = hang, prescription = PrescriptionPayload(sets = 5)),
                    ModuleEntryDraft(exerciseId = stretch, prescription = PrescriptionPayload(sets = 2)),
                ),
            )
        )
        repository.scheduleExercise(warm, monday, monday)
        repository.scheduleRoutine(circuit, monday, monday)
        repository.scheduleModule(module, monday, monday)
        repository.scheduleExercise(cool, monday, monday)
    }

    @Test
    fun aCardMovesPastACircuitOrAModuleOnePlaceAtATime() = runBlocking {
        busyMonday()
        assertEquals(listOf("Warm-up", "Pull circuit", "Fingers", "Cool-down"), day(monday))

        repository.nudge(ref("Warm-up"), 1)
        assertEquals(listOf("Pull circuit", "Warm-up", "Fingers", "Cool-down"), day(monday))
        repository.nudge(ref("Warm-up"), 1)
        assertEquals(listOf("Pull circuit", "Fingers", "Warm-up", "Cool-down"), day(monday))

        // Containers move as one card too.
        repository.nudge(ref("Fingers"), -1)
        assertEquals(listOf("Fingers", "Pull circuit", "Warm-up", "Cool-down"), day(monday))
    }

    @Test
    fun whatIsInsideAModuleKeepsItsOwnOrder() = runBlocking {
        busyMonday()
        repository.nudge(ref("Fingers"), -1)
        repository.nudge(ref("Fingers"), -1)
        val group = state().days.single { it.date == monday }.items
            .filterIsInstance<WeekItem.Module>().single()
        assertEquals(listOf("Hang", "Stretch"), group.members.names())
    }

    @Test
    fun theEdgeOfADayCarriesACardIntoTheNextAndBack() = runBlocking {
        busyMonday()
        repository.nudge(ref("Cool-down"), 1)
        assertEquals(listOf("Warm-up", "Pull circuit", "Fingers"), day(monday))
        assertEquals(listOf("Cool-down"), day(tuesday))

        repository.nudge(ref("Cool-down"), -1)
        // Back at the bottom of Monday, where "up" out of Tuesday leads.
        assertEquals(listOf("Warm-up", "Pull circuit", "Fingers", "Cool-down"), day(monday))
    }

    @Test
    fun aWholeModuleCrossesDaysWithItsMembers() = runBlocking {
        busyMonday()
        repository.nudge(ref("Cool-down"), -1)
        // Fingers is now the last card on Monday, so one nudge down carries it into Tuesday...
        repository.nudge(ref("Fingers"), 1)
        assertEquals(listOf("Fingers"), day(tuesday))
        val group = state().days.single { it.date == tuesday }.items
            .filterIsInstance<WeekItem.Module>().single()
        assertEquals(listOf("Hang", "Stretch"), group.members.names())

        // ...and, alone on Tuesday, the next one straight on into Wednesday, members and all.
        repository.nudge(ref("Fingers"), 1)
        assertEquals(emptyList<String>(), day(tuesday))
        val wednesday = state().days.single { it.date == monday.plusDays(2) }.items
            .filterIsInstance<WeekItem.Module>().single()
        assertEquals(listOf("Hang", "Stretch"), wednesday.members.names())
    }

    @Test
    fun aboveMondayIsTheUnscheduledAreaButNotForTrainedWork() = runBlocking {
        busyMonday()
        repository.nudge(ref("Warm-up"), -1)
        assertEquals(listOf("Warm-up"), unscheduled())

        val cool = ref("Cool-down")
        repository.saveLogs(
            trainingDate = monday,
            writes = listOf(
                OccurrenceLogWrite(
                    occurrenceId = cool.id,
                    completed = true,
                    sets = listOf(SetWrite(ActualSetPayload(reps = 5), null)),
                )
            ),
        )
        repeat(3) { repository.nudge(cool, -1) }
        // Now at the top of Monday, and trained: it cannot be made undated.
        assertEquals(NudgeResult.NEEDS_A_DAY, repository.nudge(cool, -1))
        assertEquals("Cool-down", day(monday).first())
    }

    @Test
    fun theEndsOfTheWeekStop() = runBlocking {
        busyMonday()
        repository.nudge(ref("Warm-up"), -1)
        assertEquals(NudgeResult.AT_EDGE, repository.nudge(ref("Warm-up"), -1))
    }
}
