package com.example.antarakeyboard.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.service.RgbBackgroundAnimator
import com.example.antarakeyboard.service.RgbSettings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlin.math.roundToInt

/**
 * Settings popup of RGB Smooth / RGB Wild: a live preview (the keyboard's own animation),
 * speed, saturation and brightness. Every change is saved at once (no Save button);
 * [onChanged] lets the caller show one "<theme> saved" toast when the popup closes.
 */
object RgbSettingsDialog {

    fun show(activity: Activity, wild: Boolean, onChanged: () -> Unit, onDismiss: () -> Unit) {
        var settings = if (wild) RgbSettings.wild(activity) else RgbSettings.smooth(activity)

        val scope = MainScope()
        val preview = View(activity)
        // The preview shows each frame exactly as the keyboard does
        lateinit var previewAnimator: RgbBackgroundAnimator
        previewAnimator = RgbBackgroundAnimator(activity, scope) {
            preview.background = previewAnimator.newDrawable()
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(activity), 16.dp(activity), 16.dp(activity), 16.dp(activity))
            setBackgroundColor(ContextCompat.getColor(activity, R.color.main_app_bg))
        }

        root.addView(TextView(activity).apply {
            setText(if (wild) R.string.theme_rgb_wild else R.string.theme_rgb_smooth)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 48.dp(activity)
        ).apply { topMargin = 16.dp(activity) })

        fun changed(new: RgbSettings) {
            settings = new
            previewAnimator.start(settings)
            onChanged()
        }

        // Speed
        if (wild) {
            root.addSlider(
                min = KeyboardPrefs.RGB_WILD_SPEED_MIN, max = KeyboardPrefs.RGB_WILD_SPEED_MAX,
                value = settings.speedMs, label = { activity.getString(R.string.rgb_speed_ms, it) },
                hint = R.string.rgb_insane_chill
            ) {
                KeyboardPrefs.setRgbWildSpeed(activity, it)
                changed(settings.copy(speedMs = it))
            }
        } else {
            root.addSlider(
                min = KeyboardPrefs.RGB_SPEED_MIN, max = KeyboardPrefs.RGB_SPEED_MAX,
                value = settings.speedMs, label = { activity.getString(R.string.rgb_speed_seconds, it / 1000) },
                hint = R.string.rgb_fast_slow
            ) {
                KeyboardPrefs.setRgbSmoothSpeed(activity, it)
                changed(settings.copy(speedMs = it))
            }
        }

        // Saturation and brightness in percent; the lower limit is what KeyboardPrefs stores
        val minPercent = if (wild) 30 else 10
        root.addSlider(
            min = minPercent, max = 100, value = (settings.saturation * 100).roundToInt(),
            label = { activity.getString(R.string.rgb_saturation, it) }
        ) {
            if (wild) KeyboardPrefs.setRgbWildSaturation(activity, it / 100f)
            else KeyboardPrefs.setRgbSmoothSaturation(activity, it / 100f)
            changed(settings.copy(saturation = it / 100f))
        }
        root.addSlider(
            min = minPercent, max = 100, value = (settings.brightness * 100).roundToInt(),
            label = { activity.getString(R.string.rgb_brightness, it) }
        ) {
            if (wild) KeyboardPrefs.setRgbWildBrightness(activity, it / 100f)
            else KeyboardPrefs.setRgbSmoothBrightness(activity, it / 100f)
            changed(settings.copy(brightness = it / 100f))
        }

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            scope.cancel()
            onDismiss()
        }
        previewAnimator.start(settings)
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
