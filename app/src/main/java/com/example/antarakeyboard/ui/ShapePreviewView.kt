package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.graphics.RectF
import com.example.antarakeyboard.data.ClassicLayouts
import com.example.antarakeyboard.model.KeyShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ShapePreviewView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var shape: KeyShape = KeyShape.HEX
        set(value) {
            field = value
            invalidate()
        }

    /** Classic + numbers: whether the preview shows the number row. */
    var numberRow: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY  // Always gray regardless of theme
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (shape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> drawHex(canvas)

            KeyShape.TRIANGLE -> drawTriangle(canvas)
            KeyShape.CIRCLE -> drawCircle(canvas)
            KeyShape.CUBE -> drawCube(canvas)
            KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> drawClassic(canvas, shape)
        }
    }

    /** A small classic keyboard: its default rows with the real key widths. */
    private fun drawClassic(c: Canvas, shape: KeyShape) {
        val layout = ClassicLayouts.defaultAlphabet(ClassicLayouts.layoutId(shape, 0))
        if (shape == KeyShape.CLASSIC_NUMBERS && !numberRow) layout.rows.removeAt(0)
        val w = width.toFloat()
        val unit = w / ClassicLayouts.ROW_UNITS
        val rowH = unit * 1.3f
        val gap = unit * 0.08f
        var top = (height - rowH * layout.rows.size) / 2f
        val rect = RectF()
        layout.rows.forEach { row ->
            val labels = row.keys.map { it.label }
            val widths = ClassicLayouts.keyWidths(shape, labels)
            var left = (w - widths.sum() * unit) / 2f
            widths.forEach { units ->
                rect.set(left + gap, top + gap, left + units * unit - gap, top + rowH - gap)
                c.drawRoundRect(rect, unit * 0.15f, unit * 0.15f, paint)
                left += units * unit
            }
            top += rowH
        }
    }

    private fun drawHex(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) * 0.42f
        val cx = w / 2f
        val cy = h / 2f

        val p = Path()
        for (i in 0..5) {
            val angle = (60 * i - 30) * PI / 180
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }

        p.close()
        c.drawPath(p, paint)
    }

    private fun drawTriangle(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = minOf(w, h) * 0.12f

        val p = Path().apply {
            moveTo(w / 2f, pad)
            lineTo(pad, h - pad)
            lineTo(w - pad, h - pad)
            close()
        }

        c.drawPath(p, paint)
    }

    private fun drawCircle(c: Canvas) {
        val r = minOf(width, height) * 0.40f
        c.drawCircle(width / 2f, height / 2f, r, paint)
    }

    private fun drawCube(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val size = minOf(w, h) * 0.58f
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        val right = left + size
        val bottom = top + size

        val radius = size * 0.10f

        c.drawRoundRect(
            RectF(left, top, right, bottom),
            radius,
            radius,
            paint
        )
    }
}
