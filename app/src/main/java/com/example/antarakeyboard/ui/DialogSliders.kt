package com.example.antarakeyboard.ui

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.antarakeyboard.extensions.dp

/** Label + SeekBar from [min] to [max] (+ optional hint under it); [onChange] gets the value. */
internal fun LinearLayout.addSlider(
    min: Int,
    max: Int,
    value: Int,
    label: (Int) -> String,
    hint: Int? = null,
    onChange: (Int) -> Unit
) {
    val labelView = TextView(context).apply {
        text = label(value)
        textSize = 14f
        setPadding(0, 16.dp(context), 0, 4.dp(context))
    }
    addView(labelView)
    addView(SeekBar(context).apply {
        this.max = max - min
        progress = value.coerceIn(min, max) - min
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                labelView.text = label(progress + min)
                onChange(progress + min)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    })
    if (hint != null) {
        addView(TextView(context).apply {
            setText(hint)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF888888.toInt())
        })
    }
}
