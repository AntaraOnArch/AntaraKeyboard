package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.example.antarakeyboard.R
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.KeyView

/**
 * Manages long press popup for character selection.
 * Extracted from MyKeyboardService for better separation of concerns.
 */
class LongPressPopupManager(
    private val context: Context,
    private val overlayLayerProvider: () -> FrameLayout,
    private val themedCtxProvider: () -> Context,
    private val inputConnectionProvider: () -> InputConnection?,
    private val keyHeightProvider: () -> Int,
    private val isPortraitProvider: () -> Boolean,
    private val currentShapeProvider: () -> KeyShape,
    private val isDarkModeProvider: () -> Boolean
) {
    private var longPressPopup: PopupWindow? = null
    private var lpRects: List<Rect> = emptyList()
    private var lpChars: List<String> = emptyList()
    private var lpSelectedIndex: Int = 0
    private var lpPreviewTv: TextView? = null
    private var lpGrid: GridLayout? = null
    private var lpHasLiveInserted = false

    private val LIVE_REPLACE = false

    val isPopupShowing: Boolean
        get() = longPressPopup != null

    fun showLongPressPopup(anchor: View, chars: List<String>) {
        if (chars.isEmpty()) return

        hideLongPressPopup()

        lpChars = chars
        lpSelectedIndex = 0
        lpHasLiveInserted = false

        val overlayLayer = overlayLayerProvider()
        val themedCtx = themedCtxProvider()

        val maxW = (overlayLayer.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels) - 16.dp(context)

        val cols = minOf(7, chars.size)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp(context), 10.dp(context), 10.dp(context), 10.dp(context))
            setBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = ViewGroup.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val preview = TextView(context).apply {
            text = chars.first()
            textSize = 26f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 6.dp(context))
        }

        lpPreviewTv = preview
        root.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val grid = GridLayout(context).apply {
            columnCount = cols
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        lpGrid = grid

        val popupKeyH = (keyHeightProvider() * 0.62f).toInt().coerceIn(28.dp(context), 70.dp(context))
        val popupTextSize = if (isPortraitProvider()) 16f else 14f

        val isDark = isDarkModeProvider()
        val keyTextColor = themeColor(themedCtx, R.attr.keyText,
            if (isDark) Color.WHITE else Color.BLACK)

        chars.forEachIndexed { idx, ch ->
            val kv = KeyView(themedCtx).apply {
                tag = idx
                text = ch
                isAllCaps = false
                shape = currentShapeProvider()
                gravity = Gravity.CENTER
                isClickable = false
                isFocusable = false
                textSize = popupTextSize

                setTextColor(keyTextColor)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
            }

            val lp = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(idx / cols)
                columnSpec = GridLayout.spec(idx % cols, 1f)
                width = 0
                height = popupKeyH
                setMargins(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
            }

            grid.addView(kv, lp)
        }

        root.addView(grid)

        val pw = PopupWindow(
            root,
            maxW,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            isClippingEnabled = true
            elevation = 10.dp(context).toFloat()
            setOnDismissListener {
                longPressPopup = null
                lpPreviewTv = null
                lpGrid = null
                lpChars = emptyList()
                lpHasLiveInserted = false
            }
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        overlayLayer.getLocationOnScreen(rootLoc)

        val popupW = maxW
        val popupH = root.measuredHeight

        val desiredX = anchorLoc[0] - rootLoc[0] + anchor.width / 2 - popupW / 2
        val desiredY = anchorLoc[1] - rootLoc[1] - popupH - 10.dp(context)

        val minPopupOffset = 8.dp(context)

        val maxX = (
                overlayLayer.width -
                        popupW -
                        minPopupOffset
                ).coerceAtLeast(minPopupOffset)

        val maxY = (
                overlayLayer.height -
                        popupH -
                        minPopupOffset
                ).coerceAtLeast(minPopupOffset)

        val x = desiredX.coerceIn(
            minPopupOffset,
            maxX
        )

        val y = desiredY.coerceIn(
            minPopupOffset,
            maxY
        )

        pw.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, x, y)
        longPressPopup = pw

        updateLongPressHighlight()

        root.post {
            val rects = MutableList(lpChars.size) { Rect() }
            val g = lpGrid ?: return@post

            for (i in 0 until g.childCount) {
                val child = g.getChildAt(i)
                val idx = (child.tag as? Int) ?: continue
                val loc = IntArray(2)
                child.getLocationOnScreen(loc)

                rects[idx] = Rect(
                    loc[0],
                    loc[1],
                    loc[0] + child.width,
                    loc[1] + child.height
                )
            }

            lpRects = rects
        }

        if (LIVE_REPLACE) {
            commitLiveSelected()
        }
    }

    private fun updateLongPressHighlight() {
        val grid = lpGrid ?: return
        val themedCtx = themedCtxProvider()

        val fillActive = themeColor(themedCtx, R.attr.enterFill, 0xFF2E55E7.toInt())
        val textActive = themeColor(themedCtx, R.attr.enterText, 0xFFFFFFFF.toInt())
        val textNormal = themeColor(themedCtx, R.attr.keyText, 0xFFFFFFFF.toInt())

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val idx = (child.tag as? Int) ?: continue

            if (child is KeyView) {
                if (idx == lpSelectedIndex) {
                    child.customBgColor = fillActive
                    child.setTextColor(textActive)
                    child.alpha = 1f
                } else {
                    child.customBgColor = null
                    child.setTextColor(textNormal)
                    child.alpha = 0.65f
                }
            } else {
                child.alpha = if (idx == lpSelectedIndex) 1f else 0.65f
            }

            child.scaleX = if (idx == lpSelectedIndex) 1.06f else 1f
            child.scaleY = if (idx == lpSelectedIndex) 1.06f else 1f
        }

        lpPreviewTv?.text = lpChars.getOrNull(lpSelectedIndex) ?: ""
    }

    fun moveLpSelection(dx: Int, dy: Int) {
        if (lpChars.isEmpty()) return

        val cols = lpGrid?.columnCount ?: 7
        val total = lpChars.size
        val rows = (total + cols - 1) / cols

        val curRow = lpSelectedIndex / cols
        val curCol = lpSelectedIndex % cols

        var newRow = (curRow + dy).coerceIn(0, rows - 1)
        var newCol = (curCol + dx).coerceIn(0, cols - 1)
        var newIndex = newRow * cols + newCol

        if (newIndex >= total) {
            while (newIndex >= total && newCol > 0) {
                newCol--
                newIndex = newRow * cols + newCol
            }
            if (newIndex >= total) newIndex = total - 1
        }

        if (newIndex != lpSelectedIndex) {
            lpSelectedIndex = newIndex
            updateLongPressHighlight()

            if (LIVE_REPLACE) {
                replaceLiveSelected()
            }
        }
    }

    private fun commitLiveSelected() {
        val ch = lpChars.getOrNull(lpSelectedIndex) ?: return
        inputConnectionProvider()?.commitText(ch, 1)
        lpHasLiveInserted = true
    }

    private fun replaceLiveSelected() {
        if (lpHasLiveInserted) {
            inputConnectionProvider()?.deleteSurroundingText(1, 0)
        }
        commitLiveSelected()
    }

    fun hideLongPressPopup() {
        longPressPopup?.dismiss()
        longPressPopup = null
    }

    /**
     * Gets the currently selected character from the popup.
     * Returns null if no popup is showing.
     */
    fun getSelectedChar(): String? {
        return lpChars.getOrNull(lpSelectedIndex)
    }

    /**
     * Gets the list of screen-space rects for each character in the popup grid.
     */
    fun getLpRects(): List<Rect> = lpRects

    /**
     * Gets the list of characters displayed in the popup.
     */
    fun getLpChars(): List<String> = lpChars

    /**
     * Gets the currently selected index.
     */
    fun getSelectedIndex(): Int = lpSelectedIndex

    /**
     * Sets the selected index and updates the highlight.
     */
    fun setSelectedIndex(index: Int) {
        if (index in lpChars.indices && index != lpSelectedIndex) {
            lpSelectedIndex = index
            updateLongPressHighlight()
        }
    }

    /**
     * Resets the selection to the first item.
     */
    fun resetSelection() {
        lpSelectedIndex = 0
        if (longPressPopup != null) {
            updateLongPressHighlight()
        }
    }

    private fun themeColor(ctx: Context, attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        val th = ctx.theme
        return if (
            th.resolveAttribute(attr, tv, true) &&
            tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            fallback
        }
    }
}
