package com.example.antarakeyboard.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Manages haptic feedback for keyboard interactions.
 * Provides different vibration patterns for various key actions.
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var enabled: Boolean = true

    /**
     * Enable or disable haptic feedback
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Slightly stronger feedback for special keys (shift, backspace, enter)
     */
    fun onSpecialKey() {
        if (!enabled) return
        vibrate(DURATION_SPECIAL_KEY)
    }

    /**
     * Feedback for long press trigger
     */
    fun onLongPress() {
        if (!enabled) return
        vibrate(DURATION_LONG_PRESS)
    }

    /**
     * Use system haptic feedback on a view (preferred method)
     */
    fun performHapticFeedback(view: View) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun vibrate(durationMs: Long) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    companion object {
        private const val DURATION_SPECIAL_KEY = 20L    // Shift, backspace, enter
        private const val DURATION_LONG_PRESS = 30L     // Long press trigger
    }
}
