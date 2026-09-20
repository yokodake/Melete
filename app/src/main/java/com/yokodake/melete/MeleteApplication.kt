package com.yokodake.melete

import android.app.Application
import android.content.Context
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.TrainingRepository

/**
 * Manual dependency container. The app is small and single-user; a full DI framework would add
 * more machinery than it removes.
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: MeleteDatabase by lazy { MeleteDatabase.build(applicationContext) }

    val trainingRepository: TrainingRepository by lazy { TrainingRepository(database) }
}

class MeleteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
