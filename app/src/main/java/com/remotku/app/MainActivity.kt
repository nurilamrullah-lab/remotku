package com.remotku.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.remotku.app.core.haptics.LocalHaptics
import com.remotku.app.core.haptics.rememberHaptics
import com.remotku.app.ui.main.MainScreen
import com.remotku.app.ui.theme.RemotkuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as RemotkuApplication).container
        setContent {
            RemotkuTheme {
                CompositionLocalProvider(LocalHaptics provides rememberHaptics()) {
                    MainScreen(container)
                }
            }
        }
    }
}
