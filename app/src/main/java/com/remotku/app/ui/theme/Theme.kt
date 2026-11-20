package com.remotku.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Clean light palette: soft grey canvas, white surfaces, hairline borders, one indigo accent. */
object RemotkuColors {
    val Background = Color(0xFFF8F9FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF1F3F5)
    val SurfaceStrong = Color(0xFFE9ECEF)
    val Outline = Color(0xFFE5E7EB)
    val Text = Color(0xFF111827)
    val TextMuted = Color(0xFF6B7280)
    val Primary = Color(0xFF4F46E5)
    val PrimarySoft = Color(0xFFEEF2FF)
    val Power = Color(0xFFE5484D)
    val Online = Color(0xFF16A34A)
    val Warning = Color(0xFFD97706)
}

private val LightScheme = lightColorScheme(
    primary = RemotkuColors.Primary,
    onPrimary = Color.White,
    primaryContainer = RemotkuColors.PrimarySoft,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = RemotkuColors.Online,
    error = RemotkuColors.Power,
    background = RemotkuColors.Background,
    onBackground = RemotkuColors.Text,
    surface = RemotkuColors.Surface,
    onSurface = RemotkuColors.Text,
    surfaceVariant = RemotkuColors.SurfaceMuted,
    onSurfaceVariant = RemotkuColors.TextMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = RemotkuColors.SurfaceMuted,
    surfaceContainerHigh = RemotkuColors.SurfaceMuted,
    surfaceContainerHighest = RemotkuColors.SurfaceStrong,
    outline = Color(0xFFD1D5DB),
    outlineVariant = RemotkuColors.Outline,
)

private val base = Typography()
private val RemotkuTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
)

private val RemotkuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun RemotkuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, typography = RemotkuTypography, shapes = RemotkuShapes) {
        CompositionLocalProvider(LocalContentColor provides LightScheme.onBackground, content = content)
    }
}
