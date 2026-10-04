package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.ui.PopupColors

/**
 * Manages key preview popup that appears above pressed key.
 * Shows the character being typed for visual feedback.
 */
class KeyPreviewManager(
    private val context: Context,
    private val overlayLayerProvider: () -> FrameLayout
) {
    private var previewPopup: PopupWindow? = null
    private var previewTextView: TextView? = null
    private var enabled: Boolean = true

    /**
     * Enable or disable key preview
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Show preview popup for a key
     * @param anchorView The KeyView being pressed
     * @param label The character to show in preview
     */
    fun show(anchorView: View, label: String) {
        if (!enabled) return

        // Don't show preview for special keys
        if (label.length > 2 || label in EXCLUDED_LABELS) {
            return
        }

        hide()

        val overlayLayer = overlayLayerProvider()

        // Create preview TextView
        val textView = TextView(context).apply {
            text = label
            textSize = 28f
            setTextColor(PopupColors.PREVIEW_TEXT)
            gravity = Gravity.CENTER
            includeFontPadding = false

            // Background with rounded corners
            background = GradientDrawable().apply {
                setColor(PopupColors.PREVIEW_BG)
                cornerRadius = 8.dp(context).toFloat()
            }

            setPadding(
                16.dp(context),
                12.dp(context),
                16.dp(context),
                12.dp(context)
            )
        }

        previewTextView = textView

        // Calculate size
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupWidth = textView.measuredWidth.coerceAtLeast(48.dp(context))
        val popupHeight = textView.measuredHeight.coerceAtLeast(48.dp(context))

        // Create popup
        previewPopup = PopupWindow(
            textView,
            popupWidth,
            popupHeight,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            elevation = 8.dp(context).toFloat()
        }

        // Calculate position (centered above the key)
        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchorView.getLocationInWindow(anchorLoc)
        overlayLayer.getLocationInWindow(rootLoc)

        val x = anchorLoc[0] - rootLoc[0] + (anchorView.width - popupWidth) / 2
        val y = anchorLoc[1] - rootLoc[1] - popupHeight - 8.dp(context)

        // Clamp to screen bounds
        val clampedX = x.coerceIn(4.dp(context), overlayLayer.width - popupWidth - 4.dp(context))
        val clampedY = y.coerceAtLeast(4.dp(context))

        previewPopup?.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, clampedX, clampedY)
    }

    /**
     * Hide the preview popup
     */
    fun hide() {
        previewPopup?.dismiss()
        previewPopup = null
        previewTextView = null
    }

    /**
     * Check if preview is currently showing
     */
    val isShowing: Boolean
        get() = previewPopup != null

    companion object {
        // Labels that shouldn't show preview
        private val EXCLUDED_LABELS = setOf(
            " ", "⇧", "⌫", "↵", "123", "ABC", "abc",
            "?123", "#+="
        )
    }
}
