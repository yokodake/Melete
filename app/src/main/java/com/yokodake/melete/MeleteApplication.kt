package com.yokodake.melete

import android.app.Application
import android.content.Context
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.timer.TimerAlarms
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerCuePlayer
import com.yokodake.melete.data.timer.TimerNotifications
import com.yokodake.melete.data.timer.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. The app is small and single-user; a full DI framework would add
 * more machinery than it removes.
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: MeleteDatabase by lazy { MeleteDatabase.build(applicationContext) }

    val trainingRepository: TrainingRepository by lazy { TrainingRepository(database) }

    val timerNotifications: TimerNotifications by lazy { TimerNotifications(applicationContext) }

    /**
     * Survives every screen, because the countdown belongs to the workout rather than to whatever
     * page happens to be open. Lazy so that a process created only to deliver an alarm builds it,
     * restores the run from disk, and can decide whether that cue is still owed.
     */
    val timerController: TimerController by lazy {
        TimerController(
            context = applicationContext,
            store = TimerStore(applicationContext),
            cues = TimerCuePlayer(applicationContext),
            alarms = TimerAlarms(applicationContext),
            notifications = timerNotifications,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}

class MeleteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
