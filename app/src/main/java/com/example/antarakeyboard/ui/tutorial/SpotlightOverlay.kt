package com.example.antarakeyboard.ui.tutorial

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.antarakeyboard.R
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.extensions.dpF

/**
 * Coach mark over the whole screen: dims everything except [target], pulses a ring around it and
 * shows a card with [title] / [text] and a Skip button next to it. Touches inside the hole reach
 * the target (the user taps the real button); everything else is blocked.
 */
@SuppressLint("ViewConstructor")
class SpotlightOverlay(
    context: Context,
    private val target: View,
    title: String,
    text: String,
    onSkip: () -> Unit
) : FrameLayout(context) {

    private companion object {
        const val PULSE_MS = 1200L
        const val SCRIM = 0xB3000000.toInt()
    }

    private val hole = RectF()
    private val holePath = Path()
    private val holePad = 6f.dpF(this)
    private val holeRadius = 16f.dpF(this)
    private val accent = ContextCompat.getColor(context, R.color.special_fill)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SCRIM }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accent
    }
    private var pulse = 0f
    private var passThrough = false

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_MS
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    // The target can move (layout, scroll, rotation): follow it before every frame
    private val follow = ViewTreeObserver.OnPreDrawListener {
        updateHole()
        true
    }

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = 16.dp(this)
        setPadding(pad, pad, pad, 8.dp(this))
        background = GradientDrawable().apply {
            cornerRadius = 16f.dpF(this@SpotlightOverlay)
            setColor(ContextCompat.getColor(context, R.color.main_app_bg))
            setStroke(2.dp(this@SpotlightOverlay), accent)
        }
        addView(TextView(context).apply {
            this.text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 6.dp(this), 0, 0)
        })
        addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            setText(R.string.tutorial_skip)
            isAllCaps = false
            setTextColor(accent)
            setOnClickListener { onSkip() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END })
    }

    init {
        setWillNotDraw(false)
        addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            val margin = 16.dp(this@SpotlightOverlay)
            setMargins(margin, 0, margin, 0)
        })
        // TalkBack announces the coach mark when it appears
        ViewCompat.setAccessibilityPaneTitle(this, title)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(follow)
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        viewTreeObserver.removeOnPreDrawListener(follow)
        super.onDetachedFromWindow()
    }

    private fun updateHole() {
        val self = IntArray(2).also { getLocationInWindow(it) }
        val pos = IntArray(2).also { target.getLocationInWindow(it) }
        val left = (pos[0] - self[0]).toFloat()
        val top = (pos[1] - self[1]).toFloat()
        hole.set(left - holePad, top - holePad, left + target.width + holePad, top + target.height + holePad)

        // Card under the target, above it when there is no room below
        val gap = 20.dp(this)
        val below = hole.bottom + gap
        val y = if (below + card.height <= height) below else (hole.top - gap - card.height).coerceAtLeast(0f)
        if (card.translationY != y) card.translationY = y
    }

    override fun dispatchDraw(canvas: Canvas) {
        holePath.reset()
        holePath.fillType = Path.FillType.EVEN_ODD
        holePath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        holePath.addRoundRect(hole, holeRadius, holeRadius, Path.Direction.CW)
        canvas.drawPath(holePath, scrimPaint)

        // A ring grows out of the hole and fades: "tap here"
        val grow = pulse * 14f.dpF(this)
        ringPaint.strokeWidth = 3f.dpF(this)
        ringPaint.alpha = ((1f - pulse) * 255).toInt()
        canvas.drawRoundRect(
            hole.left - grow, hole.top - grow, hole.right + grow, hole.bottom + grow,
            holeRadius + grow, holeRadius + grow, ringPaint
        )
        ringPaint.alpha = 255
        canvas.drawRoundRect(hole, holeRadius, holeRadius, ringPaint)

        super.dispatchDraw(canvas)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // A gesture that starts in the hole goes to the target under the overlay
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            passThrough = hole.contains(ev.x, ev.y)
        }
        if (passThrough) return false
        super.dispatchTouchEvent(ev)
        return true
    }
}
