package com.example.antarakeyboard.ui.tutorial

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.antarakeyboard.R
import com.example.antarakeyboard.extensions.dpF
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyShape
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Looping animated demo of one tutorial gesture or feature: a few keys in the keyboard's colors,
 * a finger that taps / swipes / holds them and a text field above with the result. Drawn in code
 * (no GIFs), so it follows the theme, the key shape and the keyboard's script.
 */
class TutorialDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        /** Keys of the Restore demo; one of them is tapped each round. */
        val RESTORE_KEYS = listOf("a", "s", "d", "f")
    }

    enum class Demo(val cycleMs: Long, val hasField: Boolean = true) {
        CAPITALS(2600), CAPS_LOCK(3800), PUNCTUATION(3200), DELETE(3000), RESTORE(4200),
        LONG_PRESS(3400), EMOJI(3600), SWAP(4400, hasField = false), BIND_EMOJI(4600),
        THEMES(6400, hasField = false)
    }

    var demo = Demo.CAPITALS
        set(value) {
            field = value
            restart()
        }

    /** Latin key letter → its label on the keyboard (Cyrillic / Greek script). */
    var letter: (String) -> String = { it }
    var longPressKey = "e"
    var longPressLetters = listOf("é", "è")
    var word = "Antara"
    var themeNames = listOf("", "", "", "")
    var shape = KeyShape.HEX

    private val bg = ContextCompat.getColor(context, R.color.keyboard_bg)
    private val keyFill = ContextCompat.getColor(context, R.color.key_fill)
    private val keyFillPressed = ContextCompat.getColor(context, R.color.key_fill_pressed)
    private val keyStroke = ContextCompat.getColor(context, R.color.key_stroke)
    private val keyText = ContextCompat.getColor(context, R.color.key_text)
    private val accent = ContextCompat.getColor(context, R.color.special_fill)
    private val accentText = ContextCompat.getColor(context, R.color.special_text)
    private val fieldBg = ContextCompat.getColor(context, R.color.main_button_bg)
    private val fieldBorder = ContextCompat.getColor(context, R.color.main_button_border)
    private val fieldText = ContextCompat.getColor(context, R.color.main_button_text)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { pathEffect = CornerPathEffect(4f.dpF(this@TutorialDemoView)) }
    private val path = Path()
    private val rect = RectF()

    private val pad = 12f.dpF(this)
    private val keyR = 24f.dpF(this)
    private val field = RectF()
    private val board = RectF()
    private var keyY = 0f
    private var popupY = 0f

    private var t = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            // Restore demo: another key every round
            override fun onAnimationRepeat(animation: Animator) {
                tapKey = (tapKey + 1 + Random.nextInt(RESTORE_KEYS.size - 1)) % RESTORE_KEYS.size
            }
        })
    }
    private var tapKey = 1

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun restart() {
        animator.cancel()
        animator.duration = demo.cycleMs
        layoutAreas()
        if (isAttachedToWindow) animator.start()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutAreas()

    private fun layoutAreas() {
        val top = if (demo.hasField) {
            field.set(pad, 4f.dpF(this), width - pad, 40f.dpF(this))
            field.bottom + 8f.dpF(this)
        } else 0f
        board.set(0f, top, width.toFloat(), height.toFloat())
        keyY = height - keyR - 10f.dpF(this)
        popupY = keyY - 2.4f * keyR
    }

    override fun onDraw(canvas: Canvas) {
        fill.color = bg
        canvas.drawRoundRect(board, 16f.dpF(this), 16f.dpF(this), fill)
        when (demo) {
            Demo.CAPITALS -> drawCapitals(canvas)
            Demo.CAPS_LOCK -> drawCapsLock(canvas)
            Demo.PUNCTUATION -> drawPunctuation(canvas)
            Demo.DELETE -> drawDeleteRestore(canvas)
            Demo.RESTORE -> drawRestoreRound(canvas)
            Demo.LONG_PRESS -> drawLongPress(canvas)
            Demo.EMOJI -> drawEmoji(canvas)
            Demo.SWAP -> drawSwap(canvas)
            Demo.BIND_EMOJI -> drawBindEmoji(canvas)
            Demo.THEMES -> drawThemes(canvas)
        }
    }

    /* ───────── demos ───────── */

    private fun drawCapitals(canvas: Canvas) {
        val labels = listOf("s", "a", "d").map(letter)
        val swipe = ease(seg(.22f, .48f))
        val pressed = t in .16f..0.5f
        labels.forEachIndexed { i, label ->
            val up = i == 1 && swipe > .45f
            drawKey(canvas, keyX(i, 3), keyY, if (up) label.uppercase() else label,
                fillColor = if (i == 1 && pressed) keyFillPressed else keyFill)
        }
        drawField(canvas, if (t >= .48f) labels[1].uppercase() else "")
        val y = keyY - swipe * 1.7f * keyR
        val alpha = seg(.08f, .16f) * (1 - seg(.56f, .64f))
        drawTrail(canvas, keyX(1, 3), keyY, keyX(1, 3), y, alpha)
        drawFinger(canvas, keyX(1, 3), y, alpha, pressed)
    }

    private fun drawCapsLock(canvas: Canvas) {
        val shiftOn = t in .10f..0.80f
        val letters = listOf("o", "k").map(letter)
        val labels = listOf(if (shiftOn) KeyMarkers.SHIFT_ON else KeyMarkers.SHIFT_OFF) +
            letters.map { if (shiftOn) it.uppercase() else it }
        val taps = listOf(.10f to 0, .34f to 1, .54f to 2, .80f to 0)
        labels.forEachIndexed { i, label ->
            val down = taps.any { (at, key) -> key == i && t in at..(at + .05f) }
            drawKey(canvas, keyX(i, 3), keyY, label, fillColor = if (down) keyFillPressed else keyFill)
        }
        val typed = buildString {
            if (t >= .34f) append(letters[0].uppercase())
            if (t >= .54f) append(letters[1].uppercase())
        }
        drawField(canvas, typed)
        drawTaps(canvas, taps.map { (at, key) -> Tap(at, keyX(key, 3), keyY) })
    }

    private fun drawPunctuation(canvas: Canvas) {
        val keys = listOf("." to ",", "?" to "!")
        val s0 = ease(seg(.12f, .36f))
        val s1 = ease(seg(.56f, .80f))
        keys.forEachIndexed { i, (main, sub) ->
            val swiping = (i == 0 && t in .10f..0.38f) || (i == 1 && t in .54f..0.82f)
            drawKey(canvas, keyX(i, 2), keyY, main, sub = sub, subHighlighted = swiping,
                fillColor = if (swiping) keyFillPressed else keyFill)
        }
        drawField(canvas, (if (t >= .36f) "," else "") + (if (t >= .80f) "!" else ""))

        val (x, y) = when {
            t < .42f -> keyX(0, 2) to keyY - s0 * 1.7f * keyR
            t < .54f -> {
                val f = ease(seg(.42f, .54f))
                lerp(keyX(0, 2), keyX(1, 2), f) to lerp(keyY - 1.7f * keyR, keyY, f)
            }
            else -> keyX(1, 2) to keyY - s1 * 1.7f * keyR
        }
        val alpha = seg(.04f, .10f) * (1 - seg(.86f, .92f))
        if (t < .42f) drawTrail(canvas, keyX(0, 2), keyY, x, y, alpha)
        if (t >= .54f) drawTrail(canvas, keyX(1, 2), keyY, x, y, alpha)
        drawFinger(canvas, x, y, alpha,
            pressed = t in .12f..0.38f || t in .56f..0.82f)
    }

    private fun drawDeleteRestore(canvas: Canvas) {
        val labels = listOf("a", "s", "d", "f").map(letter)
        labels.forEachIndexed { i, label -> drawKey(canvas, keyX(i, 4), keyY, label) }
        val p = ease(seg(.16f, .62f))
        drawField(canvas, word.take(ceil(word.length * (1 - p) - 0.001f).toInt()))

        val from = keyX(3, 4) + keyR
        val to = keyX(0, 4) - keyR
        val x = lerp(from, to, p)
        val alpha = seg(.06f, .14f) * (1 - seg(.66f, .74f))
        drawTrail(canvas, from, keyY, x, keyY, alpha)
        drawFinger(canvas, x, keyY, alpha, pressed = t in .14f..0.64f)
    }

    /** One round of the Restore step: tap a random key, swipe left (gone), swipe right (back). */
    private fun drawRestoreRound(canvas: Canvas) {
        val labels = RESTORE_KEYS.map(letter)
        val tapping = t in .08f..0.16f
        labels.forEachIndexed { i, label ->
            drawKey(canvas, keyX(i, 4), keyY, label,
                fillColor = if (i == tapKey && tapping) keyFillPressed else keyFill)
        }
        val typed = t >= .14f && t !in .36f..<.70f
        drawField(canvas, if (typed) labels[tapKey] else "")

        val left = keyX(0, 4) - keyR
        val right = keyX(3, 4) + keyR
        val del = ease(seg(.24f, .48f))
        val res = ease(seg(.58f, .82f))
        val (x, alpha) = when {
            t < .20f -> keyX(tapKey, 4) to seg(.02f, .08f)
            t < .24f -> lerp(keyX(tapKey, 4), right, ease(seg(.18f, .24f))) to 1f
            t < .52f -> lerp(right, left, del) to 1f
            else -> lerp(left, right, res) to 1 - seg(.86f, .94f)
        }
        if (t in .24f..0.50f) drawTrail(canvas, right, keyY, x, keyY, alpha)
        if (t in .58f..0.86f) drawTrail(canvas, left, keyY, x, keyY, alpha)
        drawFinger(canvas, x, keyY, alpha, pressed = tapping || t in .24f..0.48f || t in .58f..0.82f)
    }

    private fun drawLongPress(canvas: Canvas) {
        val labels = listOf(letter("q"), longPressKey, letter("m"))
        val x = keyX(1, 3)
        labels.forEachIndexed { i, label ->
            drawKey(canvas, keyX(i, 3), keyY, label,
                fillColor = if (i == 1 && t in .10f..0.64f) keyFillPressed else keyFill)
        }
        val cells = longPressLetters.take(4).ifEmpty { listOf(longPressKey.uppercase()) }
        val cellXs = cellXs(x, cells.size)
        val popup = t in .34f..0.66f
        val slide = ease(seg(.40f, .58f))
        val fx = lerp(x, cellXs.last(), slide)
        val fy = lerp(keyY, popupY, slide)
        if (popup) drawPopup(canvas, cells, cellXs, selected = nearest(cellXs, fx))

        drawField(canvas, if (t >= .64f) cells.last() else "")
        drawHoldRing(canvas, x, keyY, seg(.10f, .34f), visible = t in .10f..0.36f)
        drawFinger(canvas, fx, fy, seg(.04f, .10f) * (1 - seg(.66f, .74f)), pressed = t in .10f..0.64f)
    }

    private fun drawEmoji(canvas: Canvas) {
        val keys = listOf(letter("m") to null, "123" to "😊", "↵" to null)
        val swipe = ease(seg(.10f, .30f))
        keys.forEachIndexed { i, (label, sub) ->
            drawKey(canvas, keyX(i, 3), keyY, label, sub = sub, subHighlighted = i == 1 && t in .08f..0.32f,
                fillColor = if (i == 2) accent else keyFill, textColor = if (i == 2) accentText else keyText)
        }
        drawTrail(canvas, keyX(1, 3), keyY, keyX(1, 3), keyY - swipe * 1.7f * keyR, 1 - seg(.30f, .36f))

        // The emoji panel covers the keys, like on the keyboard
        val panel = seg(.30f, .36f) * (1 - seg(.80f, .86f))
        val emoji = listOf("😀", "😂", "😍", "👍", "🎉", "❤", "🔥", "😎", "🙏", "✨")
        val cols = 5
        val cell = (board.width() - 2 * pad) / cols
        val rowH = (height - board.top - 2 * pad) / 2
        fun emojiX(i: Int) = pad + cell * (i % cols + .5f)
        fun emojiY(i: Int) = board.top + pad + rowH * (i / cols + .5f)
        if (panel > 0) {
            fill.color = ColorUtils.setAlphaComponent(bg, (panel * 255).toInt())
            canvas.drawRoundRect(board, 16f.dpF(this), 16f.dpF(this), fill)
            text.textSize = 24f.dpF(this)
            text.alpha = (panel * 255).toInt()
            emoji.forEachIndexed { i, e -> canvas.drawText(e, emojiX(i), centerY(emojiY(i)), text) }
            text.alpha = 255
        }
        drawField(canvas, if (t >= .56f) emoji[2] else "")

        val move = ease(seg(.38f, .54f))
        val fx = lerp(keyX(1, 3), emojiX(2), move)
        val fy = lerp(keyY - 1.7f * keyR, emojiY(2), move)
        drawFinger(canvas, fx, if (t < .38f) keyY - swipe * 1.7f * keyR else fy,
            seg(.04f, .10f) * (1 - seg(.62f, .70f)), pressed = t in .10f..0.30f || t in .54f..0.58f)
    }

    private fun drawSwap(canvas: Canvas) {
        // Two halves: swap q and e, then swap them back
        val h = (t * 2) % 1f
        val back = t >= .5f
        val labels = listOf("q", "w", "e", "r").map(letter).toMutableList()
        if (back) labels[0] = labels[2].also { labels[2] = labels[0] }
        val f = ease(seg(.45f, .75f, h))
        val selected = h in .15f..0.45f

        labels.forEachIndexed { i, label ->
            when (i) {
                0 -> drawKey(canvas, lerp(keyX(0, 4), keyX(2, 4), f), keyY - sin(PI * f).toFloat() * keyR,
                    label, outline = selected)
                2 -> drawKey(canvas, lerp(keyX(2, 4), keyX(0, 4), f), keyY - sin(PI * f).toFloat() * .4f * keyR, label)
                else -> drawKey(canvas, keyX(i, 4), keyY, label)
            }
        }
        val taps = listOf(Tap(.15f, keyX(0, 4), keyY), Tap(.38f, keyX(2, 4), keyY))
        drawTaps(canvas, taps, h)
    }

    private fun drawBindEmoji(canvas: Canvas) {
        val x = keyX(1, 3)
        val labels = listOf(letter("q"), longPressKey, letter("m"))
        labels.forEachIndexed { i, label ->
            drawKey(canvas, keyX(i, 3), keyY, label,
                fillColor = if (i == 1 && t in .52f..0.82f) keyFillPressed else keyFill)
        }
        // Badge: how many characters are bound to the key (like the editor)
        val count = longPressLetters.size + if (t >= .32f) 1 else 0
        fill.color = accent
        val bx = x + keyR * .8f
        val by = keyY - keyR * .8f
        canvas.drawCircle(bx, by, 9f.dpF(this), fill)
        text.color = accentText
        text.textSize = 11f.dpF(this)
        canvas.drawText(count.toString(), bx, centerY(by), text)

        // Phase 1: the picker, tap 🔥, it flies onto the key
        val picker = listOf("❤", "👍", "😂", "🔥")
        val pickerXs = cellXs(width / 2f, picker.size)
        val pickerAlpha = 1 - seg(.36f, .44f)
        if (pickerAlpha > 0) drawPopup(canvas, picker, pickerXs, selected = if (t in .12f..0.36f) 3 else -1, alpha = pickerAlpha)
        val fly = ease(seg(.16f, .32f))
        if (t in .16f..0.32f) {
            text.textSize = lerp(20f, 12f, fly).dpF(this)
            canvas.drawText("🔥", lerp(pickerXs[3], bx, fly), centerY(lerp(popupY, by, fly)), text)
        }

        // Phase 2: hold the key, slide to 🔥
        val cells = longPressLetters.take(1) + "🔥"
        val cellXs = cellXs(x, cells.size)
        val slide = ease(seg(.70f, .78f))
        val fx = lerp(x, cellXs.last(), slide)
        val fy = lerp(keyY, popupY, slide)
        if (t in .66f..0.84f) drawPopup(canvas, cells, cellXs, selected = nearest(cellXs, fx))
        drawHoldRing(canvas, x, keyY, seg(.52f, .66f), visible = t in .52f..0.68f)
        drawField(canvas, if (t >= .82f) "🔥" else "")

        val tap = Finger(pickerXs[3], popupY, seg(.04f, .10f) * (1 - seg(.16f, .22f)), t in .12f..0.16f)
        drawFinger(canvas, tap.x, tap.y, tap.alpha, tap.pressed)
        drawFinger(canvas, fx, fy, seg(.44f, .50f) * (1 - seg(.86f, .92f)), pressed = t in .52f..0.82f)
    }

    private fun drawThemes(canvas: Canvas) {
        fun res(bgRes: Int, keyRes: Int, textRes: Int) = Palette(
            ContextCompat.getColor(context, bgRes), ContextCompat.getColor(context, keyRes),
            ContextCompat.getColor(context, textRes)
        )
        val light = res(R.color.keyboard_bg_light, R.color.key_fill_light, R.color.key_text_light)
        val dark = res(R.color.keyboard_bg_dark, R.color.key_fill_dark, R.color.key_text_dark)
        val custom = Palette(0xFF2B1A47.toInt(), 0xFF7E57C2.toInt(), Color.WHITE)
        fun rgb(f: Float) = Palette(
            Color.HSVToColor(floatArrayOf(f * 360f, .6f, .6f)), 0xCC1E1E1E.toInt(), Color.WHITE
        )

        val index = (t * 4).toInt().coerceAtMost(3)
        val local = t * 4 - index
        val palettes = listOf(light, dark, custom, rgb(local))
        val previous = if (index == 0) rgb(1f) else listOf(light, dark, custom)[index - 1]
        val p = previous.blend(palettes[index], seg(0f, .15f, local))

        fill.color = p.bg
        canvas.drawRoundRect(board, 16f.dpF(this), 16f.dpF(this), fill)
        val r = keyR * .8f
        val step = r * 2.2f
        listOf("qwerty", "asdfg").forEachIndexed { row, letters ->
            val y = keyY - (1 - row) * r * 2.1f
            letters.forEachIndexed { i, c ->
                val cx = width / 2f + (i - (letters.length - 1) / 2f) * step
                drawKey(canvas, cx, y, letter(c.toString()), fillColor = p.key, textColor = p.text, r = r, strokeColor = p.key)
            }
        }

        // Theme name chip
        val name = themeNames.getOrElse(index) { "" }
        text.textSize = 13f.dpF(this)
        val w = text.measureText(name) + 24f.dpF(this)
        rect.set(width / 2f - w / 2, 8f.dpF(this), width / 2f + w / 2, 34f.dpF(this))
        fill.color = ColorUtils.setAlphaComponent(p.text, 0x33)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        text.color = p.text
        canvas.drawText(name, rect.centerX(), centerY(rect.centerY()), text)
    }

    /* ───────── drawing helpers ───────── */

    private class Tap(val at: Float, val x: Float, val y: Float)
    private class Finger(val x: Float, val y: Float, val alpha: Float, val pressed: Boolean)

    private class Palette(val bg: Int, val key: Int, val text: Int) {
        fun blend(to: Palette, f: Float) = Palette(
            ColorUtils.blendARGB(bg, to.bg, f), ColorUtils.blendARGB(key, to.key, f),
            ColorUtils.blendARGB(text, to.text, f)
        )
    }

    /** Finger moving from tap to tap ([taps] in time order), fading in before the first and out after the last. */
    private fun drawTaps(canvas: Canvas, taps: List<Tap>, time: Float = t) {
        val first = taps.first()
        val last = taps.last()
        var x = first.x
        var y = first.y
        for (i in 0 until taps.size - 1) {
            val a = taps[i]
            val b = taps[i + 1]
            if (time >= a.at) {
                val f = ease(seg(a.at + .05f, b.at - .03f, time))
                x = lerp(a.x, b.x, f)
                y = lerp(a.y, b.y, f)
            }
        }
        val alpha = seg(first.at - .08f, first.at - .03f, time) * (1 - seg(last.at + .06f, last.at + .12f, time))
        drawFinger(canvas, x, y, alpha, pressed = taps.any { time in it.at..(it.at + .05f) })
    }

    private fun drawKey(
        canvas: Canvas, cx: Float, cy: Float, label: String,
        fillColor: Int = keyFill, textColor: Int = keyText, sub: String? = null,
        subHighlighted: Boolean = false, outline: Boolean = false, r: Float = keyR,
        strokeColor: Int = keyStroke
    ) {
        keyPath(cx, cy, r)
        hexPaint.style = Paint.Style.FILL
        hexPaint.color = fillColor
        canvas.drawPath(path, hexPaint)
        hexPaint.style = Paint.Style.STROKE
        hexPaint.strokeWidth = (if (outline) 3f else 1f).dpF(this)
        hexPaint.color = if (outline) accent else strokeColor
        canvas.drawPath(path, hexPaint)

        text.color = textColor
        text.textSize = (r / keyR) * 18f.dpF(this)
        canvas.drawText(label, cx, centerY(cy), text)
        if (sub != null) {
            text.textSize = (if (subHighlighted) 14f else 10f).dpF(this)
            text.color = if (subHighlighted) accent else ColorUtils.setAlphaComponent(textColor, 0xAA)
            canvas.drawText(sub, cx + r * .42f, centerY(cy - r * .5f), text)
        }
    }

    private fun keyPath(cx: Float, cy: Float, r: Float) {
        path.reset()
        when (shape) {
            KeyShape.CIRCLE -> path.addCircle(cx, cy, r * .92f, Path.Direction.CW)
            KeyShape.HEX, KeyShape.HEX_TALL, KeyShape.HEX_HALF_LEFT, KeyShape.HEX_HALF_RIGHT -> {
                for (k in 0 until 6) {
                    val a = (-90.0 + 60.0 * k) * PI / 180.0
                    val px = cx + r * cos(a).toFloat()
                    val py = cy + r * sin(a).toFloat()
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            else -> {
                val s = r * .85f
                rect.set(cx - s, cy - s, cx + s, cy + s)
                path.addRoundRect(rect, 8f.dpF(this), 8f.dpF(this), Path.Direction.CW)
            }
        }
    }

    /** Long-press style popup: a surface with a row of cells, [selected] in the Enter colors. */
    private fun drawPopup(canvas: Canvas, cells: List<String>, xs: List<Float>, selected: Int, alpha: Float = 1f) {
        val half = keyR * .78f
        val a = (alpha * 255).toInt()
        rect.set(xs.first() - half - 6f.dpF(this), popupY - half - 6f.dpF(this),
            xs.last() + half + 6f.dpF(this), popupY + half + 6f.dpF(this))
        fill.color = ColorUtils.setAlphaComponent(bg, a)
        canvas.drawRoundRect(rect, 12f.dpF(this), 12f.dpF(this), fill)
        stroke.color = ColorUtils.setAlphaComponent(keyStroke, a)
        stroke.strokeWidth = 1f.dpF(this)
        canvas.drawRoundRect(rect, 12f.dpF(this), 12f.dpF(this), stroke)
        cells.forEachIndexed { i, c ->
            rect.set(xs[i] - half, popupY - half, xs[i] + half, popupY + half)
            fill.color = ColorUtils.setAlphaComponent(if (i == selected) accent else keyFill, a)
            canvas.drawRoundRect(rect, 8f.dpF(this), 8f.dpF(this), fill)
            text.color = ColorUtils.setAlphaComponent(if (i == selected) accentText else keyText, a)
            text.textSize = 18f.dpF(this)
            canvas.drawText(c, xs[i], centerY(popupY), text)
        }
    }

    private fun drawField(canvas: Canvas, value: String) {
        fill.color = fieldBg
        canvas.drawRoundRect(field, 10f.dpF(this), 10f.dpF(this), fill)
        stroke.color = fieldBorder
        stroke.strokeWidth = 1f.dpF(this)
        canvas.drawRoundRect(field, 10f.dpF(this), 10f.dpF(this), stroke)

        text.textAlign = Paint.Align.LEFT
        text.color = fieldText
        text.textSize = 16f.dpF(this)
        val x = field.left + 12f.dpF(this)
        canvas.drawText(value, x, centerY(field.centerY()), text)
        text.textAlign = Paint.Align.CENTER
        // Caret
        val cx = x + text.measureText(value) + 2f.dpF(this)
        stroke.color = accent
        stroke.strokeWidth = 2f.dpF(this)
        canvas.drawLine(cx, field.top + 9f.dpF(this), cx, field.bottom - 9f.dpF(this), stroke)
    }

    private fun drawFinger(canvas: Canvas, x: Float, y: Float, alpha: Float, pressed: Boolean) {
        if (alpha <= 0f) return
        val r = (if (pressed) 12f else 15f).dpF(this)
        fill.color = ColorUtils.setAlphaComponent(fieldText, (alpha * 0x55).toInt())
        canvas.drawCircle(x, y, r, fill)
        stroke.color = ColorUtils.setAlphaComponent(fieldText, (alpha * 0xDD).toInt())
        stroke.strokeWidth = 2f.dpF(this)
        canvas.drawCircle(x, y, r, stroke)
    }

    /** Path of a swipe behind the finger. */
    private fun drawTrail(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, alpha: Float) {
        if (alpha <= 0f || (x0 == x1 && y0 == y1)) return
        stroke.color = ColorUtils.setAlphaComponent(accent, (alpha * 0x99).toInt())
        stroke.strokeWidth = 4f.dpF(this)
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x0, y0, x1, y1, stroke)
    }

    /** Arc that fills while a key is held (long press timeout). */
    private fun drawHoldRing(canvas: Canvas, x: Float, y: Float, progress: Float, visible: Boolean) {
        if (!visible || progress <= 0f) return
        val r = 20f.dpF(this)
        rect.set(x - r, y - r, x + r, y + r)
        stroke.color = accent
        stroke.strokeWidth = 3f.dpF(this)
        canvas.drawArc(rect, -90f, 360f * progress, false, stroke)
    }

    private fun keyX(i: Int, n: Int) = width / 2f + (i - (n - 1) / 2f) * 2.3f * keyR

    private fun cellXs(center: Float, n: Int): List<Float> {
        val step = keyR * 1.75f
        val first = (center - (n - 1) / 2f * step).coerceIn(keyR, width - keyR - (n - 1) * step)
        return List(n) { first + it * step }
    }

    private fun nearest(xs: List<Float>, x: Float) = xs.indices.minBy { kotlin.math.abs(xs[it] - x) }

    private fun centerY(cy: Float) = cy - (text.ascent() + text.descent()) / 2

    private fun seg(a: Float, b: Float, time: Float = t) = ((time - a) / (b - a)).coerceIn(0f, 1f)

    private fun ease(f: Float) = (1 - cos(PI * f).toFloat()) / 2

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}
