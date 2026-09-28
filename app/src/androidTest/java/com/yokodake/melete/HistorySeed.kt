package com.yokodake.melete

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.ExerciseDraft
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.OccurrenceLogWrite
import com.yokodake.melete.data.SetWrite
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import kotlin.random.Random

/**
 * Not a test: writes years of made-up training history into **Melete Debug**, for looking at the
 * dashboard and, later, the graphs against something the size of a real record. Skipped unless
 * asked for by name, refused on any other app, and refused a second time unless forced:
 *
 *     adb shell am instrument -w -e seed history [-e force true] \
 *         -e class com.yokodake.melete.HistorySeed \
 *         com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Deterministic (a fixed random seed), from 2 January 2023 to the Sunday before the current
 * week. Two or three sessions a normal week — strength, fingers, board, a circuit, flexibility —
 * with loads that creep up and dip after breaks; thin springs; no training in summer 2024, in
 * September 2023 or in October 2025; about ten outdoor climbing days a year; two two-week trips a
 * year with nothing logged at all. Everything goes through the repository, as logging does:
 * scheduled on its date, then logged, sets and durations included (most typed, some inferred,
 * a few missing).
 */
@RunWith(AndroidJUnit4::class)
class HistorySeed {

    @Test
    fun seedHistory() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("seed") == "history")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "com.yokodake.melete.debug") { "History seeding may only modify Melete Debug." }
        fun say(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$text\n") })

        val start = LocalDate.of(2023, 1, 2)
        val end = WeekMath.weekStartOf(LocalDate.now()).minusDays(1)
        val database = MeleteDatabase.build(context)
        try {
            val repository = TrainingRepository(database)
            val first = repository.observeFirstCompletedDate().first()
            if (first != null && first < start.plusYears(1) && arguments.getString("force") != "true") {
                say("History already present (first completed $first); pass -e force true to add it again.")
                return@runBlocking
            }
            val library = Library(repository)
            val random = Random(20230102)
            val plan = Calendar(start, end, random)
            var sessions = 0
            var exercises = 0

            // One transaction per logged day's workout, as the logger does — not one around the
            // lot, since the library is read through flows as it goes.
            var date = start
            while (date <= end) {
                for (session in plan.sessionsOn(date)) {
                    exercises += library.log(session, date, plan.fitness(date), random)
                    sessions++
                }
                date = date.plusDays(1)
            }
            say("Seeded $sessions sessions, $exercises completed exercises, $start to $end.")
            say("Off: summer 2024, September 2023, October 2025; trips: ${plan.trips.joinToString { "${it.start}" }}")
        } finally {
            database.close()
        }
    }

    /** The kinds of day the history is made of. */
    private enum class Session { STRENGTH, FINGERS, BOARD, CIRCUIT, FLEXIBILITY, OUTDOOR, RUN }

    private data class Trip(val start: LocalDate) {
        operator fun contains(date: LocalDate) = date >= start && date < start.plusWeeks(2)
    }

    /** When training happens, and how fit the made-up athlete is on a day. */
    private class Calendar(private val start: LocalDate, end: LocalDate, random: Random) {

        private fun off(date: LocalDate): Boolean =
            (date.year == 2024 && date.month in listOf(Month.JUNE, Month.JULY, Month.AUGUST)) ||
                (date.year == 2023 && date.month == Month.SEPTEMBER) ||
                (date.year == 2025 && date.month == Month.OCTOBER)

        /** Two trips a year, two weeks each, clear of the months off. */
        val trips: List<Trip> = (start.year..end.year).flatMap { year ->
            listOf(Month.APRIL to 0, Month.NOVEMBER to 1).mapNotNull { (month, offset) ->
                val day = LocalDate.of(year, month, 1).plusDays(random.nextLong(0, 20) + offset)
                WeekMath.weekStartOf(day).takeIf { !off(it) && !off(it.plusDays(13)) && it >= start && it.plusDays(13) <= end }
                    ?.let(::Trip)
            }
        }

        /** Roughly ten outdoor days a year, on weekends that are neither off nor away. */
        private val outdoor: Set<LocalDate> = run {
            val days = mutableSetOf<LocalDate>()
            for (year in start.year..end.year) {
                var placed = 0
                var guard = 0
                while (placed < 10 && guard++ < 500) {
                    val day = LocalDate.of(year, 1, 1).plusDays(random.nextLong(0, 365))
                        .with(DayOfWeek.SATURDAY)
                    if (day < start || day > end || off(day) || trips.any { day in it } || !days.add(day)) continue
                    placed++
                }
            }
            days
        }

        /** Which weeks of spring are thin (one session) and which empty, decided once per week. */
        private val weekKind: MutableMap<LocalDate, Int> = mutableMapOf()
        private val weekRandom = Random(7)

        fun sessionsOn(date: LocalDate): List<Session> {
            if (off(date) || trips.any { date in it }) return emptyList()
            if (date in outdoor) return listOf(Session.OUTDOOR)
            val week = WeekMath.weekStartOf(date)
            val spring = date.month in listOf(Month.MARCH, Month.APRIL, Month.MAY)
            // 0 = empty, 1 = thin, 2 = normal, 3 = keen.
            val kind = weekKind.getOrPut(week) {
                val roll = weekRandom.nextInt(100)
                if (spring) (if (roll < 45) 0 else 1) else (if (roll < 8) 1 else if (roll < 70) 2 else 3)
            }
            val pattern: Map<DayOfWeek, List<Session>> = when (kind) {
                0 -> emptyMap()
                1 -> mapOf(DayOfWeek.WEDNESDAY to listOf(Session.BOARD))
                2 -> mapOf(
                    DayOfWeek.MONDAY to listOf(Session.FINGERS, Session.STRENGTH),
                    DayOfWeek.THURSDAY to listOf(Session.BOARD),
                    DayOfWeek.SATURDAY to listOf(if (week.dayOfYear % 2 == 0) Session.CIRCUIT else Session.FLEXIBILITY),
                )
                else -> mapOf(
                    DayOfWeek.MONDAY to listOf(Session.FINGERS, Session.STRENGTH),
                    DayOfWeek.WEDNESDAY to listOf(Session.BOARD),
                    DayOfWeek.FRIDAY to listOf(Session.CIRCUIT, Session.FLEXIBILITY),
                    DayOfWeek.SUNDAY to listOf(Session.RUN),
                )
            }
            return pattern[date.dayOfWeek].orEmpty()
        }

        /**
         * 0..1: builds with steady training and drops after long breaks, so loads drawn from it
         * rise over the years and dip after the summer off.
         */
        fun fitness(date: LocalDate): Double {
            val years = (date.toEpochDay() - start.toEpochDay()) / 365.0
            val base = (years / 4.0).coerceIn(0.0, 1.0)
            val afterBreak = listOf(LocalDate.of(2024, 9, 1), LocalDate.of(2023, 10, 1), LocalDate.of(2025, 11, 1))
                .filter { date >= it }
                .minOfOrNull { (date.toEpochDay() - it.toEpochDay()).toDouble() }
            val dip = if (afterBreak != null && afterBreak < 60) 0.15 * (1 - afterBreak / 60) else 0.0
            return (base - dip).coerceIn(0.0, 1.0)
        }
    }

    /** The exercises and circuits a session uses, found in the library or created. */
    private class Library(private val repository: TrainingRepository) {

        private var exercises: Map<String, LibraryExercise> = emptyMap()
        private var routines: Map<String, String> = emptyMap()

        private suspend fun refresh() {
            exercises = repository.observeLibrary().first().filter { it.deletedAtEpochMs == null }.associateBy { it.name }
            routines = repository.observeRoutines().first().associate { it.name to it.id }
        }

        private suspend fun exercise(
            name: String,
            mode: ExerciseMode,
            category: ExerciseCategory,
            unit: String? = null,
            meaning: MeasurementMeaning? = null,
            unilateral: Boolean = false,
            plan: PrescriptionPayload = PrescriptionPayload(sets = 3, targetReps = 5),
        ): LibraryExercise {
            if (exercises.isEmpty()) refresh()
            exercises[name]?.let { return it }
            repository.createExercise(
                ExerciseDraft(name, mode, unilateral, unit, meaning, null, null, category, plan)
            )
            refresh()
            return exercises.getValue(name)
        }

        private fun load(value: Double, exercise: LibraryExercise): Measurement? =
            exercise.measurementUnit?.let {
                // Loads are recorded to the half-kilo, as a plate allows.
                Measurement(Math.round(value * 2) / 2.0, it, exercise.measurementMeaning ?: MeasurementMeaning.TOTAL_LOAD)
            }

        /** Schedules one exercise on its day and logs it; returns the one completed exercise. */
        private suspend fun logOne(
            exercise: LibraryExercise,
            date: LocalDate,
            sets: List<SetWrite>,
            minutes: Int?,
            random: Random,
            effort: EffortLevel? = null,
        ): Int {
            val id = repository.scheduleExercise(exercise.id, WeekMath.weekStartOf(date), date)
            // Most durations typed, some left to the estimate, a few never recorded.
            val roll = random.nextInt(100)
            val seconds = minutes?.takeIf { roll >= 8 }?.let { it * 60 + random.nextInt(-120, 121) }?.coerceAtLeast(60)
            repository.saveLogs(
                date,
                listOf(
                    OccurrenceLogWrite(
                        occurrenceId = id,
                        completed = true,
                        sets = sets,
                        effort = effort,
                        durationSeconds = seconds,
                        durationManual = roll >= 35,
                    )
                ),
            )
            return 1
        }

        private fun sets(count: Int, reps: Int?, value: Double?, exercise: LibraryExercise, random: Random): List<SetWrite> {
            val effort = EffortLevel.entries[random.nextInt(1, 4)]
            return (0 until count).flatMap { index ->
                // The last set sometimes drops a little, as last sets do.
                val v = value?.let { if (index == count - 1 && random.nextInt(3) == 0) it - 2.5 else it }
                val payload = ActualSetPayload(reps = reps, measurement = v?.let { load(it, exercise) }, effort = effort)
                if (exercise.unilateral) {
                    listOf(SetWrite(payload, BodySide.LEFT), SetWrite(payload, BodySide.RIGHT))
                } else {
                    listOf(SetWrite(payload, null))
                }
            }
        }

        suspend fun log(session: Session, date: LocalDate, fitness: Double, random: Random): Int {
            fun jitter(amplitude: Double) = (random.nextDouble() - 0.5) * 2 * amplitude
            return when (session) {
                Session.STRENGTH -> {
                    val pull = exercise("Weighted pull-up", ExerciseMode.REPETITIONS, ExerciseCategory.STRENGTH_CONDITIONING, "kg", MeasurementMeaning.ADDED_LOAD)
                    val squat = exercise("Back squat", ExerciseMode.REPETITIONS, ExerciseCategory.STRENGTH_CONDITIONING, "kg", MeasurementMeaning.TOTAL_LOAD)
                    logOne(pull, date, sets(4, 3, 15 + 25 * fitness + jitter(2.5), pull, random), 25, random) +
                        logOne(squat, date, sets(4, 5, 60 + 40 * fitness + jitter(5.0), squat, random), 30, random)
                }
                Session.FINGERS -> {
                    val hang = exercise("Max hangs 20 mm", ExerciseMode.DURATION, ExerciseCategory.FINGER_TRAINING, "kg", MeasurementMeaning.ADDED_LOAD)
                    val repeaters = exercise("7/3 repeaters 20 mm", ExerciseMode.REPEATERS, ExerciseCategory.FINGER_TRAINING)
                    // Assisted at first, then bodyweight, then weighted: the signed scale at work.
                    logOne(hang, date, sets(5, null, -5 + 25 * fitness + jitter(2.0), hang, random), 20, random) +
                        logOne(repeaters, date, sets(3, null, null, repeaters, random), 15, random)
                }
                Session.BOARD -> logOne(
                    exercise("Board session", ExerciseMode.ACTIVITY, ExerciseCategory.BOARD_CLIMBING),
                    date, emptyList(), 75 + random.nextInt(0, 46), random, EffortLevel.HARD,
                )
                Session.FLEXIBILITY -> {
                    val couch = exercise("Couch stretch", ExerciseMode.DURATION, ExerciseCategory.FLEXIBILITY)
                    logOne(couch, date, sets(2, null, null, couch, random), 10, random) +
                        if (random.nextInt(3) == 0) {
                            logOne(exercise("Yoga", ExerciseMode.ACTIVITY, ExerciseCategory.FLEXIBILITY), date, emptyList(), 60, random)
                        } else {
                            0
                        }
                }
                Session.RUN -> logOne(
                    exercise("Run", ExerciseMode.ACTIVITY, ExerciseCategory.OTHER_ACTIVITY),
                    date, emptyList(), 30 + random.nextInt(0, 31), random,
                )
                Session.OUTDOOR -> logOne(
                    exercise("Outdoor bouldering", ExerciseMode.ACTIVITY, ExerciseCategory.OPEN_CLIMBING),
                    date, emptyList(), 180 + random.nextInt(0, 121), random, EffortLevel.HARD,
                )
                Session.CIRCUIT -> circuit(date, random)
            }
        }

        /** A saved circuit, scheduled and every station logged: each counts once, the circuit nothing. */
        private suspend fun circuit(date: LocalDate, random: Random): Int {
            if (routines.isEmpty()) refresh()
            val routineId = routines["Pull + core"] ?: routines.values.firstOrNull()
                ?: return logOne(
                    exercise("Plank", ExerciseMode.DURATION, ExerciseCategory.STRENGTH_CONDITIONING),
                    date, emptyList(), 10, random,
                )
            val circuitId = repository.scheduleRoutine(routineId, WeekMath.weekStartOf(date), date) ?: return 0
            val stations = repository.observeScheduledCircuit(circuitId).first()?.stations.orEmpty()
            repository.saveLogs(
                date,
                stations.map { station ->
                    OccurrenceLogWrite(
                        occurrenceId = station.id,
                        completed = true,
                        sets = List(3) { SetWrite(ActualSetPayload(reps = station.prescription?.targetReps), null) },
                        durationSeconds = 6 * 60 + random.nextInt(-60, 61),
                        durationManual = false,
                    )
                },
            )
            return stations.size
        }
    }
}
