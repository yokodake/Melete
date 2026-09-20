package com.yokodake.melete

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yokodake.melete.ui.theme.MeleteTheme
import com.yokodake.melete.ui.week.WeekRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeleteTheme {
                WeekRoute()
            }
        }
    }
}
