package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var wheelRadius = 0f
    private var thumbRadius = 0f

    private var hue = 0f
    private var saturation = 1f

    private var onColorChanged: ((Float, Float) -> Unit)? = null

    init {
        thumbPaint.style = Paint.Style.FILL
        thumbStrokePaint.style = Paint.Style.STROKE
        thumbStrokePaint.strokeWidth = 4f
        thumbStrokePaint.color = Color.WHITE
    }

    fun setOnColorChangedListener(listener: (Float, Float) -> Unit) {
        onColorChanged = listener
    }

    fun setHueSaturation(h: Float, s: Float) {
        hue = h
        saturation = s
        invalidate()
    }

    fun getHue(): Float = hue
    fun getSaturation(): Float = saturation

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        wheelRadius = min(w, h) / 2f * 0.85f
        thumbRadius = wheelRadius * 0.08f
    }

    override fun onDraw(canvas: Canvas) {
        // Draw color wheel (hue) - ISPRAVAN REDOSLIJED
        val sweepShader = SweepGradient(
            centerX, centerY,
            intArrayOf(
                Color.RED,     // 0°   = desno
                Color.YELLOW,  // 60°
                Color.GREEN,   // 120° = dolje-lijevo
                Color.CYAN,    // 180° = lijevo
                Color.BLUE,    // 240° = gore-lijevo
                Color.MAGENTA, // 300° = gore-desno
                Color.RED      // 360° = desno
            ),
            null
        )
        wheelPaint.shader = sweepShader
        canvas.drawCircle(centerX, centerY, wheelRadius, wheelPaint)

        // Draw saturation gradient (white to transparent)
        val radialShader = RadialGradient(
            centerX, centerY, wheelRadius,
            Color.WHITE, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        saturationPaint.shader = radialShader
        canvas.drawCircle(centerX, centerY, wheelRadius, saturationPaint)

        // Draw thumb
        val thumbAngle = Math.toRadians(hue.toDouble())
        val thumbDistance = saturation * wheelRadius
        val thumbX = centerX + (thumbDistance * cos(thumbAngle)).toFloat()
        val thumbY = centerY + (thumbDistance * sin(thumbAngle)).toFloat()

        val thumbColor = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        thumbPaint.color = thumbColor

        // Thumb shadow
        canvas.drawCircle(thumbX, thumbY, thumbRadius + 2f, Paint().apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
        })

        // Draw thumb
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                hue = if (angle < 0) angle + 360 else angle
                saturation = (distance / wheelRadius).coerceIn(0f, 1f)

                onColorChanged?.invoke(hue, saturation)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}