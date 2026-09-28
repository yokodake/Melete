package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.entity.TrackerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Changing or retiring a tracker changes the days to come, never a day already recorded: every
 * value keeps the tracker as it was when it was written.
 */
@RunWith(AndroidJUnit4::class)
class DiaryTrackersTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var diary: DiaryRepository

    private val monday = LocalDate.of(2026, 9, 21)
    private val tuesday = monday.plusDays(1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        diary = DiaryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun tracker(label: String) = diary.observeTrackers().first().first { it.label == label }

    private suspend fun day(date: LocalDate) = diary.observeDays(date, date).first().getValue(date)

    @Test
    fun aChangedTrackerLeavesRecordedDaysAsTheyWere() = runBlocking {
        val energy = tracker("Energy")
        diary.save(monday, null, listOf(TrackedValue(energy, TrackerReading(number = 3.0))))

        // A new name, a new kind, a unit: everything about it changes from now on.
        assertNull(diary.updateTracker(energy.id, "Body weight", TrackerType.NUMBER, null, null, "kg"))
        val now = tracker("Body weight")
        diary.save(tuesday, null, listOf(TrackedValue(now, TrackerReading(number = 72.5))))

        val then = day(monday).values.getValue(energy.id)
        assertEquals(Tracker(energy.id, "Energy", TrackerType.SCALE, 0, 5), then.tracker)
        assertEquals("Energy 3", then.tracker.format(then.reading))
        val later = day(tuesday).values.getValue(energy.id)
        assertEquals("Body weight 72.5 kg", later.tracker.format(later.reading))
    }

    @Test
    fun aRescaledTrackerKeepsValuesOutsideItsNewRange() = runBlocking {
        val energy = tracker("Energy")
        diary.save(monday, null, listOf(TrackedValue(energy, TrackerReading(number = 0.0))))
        assertNull(diary.updateTracker(energy.id, "Energy", TrackerType.SCALE, 1, 10, null))

        val then = day(monday).values.getValue(energy.id)
        assertEquals(0.0, then.reading.number)
        assertEquals(0 to 5, then.tracker.scaleMin to then.tracker.scaleMax)
    }

    @Test
    fun aRetiredTrackerIsGoneFromNowOnButStaysOnItsDays() = runBlocking {
        val finger = tracker("Finger discomfort")
        diary.save(monday, "Crimped too much", listOf(TrackedValue(finger, TrackerReading(number = 4.0))))
        diary.retireTracker(finger.id)

        assertTrue(diary.observeTrackers().first().none { it.id == finger.id })
        val recorded = day(monday)
        assertEquals("Finger discomfort 4", recorded.values.getValue(finger.id).let { it.tracker.format(it.reading) })
        // Ordered after the trackers still in use.
        assertEquals(finger.id, recorded.orderedBy(diary.observeTrackers().first()).last().tracker.id)
    }

    @Test
    fun aValueItsTrackerCannotHoldIsNeverStored() = runBlocking {
        val energy = tracker("Energy")
        diary.save(
            monday, "Kept",
            listOf(
                TrackedValue(energy, TrackerReading(number = 9.0)),     // off the scale
                TrackedValue(energy.copy(id = "x", type = TrackerType.CHECK), TrackerReading(number = 0.0)),
            ),
        )
        assertTrue(day(monday).values.isEmpty())
        assertEquals("Kept", day(monday).text)
    }

    /** One history: switching bodyweight off keeps what was weighed, and on again finds it. */
    @Test
    fun bodyweightTrackingIsOneTrackerThatComesBackWithItsHistory() = runBlocking {
        assertEquals(false, diary.observeBodyweightTracked().first())
        diary.setBodyweightTracked(true)
        assertEquals(true, diary.observeBodyweightTracked().first())
        val tracker = diary.observeTrackers().first().single { it.id == BODYWEIGHT_TRACKER_ID }
        assertEquals(TrackerType.NUMBER, tracker.type)
        assertEquals("kg", tracker.unit)

        val date = LocalDate.of(2026, 9, 28)
        diary.save(date, null, listOf(TrackedValue(tracker, TrackerReading(71.5, null))))
        diary.setBodyweightTracked(false)
        assertEquals(false, diary.observeBodyweightTracked().first())
        assertEquals(71.5, diary.observeDays(date, date).first().getValue(date).values.getValue(BODYWEIGHT_TRACKER_ID).reading.number!!, 0.0)

        diary.setBodyweightTracked(true)
        assertEquals(1, diary.observeTrackers().first().count { it.id == BODYWEIGHT_TRACKER_ID })
    }
}
