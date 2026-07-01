package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.graphics.RectF
import com.example.antarakeyboard.model.KeyShape

class ShapePreviewView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var shape: KeyShape = KeyShape.HEX
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
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
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
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

//    private fun drawCube(c: Canvas) {
//        val w = width.toFloat()
//        val h = height.toFloat()
//        val pad = minOf(w, h) * 0.20f
//        val dx = pad * 0.65f
//        val dy = pad * 0.65f
//
//        val front = Path().apply {
//            moveTo(pad, pad + dy)
//            lineTo(w - pad - dx, pad + dy)
//            lineTo(w - pad - dx, h - pad)
//            lineTo(pad, h - pad)
//            close()
//        }
//
//        val top = Path().apply {
//            moveTo(pad, pad + dy)
//            lineTo(pad + dx, pad)
//            lineTo(w - pad, pad)
//            lineTo(w - pad - dx, pad + dy)
//            close()
//        }
//
//        val side = Path().apply {
//            moveTo(w - pad - dx, pad + dy)
//            lineTo(w - pad, pad)
//            lineTo(w - pad, h - pad - dy)
//            lineTo(w - pad - dx, h - pad)
//            close()
//        }
//
//        c.drawPath(front, paint)
//        c.drawPath(top, paint)
//        c.drawPath(side, paint)
//    }
