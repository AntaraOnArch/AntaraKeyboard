package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.example.antarakeyboard.data.KeyboardPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.time.Duration.Companion.milliseconds

/** Pure color math of the RGB themes (no Android classes, so it is unit-testable). */
internal object RainbowMath {

    /** Steps of one full rainbow cycle: 6 segments × 256 steps. */
    const val CYCLE = 1536

    /**
     * Color at [x] on the rainbow Red→Yellow→Green→Cyan→Blue→Magenta→Red, as ARGB.
     * Any integer works; it wraps around the cycle (negative values too).
     */
    fun rainbowColor(x: Int): Int {
        val step = ((x % CYCLE) + CYCLE) % CYCLE
        val c = step % 256
        return when (step / 256) {
            0 -> argb(255, c, 0)           // Red to Yellow
            1 -> argb(255 - c, 255, 0)     // Yellow to Green
            2 -> argb(0, 255, c)           // Green to Cyan
            3 -> argb(0, 255 - c, 255)     // Cyan to Blue
            4 -> argb(c, 0, 255)           // Blue to Magenta
            else -> argb(255, 0, 255 - c)  // Magenta to Red
        }
    }

    /** Ease-in-out for smooth acceleration/deceleration, t in 0..1. */
    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    /** Step between [from] and [to] at [progress] (0..1), going the shorter way round. */
    fun interpolateStep(from: Int, to: Int, progress: Float): Int {
        var diff = to - from
        if (diff > CYCLE / 2) diff -= CYCLE
        if (diff < -CYCLE / 2) diff += CYCLE
        return (from + (diff * progress).toInt() + CYCLE) % CYCLE
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Multi-center radial rainbow: three overlapping radial gradients (screen-blended) centered
 * near the first letter of row 1, the last letter of row 2 and the 3rd letter of row 3.
 */
private class RadialRainbowDrawable(
    private val startStep: Int,
    private val saturation: Float,
    private val brightness: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val positions = FloatArray(NUM_COLORS) { i -> i.toFloat() / (NUM_COLORS - 1) }
    private val hsv = FloatArray(3)

    private fun buildColors(phaseOffset: Int): IntArray = IntArray(NUM_COLORS) { i ->
        val color = RainbowMath.rainbowColor(startStep + phaseOffset + i * RainbowMath.CYCLE / NUM_COLORS)
        // Saturation and brightness scale HSV S and V
        Color.colorToHSV(color, hsv)
        hsv[1] *= saturation
        hsv[2] *= brightness
        Color.HSVToColor(hsv)
    }

    /** The three layers: center (fraction of the bounds) and phase offset. */
    private val layers = listOf(
        Triple(0.12f, 0.15f, 0),     // base layer: first letter, row 1 (top left)
        Triple(0.88f, 0.42f, 512),   // last letter, row 2 (right middle)
        Triple(0.28f, 0.68f, 1024)   // 3rd letter, row 3 (left-center, lower)
    )
    private var shaders: List<Shader> = emptyList()

    // One drawable is one frame, so its shaders only change with the size
    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val radius = (maxOf(w, h) * 0.9f).coerceAtLeast(1f)
        shaders = layers.map { (cx, cy, phaseOffset) ->
            RadialGradient(
                bounds.left + w * cx, bounds.top + h * cy, radius,
                buildColors(phaseOffset), positions, Shader.TileMode.CLAMP
            )
        }
    }

    override fun draw(canvas: Canvas) {
        // The first layer is the base, the others are screen-blended on top
        shaders.forEachIndexed { i, shader ->
            paint.xfermode = if (i == 0) null else screen
            paint.shader = shader
            canvas.drawRect(bounds, paint)
        }
        paint.xfermode = null
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val NUM_COLORS = 7
    }
}

/** Settings of one RGB theme: Smooth cycles through the rainbow, Wild jumps to random colors. */
data class RgbSettings(
    val wild: Boolean,
    val speedMs: Int,
    val saturation: Float,
    val brightness: Float
) {
    companion object {
        fun smooth(context: Context) = RgbSettings(
            wild = false,
            speedMs = KeyboardPrefs.getRgbSmoothSpeed(context),
            saturation = KeyboardPrefs.getRgbSmoothSaturation(context),
            brightness = KeyboardPrefs.getRgbSmoothBrightness(context)
        )

        fun wild(context: Context) = RgbSettings(
            wild = true,
            speedMs = KeyboardPrefs.getRgbWildSpeed(context),
            saturation = KeyboardPrefs.getRgbWildSaturation(context),
            brightness = KeyboardPrefs.getRgbWildBrightness(context)
        )

        /** Settings of the RGB theme that is on, or null. */
        fun active(context: Context): RgbSettings? = when {
            KeyboardPrefs.isRgbSmoothEnabled(context) -> smooth(context)
            KeyboardPrefs.isRgbWildEnabled(context) -> wild(context)
            else -> null
        }
    }
}

/**
 * Runs the RGB Smooth / RGB Wild background animation (the keyboard and the settings preview). Each frame calls [onFrame]; the
 * keyboard then takes fresh drawables from [newDrawable] for every surface it paints.
 */
class RgbBackgroundAnimator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onFrame: () -> Unit
) {
    private var job: Job? = null
    private val random = Random()

    /** Smooth mode continues from here when it restarts. */
    private var smoothStep = 0
    private var step = 0
    private var saturation = 1f
    private var brightness = 1f

    val isRunning: Boolean get() = job != null

    /** Drawable showing the current frame (each surface needs its own instance). */
    fun newDrawable(): Drawable = RadialRainbowDrawable(step, saturation, brightness)

    /** Starts the RGB theme that is enabled; false when no RGB theme is on. */
    fun start(): Boolean {
        val settings = RgbSettings.active(context)
        if (settings == null) stop() else start(settings)
        return settings != null
    }

    fun start(settings: RgbSettings) {
        if (settings.wild) startWild(settings) else startSmooth(settings)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun showFrame(frameStep: Int, sat: Float, bright: Float) {
        step = frameStep
        saturation = sat
        brightness = bright
        onFrame()
    }

    private fun startSmooth(settings: RgbSettings) {
        stop()
        val sat = settings.saturation
        val bright = settings.brightness
        val delayPerStep = (settings.speedMs / RainbowMath.CYCLE.toFloat()).toLong().coerceAtLeast(8L)

        job = scope.launch {
            while (isActive) {
                smoothStep = (smoothStep + 4) % RainbowMath.CYCLE
                showFrame(smoothStep, sat, bright)
                delay(delayPerStep.milliseconds)
            }
        }
    }

    private fun startWild(settings: RgbSettings) {
        stop()
        val speedMs = settings.speedMs
        val sat = settings.saturation
        val bright = settings.brightness
        val transitionDuration = (speedMs * 0.85f).toLong()  // 85 % of the interval moves

        job = scope.launch {
            var startStep = 0
            var targetStep = random.nextInt(RainbowMath.CYCLE)
            var transitionStart = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = now - transitionStart
                val progress = RainbowMath.easeInOutCubic(
                    (elapsed.toFloat() / transitionDuration).coerceIn(0f, 1f)
                )
                showFrame(RainbowMath.interpolateStep(startStep, targetStep, progress), sat, bright)

                // New random target once the interval is over
                if (elapsed >= speedMs) {
                    startStep = targetStep
                    targetStep = random.nextInt(RainbowMath.CYCLE)
                    transitionStart = now
                }
                delay(FRAME_INTERVAL_MS.milliseconds)
            }
        }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 16L  // ~60 fps
    }
}
