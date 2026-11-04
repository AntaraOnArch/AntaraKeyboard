package com.example.antarakeyboard.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import com.example.antarakeyboard.R
import com.example.antarakeyboard.extensions.dp

/*
 * Color picker popup used by the Colors popup and the saved-layout preview:
 * color wheel + brightness, live preview and hex / HSL / RGB values to copy.
 */

// Text built from data (names, key labels, color values), not from UI strings
@SuppressLint("SetTextI18n")
fun Context.showAdvancedColorPicker(
    title: String,
    initialColor: Int,
    onPicked: (Int) -> Unit
) {
    val r = Color.red(initialColor)
    val g = Color.green(initialColor)
    val b = Color.blue(initialColor)

    val hsv = FloatArray(3)
    Color.RGBToHSV(r, g, b, hsv)

    var currentHue = hsv[0]
    var currentSat = hsv[1]
    var currentVal = hsv[2]

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
    }

    // Preview bar (defined BEFORE updatePreview so it is available there)
    val previewBar = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            48.dp(this@showAdvancedColorPicker)
        ).apply {
            topMargin = 12.dp(this@showAdvancedColorPicker)
        }
    }

    // Input fields
    val hexInput = android.widget.EditText(this).apply {
        textSize = 14f
        setPadding(8.dp(this), 8.dp(this), 8.dp(this), 8.dp(this))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    val hslInput = TextView(this).apply {
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    val rgbInput = TextView(this).apply {
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    // Define updatePreview HERE, before colorWheel
    val updatePreview: () -> Unit = {
        val color = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
        previewBar.setBackgroundColor(color)

        val hex = String.format("#%06X", color and 0xFFFFFF)
        hexInput.setText(hex.substring(1))

        val rr = Color.red(color)
        val gg = Color.green(color)
        val bb = Color.blue(color)
        rgbInput.text = "rgb($rr $gg $bb)"

        val hslColor = colorToHSL(color)
        hslInput.text = "hsl(${hslColor[0].toInt()}deg ${(hslColor[1] * 100).toInt()}% ${(hslColor[2] * 100).toInt()}%)"

        onPicked(color)
    }

    // Now updatePreview can be used in colorWheel
    val colorWheel = ColorWheelView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            240.dp(this)
        )
        setHueSaturation(currentHue, currentSat)
        setOnColorChangedListener { h, s ->
            currentHue = h
            currentSat = s
            updatePreview()
        }
    }

    // Brightness slider
    val brightnessLabel = TextView(this).apply {
        text = getString(R.string.color_picker_brightness)
        textSize = 14f
        setPadding(0, 12.dp(this), 0, 4.dp(this))
    }

    val brightnessSeek = SeekBar(this).apply {
        max = 100
        progress = (currentVal * 100).toInt()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            currentVal = progress / 100f
            updatePreview()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    // Hex input listener
    hexInput.setOnEditorActionListener { _, _, _ ->
        val hexText = hexInput.text.toString()
        try {
            val parsedColor = "#$hexText".toColorInt()
            Color.RGBToHSV(Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor), hsv)
            currentHue = hsv[0]
            currentSat = hsv[1]
            currentVal = hsv[2]
            colorWheel.setHueSaturation(currentHue, currentSat)
            brightnessSeek.progress = (currentVal * 100).toInt()
            updatePreview()
        } catch (_: IllegalArgumentException) {}
        true
    }

    // Build UI
    root.addView(colorWheel)
    root.addView(brightnessLabel)
    root.addView(brightnessSeek)
    root.addView(previewBar)

    // Input fields
    val inputsRow = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 12.dp(this), 0, 0)
    }

    // Hex row
    val hexRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }
    hexRow.addView(TextView(this).apply { text = "#"; textSize = 14f })
    hexRow.addView(hexInput)
    hexRow.addView(Button(this).apply {
        text = "📋"
        isAllCaps = false
        setOnClickListener {
            copyToClipboard("hex", hexInput.text.toString())
        }
    })
    inputsRow.addView(hexRow)

    // HSL row
    val hslRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, 4.dp(this), 0, 0)
    }
    hslRow.addView(hslInput)
    hslRow.addView(Button(this).apply {
        text = "📋"
        isAllCaps = false
        setOnClickListener {
            copyToClipboard("hsl", hslInput.text.toString())
        }
    })
    inputsRow.addView(hslRow)

    // RGB row
    val rgbRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, 4.dp(this), 0, 0)
    }
    rgbRow.addView(rgbInput)
    rgbRow.addView(Button(this).apply {
        text = "📋"
        isAllCaps = false
        setOnClickListener {
            copyToClipboard("rgb", rgbInput.text.toString())
        }
    })
    inputsRow.addView(rgbRow)

    root.addView(inputsRow)

    // Initial update
    updatePreview()

    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(ScrollView(this).apply { addView(root) })
        .setPositiveButton(R.string.action_done) { _, _ ->
            val finalColor = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
            onPicked(finalColor)
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

/** RGB → HSL: hue in degrees, saturation and lightness 0..1. */
// ClipboardManager.primaryClip is read-only in Kotlin (nullable getter), so the setter call stays
@Suppress("UsePropertyAccessSyntax")
private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun colorToHSL(color: Int): FloatArray {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f

    val h: Float
    val s: Float

    if (max == min) {
        h = 0f
        s = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6f
            g -> ((b - r) / d + 2) / 6f
            else -> ((r - g) / d + 4) / 6f
        }
    }

    return floatArrayOf(h * 360f, s, l)
}
