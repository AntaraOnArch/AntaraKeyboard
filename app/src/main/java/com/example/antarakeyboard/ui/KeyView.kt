package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.model.KeyShape
import kotlin.math.min

class KeyView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(ctx, attrs) {

    var shape: KeyShape = KeyShape.HEX
        set(value) {
            field = value
            invalidate()
        }

    var isSpecial: Boolean = false
        set(value) {
            field = value
            applyTextColor()
            invalidate()
        }

    var hideStroke: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var hideFill: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var manualLabelSizeSp: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    var customBgColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var triangleFlipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var forceSquare: Boolean = true
        set(value) {
            field = value
            requestLayout()
        }

    var hideCompletely: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /* ───────── SPLIT KEY MODE ───────── */

    var useSplitLabels: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var mainLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var swipeUpLabel: String? = null
        set(value) {
            field = value
            invalidate()
        }


    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.25f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = false
    }

    private val splitMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = false
    }

    private val splitSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = false
    }



    private val path = Path()


    init {
        applyThemeColors()
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        isAllCaps = false
        applyTextColor()
        maxLines = 1
        ellipsize = null
        setTextColor(themeColor(R.attr.keyText, 0xFFFFFFFF.toInt()))
    }

    override fun setPressed(pressed: Boolean) {
        val changed = pressed != isPressed
        super.setPressed(pressed)
        if (changed) invalidate()
    }

    private fun applyThemeColors() {
        fill.color = themeColor(R.attr.keyFill, 0xFF777777.toInt())
        stroke.color = themeColor(R.attr.keyStroke, 0xFF222222.toInt())
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        val ok = context.theme.resolveAttribute(attr, tv, true)
        return if (
            ok && tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            fallback
        }
    }

    private fun applyTextColor() {
        val c = if (isSpecial) {
            themeColor(R.attr.enterText, 0xFFFFFFFF.toInt())
        } else {
            themeColor(R.attr.keyText, 0xFFFFFFFF.toInt())
        }
        setTextColor(c)
    }

    private fun resolvedTextColor(): Int {
        return if (isSpecial) {
            themeColor(R.attr.enterText, 0xFFFFFFFF.toInt())
        } else {
            currentTextColor
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (!forceSquare) return

        // HEX_TALL mora smjeti ostati viši od širine.
        // Inače LayoutParams height iz MyKeyboardService nema nikakav efekt.
        if (shape == KeyShape.HEX_TALL) {
            return
        }

        val s = min(measuredWidth, measuredHeight)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        if (hideCompletely) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 2f || h <= 2f) {
            super.onDraw(canvas)
            return
        }

        val pressed = isPressed

        val keyFill = themeColor(R.attr.keyFill, 0xFF777777.toInt())
        val keyFillPressed = themeColor(R.attr.keyFillPressed, keyFill)
        val keyStroke = themeColor(R.attr.keyStroke, 0xFF222222.toInt())

        val specialFill = themeColor(R.attr.enterFill, 0xFF2E55E7.toInt())
        val specialFillPressed = themeColor(R.attr.enterFillPressed, specialFill)

        val bg = customBgColor ?: when {
            isSpecial -> if (pressed) specialFillPressed else specialFill
            pressed -> keyFillPressed
            else -> keyFill
        }

        fill.color = bg
        stroke.color = keyStroke

        val inset = stroke.strokeWidth * 0.5f + dpF(0.75f)
        val l = inset
        val t = inset
        val r = w - inset
        val b = h - inset

        if (r <= l || b <= t) {
            super.onDraw(canvas)
            return
        }

        path.reset()
        buildShapePath(path, l, t, r, b)

        if (!hideFill) {
            canvas.drawPath(path, fill)
        }
        if (useSplitLabels) {
            drawTopHalfOverlay(canvas, path, l, t, r, b, bg, pressed)
        }

        if (!hideStroke) {
            canvas.drawPath(path, stroke)
        }

        if (useSplitLabels) {
            drawSplitLabels(canvas, l, t, r, b)
        } else {
            drawDefaultLabel(canvas, l, t, r, b)
        }
    }


    private fun drawTopHalfOverlay(
        canvas: Canvas,
        shapePath: Path,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        baseColor: Int,
        pressed: Boolean
    ) {
        val h = b - t

        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (pressed) {
                lightenColor(baseColor, 0.22f)
            } else {
                lightenColor(baseColor, 0.12f)
            }
        }

        val overlayPath = Path().apply {
            addRect(
                l,
                t,
                r,
                t + h * 0.48f,
                Path.Direction.CW
            )
        }

        val save = canvas.save()
        canvas.clipPath(shapePath)
        canvas.drawPath(overlayPath, overlayPaint)
        canvas.restoreToCount(save)
    }
    private fun lightenColor(color: Int, amount: Float): Int {
        val a = android.graphics.Color.alpha(color)
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)

        val nr = (r + (255 - r) * amount).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * amount).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * amount).toInt().coerceIn(0, 255)

        return android.graphics.Color.argb(a, nr, ng, nb)
    }
    private fun drawSplitLabels(
        canvas: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        val textColor = resolvedTextColor()
        val w = r - l
        val h = b - t
        val cx = (l + r) * 0.5f

        splitMainPaint.color = textColor
        splitSmallPaint.color = withAlpha(textColor, 220)

        splitMainPaint.textAlign = Paint.Align.CENTER
        splitSmallPaint.textAlign = Paint.Align.CENTER

        splitMainPaint.textSize = (manualLabelSizeSp ?: 14f) * resources.displayMetrics.scaledDensity
        splitSmallPaint.textSize = splitMainPaint.textSize * 0.52f

        swipeUpLabel?.takeIf { it.isNotBlank() }?.let { txt ->
            val fm = splitSmallPaint.fontMetrics
            val baseline = (t + h * 0.24f) - (fm.ascent + fm.descent) / 2f
            canvas.drawText(txt, cx, baseline, splitSmallPaint)
        }

        val mainText = mainLabel.ifBlank { text?.toString().orEmpty() }
        if (mainText.isNotBlank()) {
            val fm = splitMainPaint.fontMetrics
            val baseline = (t + h * 0.70f) - (fm.ascent + fm.descent) / 2f
            canvas.drawText(mainText, cx, baseline, splitMainPaint)
        }
    }

    private fun drawDefaultLabel(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val label = text?.toString().orEmpty()
        if (label.isEmpty()) return

        textPaint.color = resolvedTextColor()

        val sizeSp = manualLabelSizeSp ?: when {
            label.length == 1 -> {
                when (shape) {
                    KeyShape.HEX,
                    KeyShape.HEX_TALL,
                    KeyShape.HEX_HALF_LEFT,
                    KeyShape.HEX_HALF_RIGHT -> 15f
                    KeyShape.TRIANGLE -> 14f
                    KeyShape.CIRCLE -> 15f
                    KeyShape.CUBE -> 15f
                }
            }

            label in setOf("⇧", "⌫", "↵", "123", "ABC", "abc") -> 13f
            else -> 12f
        }

        textPaint.textSize = sizeSp * resources.displayMetrics.scaledDensity

        val baseCx = (l + r) * 0.5f
        val cx = when (shape) {
            KeyShape.HEX_HALF_LEFT -> baseCx + (r - l) * 0.10f
            KeyShape.HEX_HALF_RIGHT -> baseCx - (r - l) * 0.10f
            else -> baseCx
        }
        val cy = (t + b) * 0.5f

        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f

        canvas.drawText(label, cx, baseline, textPaint)
    }

    private fun buildShapePath(p: Path, l: Float, t: Float, r: Float, b: Float) {
        when (shape) {
            KeyShape.HEX -> buildHex(p, l, t, r, b)
            KeyShape.HEX_TALL -> buildTallHex(p, l, t, r, b)

            KeyShape.HEX_HALF_LEFT -> buildHalfHexLeft(p, l, t, r, b)
            KeyShape.HEX_HALF_RIGHT -> buildHalfHexRight(p, l, t, r, b)
            KeyShape.TRIANGLE -> buildTriangle(p, l, t, r, b, triangleFlipped)
            KeyShape.CIRCLE -> buildCircle(p, l, t, r, b)
            KeyShape.CUBE -> buildCubeFront(p, l, t, r, b)
        }
    }

    private fun buildHex(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val cx = l + w * 0.5f
        val cy = t + h * 0.5f

        val radius = min(w, h) * 0.48f
        val dx = 0.8660254f * radius
        val dy = 0.5f * radius

        p.moveTo(cx, cy - radius)
        p.lineTo(cx + dx, cy - dy)
        p.lineTo(cx + dx, cy + dy)
        p.lineTo(cx, cy + radius)
        p.lineTo(cx - dx, cy + dy)
        p.lineTo(cx - dx, cy - dy)
        p.close()
    }

    private fun buildTallHex(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val cx = l + w * 0.5f
        val cy = t + h * 0.5f

        // isti hex kut kao buildHex, samo rastegnut po visini
        val rx = w * 0.48f
        val ry = h * 0.48f

        val dx = 0.8660254f * rx
        val dy = 0.5f * ry

        p.reset()
        p.moveTo(cx, cy - ry)
        p.lineTo(cx + dx, cy - dy)
        p.lineTo(cx + dx, cy + dy)
        p.lineTo(cx, cy + ry)
        p.lineTo(cx - dx, cy + dy)
        p.lineTo(cx - dx, cy - dy)
        p.close()
    }

    private fun buildHalfHexLeft(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val cy = t + h * 0.5f
        val insetX = w * 0.18f

        p.moveTo(r, t)
        p.lineTo(r, b)
        p.lineTo(l + insetX, b)
        p.lineTo(l, cy)
        p.lineTo(l + insetX, t)
        p.close()
    }

    private fun buildHalfHexRight(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val cy = t + h * 0.5f
        val insetX = w * 0.18f

        p.moveTo(l, t)
        p.lineTo(r - insetX, t)
        p.lineTo(r, cy)
        p.lineTo(r - insetX, b)
        p.lineTo(l, b)
        p.close()
    }

    private fun buildTriangle(
        p: Path,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        flipped: Boolean
    ) {
        if (!flipped) {
            p.moveTo(l + (r - l) * 0.5f, t)
            p.lineTo(l, b)
            p.lineTo(r, b)
        } else {
            p.moveTo(l, t)
            p.lineTo(r, t)
            p.lineTo(l + (r - l) * 0.5f, b)
        }
        p.close()
    }

    private fun buildCircle(p: Path, l: Float, t: Float, r: Float, b: Float) {
        p.addOval(l, t, r, b, Path.Direction.CW)
    }

    private fun buildCubeFront(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        val pad = min(w, h) * 0.06f
        val rr = min(w, h) * 0.14f
        p.addRoundRect(
            l + pad,
            t + pad,
            r - pad,
            b - pad,
            rr,
            rr,
            Path.Direction.CW
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun dpF(v: Float): Float = v * resources.displayMetrics.density
}