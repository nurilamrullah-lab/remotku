package com.remotku.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class HapticType { Tick, Click, Heavy, Confirm, Reject }

/**
 * Uses predefined vibration effects (API 29+) for crisp, consistent pulses and falls back to
 * short calibrated one-shots on older devices or the View haptic channel without a vibrator.
 */
class Haptics(private val view: View, private val vibrator: Vibrator?) {

    private val canVibrate = vibrator?.hasVibrator() == true

    fun perform(type: HapticType) {
        if (!canVibrate) {
            view.performHapticFeedback(
                when (type) {
                    HapticType.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    HapticType.Heavy -> HapticFeedbackConstants.LONG_PRESS
                    else -> HapticFeedbackConstants.VIRTUAL_KEY
                },
            )
            return
        }
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (type) {
                HapticType.Tick -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticType.Click -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticType.Heavy -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticType.Confirm -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticType.Reject -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
            }
        } else {
            when (type) {
                HapticType.Tick -> VibrationEffect.createOneShot(8, 120)
                HapticType.Click -> VibrationEffect.createOneShot(15, 200)
                HapticType.Heavy -> VibrationEffect.createOneShot(30, 255)
                HapticType.Confirm -> VibrationEffect.createWaveform(longArrayOf(0, 15, 60, 15), -1)
                HapticType.Reject -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
            }
        }
        vibrator?.vibrate(effect)
    }

    companion object {
        fun vibratorOf(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) { Haptics(view, Haptics.vibratorOf(context)) }
}
