package com.yokodake.melete

import android.app.Application
import android.content.Context
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.plan.PlanImporter
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.TrainingRepository
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

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(database) }

    val backupService: BackupService by lazy { BackupService(database) }

    val planImporter: PlanImporter by lazy { PlanImporter(database, trainingRepository, backupService) }

    val timerNotifications: TimerNotifications by lazy { TimerNotifications(applicationContext) }

    /**
     * Survives every screen, because the countdown belongs to the workout rather than to whatever
     * page happens to be open. Lazy so that it is built when something first asks for the timer,
     * restoring whatever run was under way from disk at that point.
     */
    val timerController: TimerController by lazy {
        TimerController(
            context = applicationContext,
            store = TimerStore(applicationContext),
            cues = TimerCuePlayer(applicationContext),
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
