package com.example.antarakeyboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.antarakeyboard.R
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.extensions.dpF

/** Pulses of [flashHighlight]; each fades in and out. */
private const val FLASH_PULSES = 2
private const val FLASH_PULSE_MS = 450L

/**
 * Draws attention to a control (a Help speed link jumped to it): a rounded accent outline with a light
 * fill pulses over the view [FLASH_PULSES] times, then the view's own foreground comes back.
 */
fun View.flashHighlight() {
    val accent = ContextCompat.getColor(context, R.color.special_fill)
    val outline = GradientDrawable().apply {
        cornerRadius = 12f.dpF(this@flashHighlight)
        setColor(ColorUtils.setAlphaComponent(accent, 0x33))
        setStroke(2.dp(this@flashHighlight), accent)
        alpha = 0
    }
    val previous = foreground
    foreground = outline

    ValueAnimator.ofInt(0, 255).apply {
        duration = FLASH_PULSE_MS / 2
        repeatCount = FLASH_PULSES * 2 - 1
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { outline.alpha = it.animatedValue as Int }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                foreground = previous
            }
        })
        start()
    }
}
