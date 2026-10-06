package com.yokodake.melete

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yokodake.melete.data.reminders.OpenTarget
import com.yokodake.melete.ui.MeleteApp
import com.yokodake.melete.ui.OpenRequest
import com.yokodake.melete.ui.theme.MeleteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** What a tapped reminder asked to show, until the app has shown it. */
    private val openRequest = MutableStateFlow<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch: a recreated activity has already been where its intent pointed.
        if (savedInstanceState == null) handle(intent)
        setContent {
            MeleteTheme {
                MeleteApp(openRequest = openRequest, onOpenHandled = { openRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != ACTION_OPEN) return
        // Reopened from Recents, the old intent comes back; it was handled then.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val target = OpenTarget.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TARGET) } ?: return
        if (!intent.hasExtra(EXTRA_DATE)) return
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, 0))
        openRequest.value = OpenRequest(target, date, System.nanoTime())
    }

    companion object {
        const val ACTION_OPEN = "com.yokodake.melete.OPEN"
        const val EXTRA_TARGET = "target"
        const val EXTRA_DATE = "dateEpochDay"
    }
}
