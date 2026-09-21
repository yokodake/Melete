package com.yokodake.melete.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.timer.AlarmScheduler
import com.yokodake.melete.data.timer.CuePlayer
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerCue
import com.yokodake.melete.data.timer.TimerNotifications
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cue delivery on a real device, with real durable bookkeeping.
 *
 * These cover the part that arithmetic tests cannot: two independent paths may try to sound the
 * same cue, and exactly one of them must win, including when the second path belongs to a process
 * that was created after the first one died.
 */
@RunWith(AndroidJUnit4::class)
class TimerControllerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class RecordingCuePlayer : CuePlayer {
        val played = CopyOnWriteArrayList<TimerCue>()
        override fun play(cue: TimerCue) {
            played += cue
        }
    }

    /**
     * Stands in for the real exact alarms. The backstop is exercised by calling `onAlarm`
     * directly, which is what the receiver does, so the tests do not have to wait on the platform
     * or race the app's own controller for the same durable bookkeeping.
     */
    private class FakeAlarms : AlarmScheduler {
        var scheduled = 0
        var cancelled = 0
        override fun schedule(state: TimerState.Running) {
            scheduled++
        }

        override fun cancelAll() {
            cancelled++
        }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var store: TimerStore
    private lateinit var cues: RecordingCuePlayer
    private lateinit var alarms: FakeAlarms
    private lateinit var savedSettings: Triple<CueSettings, Int, Int>

    /** Only the end, so the tests are about delivery rather than about the cue plan. */
    private val endOnly = CueSettings(
        thirtySecondWarning = false,
        finalCountdown = false,
        quarterCues = false,
    )

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = TimerStore(context)
        cues = RecordingCuePlayer()
        alarms = FakeAlarms()
        // These settings belong to the user; the tests borrow them and hand them back.
        savedSettings = store.snapshotSettings()
        // Leave no state from an earlier test or from the app itself.
        store.readSnapshot()?.let { store.clearCues(it.runId) }
        store.writeSnapshot(null)
    }

    @After
    fun tearDown() {
        scope.cancel()
        store.readSnapshot()?.let { store.clearCues(it.runId) }
        store.writeSnapshot(null)
        store.restoreSettings(savedSettings)
    }

    private fun controller(player: CuePlayer = cues) = TimerController(
        context = context,
        store = store,
        cues = player,
        alarms = alarms,
        notifications = TimerNotifications(context),
        scope = scope,
    )

    @Test
    fun aCountdownRunsOutAndSoundsExactlyOnce() = runBlocking {
        val controller = controller()
        controller.start(TimerPhase.REST, durationSeconds = 1, settings = endOnly)

        delay(2_500)

        assertEquals(listOf(TimerCue.FINISH), cues.played.toList())
        assertTrue(controller.state.value is TimerState.Finished)
        controller.cancel()
    }

    @Test
    fun anAlarmForTheSameRunDoesNotSoundASecondTime() = runBlocking {
        val controller = controller()
        controller.start(TimerPhase.REST, durationSeconds = 1, settings = endOnly)
        delay(2_500)
        val runId = (controller.state.value as TimerState.Finished).runId

        // The backstop alarm arriving late, after the in-process countdown already cued.
        controller.onAlarm(runId, TimerCue.FINISH)
        delay(500)

        assertEquals(listOf(TimerCue.FINISH), cues.played.toList())
        controller.cancel()
    }

    @Test
    fun everyPlannedCueIsDeliveredOnceAndInOrder() = runBlocking {
        val controller = controller()
        // A rest, so the countdown under test is the whole run: starting work leads with a
        // preparation, which is covered on its own below.
        controller.start(
            TimerPhase.REST,
            durationSeconds = 4,
            settings = CueSettings(
                thirtySecondWarning = false,
                finalCountdown = true,
                quarterCues = false,
            ),
        )

        delay(5_500)

        assertEquals(
            listOf(TimerCue.COUNT_3, TimerCue.COUNT_2, TimerCue.COUNT_1, TimerCue.FINISH),
            cues.played.toList(),
        )
        controller.cancel()
    }

    @Test
    fun startingWorkLeadsWithAPreparationCountdown() = runBlocking {
        val controller = controller()
        controller.start(TimerPhase.WORK, durationSeconds = 30, settings = endOnly)

        // A set that begins the instant the button is pressed begins without you.
        val state = controller.state.value as TimerState.Running
        assertEquals(TimerPhase.PREPARE, state.phase)
        assertEquals(TimerProgram.PREPARE_SECONDS * 1000L, state.totalMs)

        controller.cancel()
    }

    @Test
    fun aPreparationHandsOverToTheSetItWasPreparingFor() = runBlocking {
        val controller = controller()
        controller.start(TimerPhase.WORK, durationSeconds = 30, settings = endOnly)
        val prepareRunId = (controller.state.value as TimerState.Running).runId

        // Five seconds of preparation, then the set itself under a new run id.
        delay(TimerProgram.PREPARE_SECONDS * 1000L + 800)

        val state = controller.state.value as TimerState.Running
        assertEquals(TimerPhase.WORK, state.phase)
        assertEquals(30_000L, state.totalMs)
        assertTrue("each interval is its own run", state.runId != prepareRunId)
        // The end of the preparation is the "go", so it sounds.
        assertEquals(listOf(TimerCue.FINISH), cues.played.toList())

        controller.cancel()
    }

    @Test
    fun cancellingBeforeTheEndSoundsNothing() = runBlocking {
        val controller = controller()
        controller.start(TimerPhase.REST, durationSeconds = 2, settings = endOnly)
        val runId = (controller.state.value as TimerState.Running).runId
        delay(300)
        controller.cancel()

        // A cue that was already in flight when the run was called off must not arrive.
        controller.onAlarm(runId, TimerCue.FINISH)
        delay(2_500)

        assertTrue(cues.played.isEmpty())
        assertEquals(TimerState.Idle, controller.state.value)
        assertTrue("cancelling must leave no alarm pending", alarms.cancelled > 0)
    }

    @Test
    fun pausingHoldsTheRemainingTimeAndResumingDoesNotRepeatACue() = runBlocking {
        val controller = controller()
        controller.start(
            TimerPhase.REST,
            durationSeconds = 5,
            settings = CueSettings(
                thirtySecondWarning = false,
                finalCountdown = true,
                quarterCues = false,
            ),
        )
        delay(2_500)

        controller.pause()
        val paused = controller.state.value as TimerState.Paused
        assertTrue("the three second tick should already be given", paused.delivered.isNotEmpty())
        val remaining = paused.remainingMs
        val givenSoFar = cues.played.toList()

        delay(1_500)
        assertEquals(remaining, (controller.state.value as TimerState.Paused).remainingMs)
        assertEquals("a paused countdown sounds nothing", givenSoFar, cues.played.toList())

        controller.resume()
        delay(remaining + 800)

        assertEquals(
            listOf(TimerCue.COUNT_3, TimerCue.COUNT_2, TimerCue.COUNT_1, TimerCue.FINISH),
            cues.played.toList(),
        )
        controller.cancel()
    }

    @Test
    fun aRunSurvivesTheProcessOwningItGoingAway() = runBlocking {
        val first = controller()
        first.start(TimerPhase.REST, durationSeconds = 30, settings = endOnly)
        val runId = (first.state.value as TimerState.Running).runId

        // A second controller built from the same durable state is what a fresh process sees.
        val second = controller(RecordingCuePlayer())
        val restored = second.state.value
        assertTrue(restored is TimerState.Running)
        assertEquals(runId, (restored as TimerState.Running).runId)

        second.cancel()
    }

    @Test
    fun aCueIsNotOwedTwiceAcrossProcesses() = runBlocking {
        val first = controller()
        first.start(TimerPhase.REST, durationSeconds = 1, settings = endOnly)
        delay(2_000)
        val runId = (first.state.value as TimerState.Finished).runId

        val laterPlayer = RecordingCuePlayer()
        val later = controller(laterPlayer)
        later.onAlarm(runId, TimerCue.FINISH)
        delay(500)

        assertTrue(laterPlayer.played.isEmpty())
        later.cancel()
    }

    @Test
    fun anAlarmDeliversTheCueWhenTheCountdownItselfNeverGotThere() = runBlocking {
        // What a killed process looks like: the run is on disk, nothing is ticking, and the
        // backstop alarm is the only thing left to ring.
        val owner = controller()
        owner.start(TimerPhase.REST, durationSeconds = 60, settings = endOnly)
        val runId = (owner.state.value as TimerState.Running).runId

        owner.onAlarm(runId, TimerCue.FINISH)
        delay(500)

        assertEquals(listOf(TimerCue.FINISH), cues.played.toList())
        assertTrue(owner.state.value is TimerState.Finished)
        owner.cancel()
    }

    @Test
    fun aCueAlreadyRungByTheOtherPathStillEndsTheCountdown() = runBlocking {
        val owner = controller()
        owner.start(TimerPhase.REST, durationSeconds = 60, settings = endOnly)
        val runId = (owner.state.value as TimerState.Running).runId

        // Someone else claimed the cue first: the sound is not owed twice, but the run is over.
        assertTrue(store.markCueDelivered(runId, TimerCue.FINISH))
        owner.onAlarm(runId, TimerCue.FINISH)
        delay(500)

        assertTrue(cues.played.isEmpty())
        assertTrue(owner.state.value is TimerState.Finished)
        owner.cancel()
    }
}
