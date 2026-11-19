package com.remotku.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object RemotkuColors {
    val Black = Color(0xFF000000)
    val Surface1 = Color(0xFF0B0B0E)
    val Surface2 = Color(0xFF141418)
    val Surface3 = Color(0xFF1E1E24)
    val Outline = Color(0xFF2C2C34)
    val Primary = Color(0xFF4F8CFF)
    val PrimaryDim = Color(0xFF1B2D52)
    val Power = Color(0xFFFF5468)
    val Online = Color(0xFF3DDC84)
    val Warning = Color(0xFFFFC14D)
    val TextMuted = Color(0xFF9A9AA5)
}

private val AmoledScheme = darkColorScheme(
    primary = RemotkuColors.Primary,
    onPrimary = Color.White,
    primaryContainer = RemotkuColors.PrimaryDim,
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF8AB4F8),
    secondaryContainer = RemotkuColors.Surface3,
    onSecondaryContainer = Color.White,
    tertiary = RemotkuColors.Online,
    error = RemotkuColors.Power,
    background = RemotkuColors.Black,
    onBackground = Color(0xFFEDEDF2),
    surface = RemotkuColors.Black,
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = RemotkuColors.Surface2,
    onSurfaceVariant = RemotkuColors.TextMuted,
    surfaceContainerLowest = RemotkuColors.Black,
    surfaceContainerLow = RemotkuColors.Surface1,
    surfaceContainer = RemotkuColors.Surface1,
    surfaceContainerHigh = RemotkuColors.Surface2,
    surfaceContainerHighest = RemotkuColors.Surface3,
    outline = RemotkuColors.Outline,
    outlineVariant = RemotkuColors.Outline,
)

@Composable
fun RemotkuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AmoledScheme, typography = Typography()) {
        // Default content color for anything drawn outside a Surface (dark theme => light text).
        CompositionLocalProvider(LocalContentColor provides AmoledScheme.onBackground, content = content)
    }
}
