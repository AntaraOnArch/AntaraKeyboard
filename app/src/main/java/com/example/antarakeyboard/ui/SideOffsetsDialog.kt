package com.example.antarakeyboard.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp

/**
 * Side button positions: four sliders that shift the left / right side buttons a few dp
 * left or right, separately for portrait and landscape (e.g. away from a rounded screen edge).
 * The middle (◊) is the default position. Every change is saved at once; [onChanged] lets the
 * caller show one "<section> saved" toast when the popup closes.
 */
object SideOffsetsDialog {

    private class Slider(val side: EdgePos.Side, val landscape: Boolean, @StringRes val label: Int)

    private val SLIDERS = listOf(
        Slider(EdgePos.Side.LEFT, landscape = false, R.string.side_offset_left),
        Slider(EdgePos.Side.LEFT, landscape = true, R.string.side_offset_left_landscape),
        Slider(EdgePos.Side.RIGHT, landscape = false, R.string.side_offset_right),
        Slider(EdgePos.Side.RIGHT, landscape = true, R.string.side_offset_right_landscape)
    )

    fun show(activity: Activity, onChanged: () -> Unit, onDismiss: () -> Unit) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(activity), 16.dp(activity), 16.dp(activity), 16.dp(activity))
            setBackgroundColor(ContextCompat.getColor(activity, R.color.main_app_bg))
        }

        root.addView(TextView(activity).apply {
            setText(R.string.main_side_offsets)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            setText(R.string.side_offset_hint)
            textSize = 13f
            setPadding(0, 8.dp(activity), 0, 0)
        })

        SLIDERS.forEach { slider ->
            root.addSlider(
                min = KeyboardPrefs.SIDE_OFFSET_MIN_DP,
                max = KeyboardPrefs.SIDE_OFFSET_MAX_DP,
                value = KeyboardPrefs.getSideOffsetDp(activity, slider.side, slider.landscape),
                label = { activity.getString(slider.label, formatOffset(it)) },
                hint = R.string.side_offset_direction
            ) {
                KeyboardPrefs.setSideOffsetDp(activity, slider.side, slider.landscape, it)
                onChanged()
            }
        }

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        // Scrolls only when the screen is too short (landscape)
        dialog.setContentView(ScrollView(activity).apply { addView(root) })
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** "◊" in the default position, otherwise a signed dp value ("+3 dp", "−2 dp"). */
    fun formatOffset(dp: Int): String = when {
        dp == 0 -> "◊"
        dp > 0 -> "+$dp dp"
        else -> "−${-dp} dp"
    }
}
