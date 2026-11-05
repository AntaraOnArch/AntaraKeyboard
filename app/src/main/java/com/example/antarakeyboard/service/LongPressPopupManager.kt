package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.MAX_LONG_PRESS_BINDINGS
import com.example.antarakeyboard.ui.KeyView
import kotlin.math.roundToInt

/**
 * Manages long press popup for character selection.
 * Extracted from MyKeyboardService for better separation of concerns.
 */
class LongPressPopupManager(
    private val context: Context,
    private val overlayLayerProvider: () -> FrameLayout,
    private val themedCtxProvider: () -> Context,
    private val isPortraitProvider: () -> Boolean,
    private val currentShapeProvider: () -> KeyShape,
    /** Key height slider: the cells are stretched like the keys. */
    private val keyHeightStretchProvider: () -> Float,
    /** Row spacing slider in px, added between the popup rows like between the key rows. */
    private val rowSpacingPxProvider: () -> Int,
    private val colorsProvider: (anchor: View) -> Colors,
    /** Current RGB theme frame, or null when no RGB theme is active. */
    private val rgbBackgroundProvider: () -> Drawable?
) {
    /** Colors resolved from the user's theme / Colors settings at popup open time. */
    data class Colors(
        val popupBg: Int,
        val keyBg: Int,
        val keyText: Int,
        val activeBg: Int,
        val activeText: Int
    )

    private var lpColors: Colors? = null

    private var longPressPopup: PopupWindow? = null
    private var lpRects: List<Rect> = emptyList()
    private var lpChars: List<String> = emptyList()
    private var lpSelectedIndex: Int = 0
    private var lpPreviewTv: TextView? = null
    private var lpCells: List<View> = emptyList()
    /** Number of cells in each popup row (honeycomb: full and one shorter, alternating). */
    private var lpRowSizes: List<Int> = emptyList()
    private var lpHoneycomb = false
    private var lpRoot: LinearLayout? = null

    val isPopupShowing: Boolean
        get() = longPressPopup != null

    fun showLongPressPopup(anchor: View, allChars: List<String>) {
        if (allChars.isEmpty()) return
        // Keys bound before the limit existed may hold more; the popup must still fit on screen
        val chars = allChars.take(MAX_LONG_PRESS_BINDINGS)

        hideLongPressPopup()

        lpChars = chars
        lpSelectedIndex = 0

        val colors = colorsProvider(anchor)
        lpColors = colors

        val overlayLayer = overlayLayerProvider()
        val themedCtx = themedCtxProvider()

        val maxW = (overlayLayer.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels) - 16.dp(context)

        val cols = minOf(MAX_COLS, chars.size)

        // Square cells the size of a full 7-column row, so a key with only two or three letters
        // gets a narrow popup instead of a few very wide, flat cells
        val popupPad = 10.dp(context)
        val cellMargin = 4.dp(context)
        val cellW = (maxW - 2 * popupPad) / MAX_COLS - 2 * cellMargin
        val popupW = cols * (cellW + 2 * cellMargin) + 2 * popupPad

        val corner = 14.dp(context).toFloat()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(popupPad, popupPad, popupPad, popupPad)
            background = rgbBackgroundProvider() ?: GradientDrawable().apply {
                cornerRadius = corner
                setColor(colors.popupBg)
            }
            // Border in the foreground so it stays while RGB changes the background
            foreground = GradientDrawable().apply {
                cornerRadius = corner
                setColor(0)
                setStroke(1.dp(context), (colors.keyText and 0x00FFFFFF) or 0x40000000)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, corner)
                }
            }
            clipToOutline = true
            layoutParams = ViewGroup.LayoutParams(popupW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        lpRoot = root

        val preview = TextView(context).apply {
            setTextColor(colors.keyText)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 6.dp(context))
            // Preview only: very long custom text is cut here, the whole text is still typed
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        // Styled before measuring, otherwise long text measures at full length and pushes the popup off the key
        setPreviewText(preview, chars.first())

        lpPreviewTv = preview
        root.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Hexagons interlock like the keyboard: every second row is indented by half a cell
        // and holds one cell less
        val honeycomb = currentShapeProvider() in HONEYCOMB_SHAPES
        val rowSizes = rowSizes(chars.size, cols, honeycomb)
        lpRowSizes = rowSizes
        lpHoneycomb = honeycomb
        val cellStep = cellW + 2 * cellMargin
        val shape = currentShapeProvider()
        val stretch = keyHeightStretchProvider()
        // KeyView grows by the drawn shape's growth (same as the keys)
        val shapeH = KeyView.shapeHeight(shape, cellW.toFloat(), cellW.toFloat())
        val rowH = (cellW + shapeH * (stretch - 1f)).roundToInt() + 2 * cellMargin
        // Honeycomb: rows interlock by the hexagon's tip (a quarter of its height), like the keyboard
        val rowStep = (if (honeycomb) (shapeH * stretch * HONEYCOMB_ROW_STEP).roundToInt() else rowH) +
                rowSpacingPxProvider()
        val rowOverlap = rowH - rowStep

        val popupTextSize = if (isPortraitProvider()) 16f else 14f
        val cells = mutableListOf<View>()
        var idx = 0

        rowSizes.forEachIndexed { rowIndex, size ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
            }
            repeat(size) {
                val kv = KeyView(themedCtx).apply {
                    tag = idx
                    text = chars[idx]
                    isAllCaps = false
                    this.shape = shape
                    heightStretch = stretch
                    gravity = Gravity.CENTER
                    isClickable = false
                    isFocusable = false
                    textSize = popupTextSize

                    customBgColor = colors.keyBg
                    setTextColor(colors.keyText)
                    includeFontPadding = false
                    setPadding(0, 0, 0, 0)
                }
                row.addView(kv, LinearLayout.LayoutParams(cellW, cellW).apply {
                    setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                })
                cells.add(kv)
                idx++
            }
            root.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = -rowOverlap
                if (honeycomb && rowIndex % 2 == 1) marginStart = cellStep / 2
            })
        }
        lpCells = cells

        val pw = PopupWindow(
            root,
            popupW,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            // Top row: the popup must be able to extend above the keyboard's edge
            isClippingEnabled = false
            elevation = 10.dp(context).toFloat()
            setOnDismissListener {
                longPressPopup = null
                lpPreviewTv = null
                lpCells = emptyList()
                lpRowSizes = emptyList()
                lpRoot = null
                lpChars = emptyList()
                lpColors = null
            }
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(popupW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        overlayLayer.getLocationOnScreen(rootLoc)

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

        // rootLoc is the overlay's screen position: Y may go above the keyboard, but not above the screen
        val y = desiredY.coerceIn(
            minPopupOffset - rootLoc[1],
            maxY
        )

        pw.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, x, y)
        longPressPopup = pw

        updateLongPressHighlight()

        root.post {
            val rects = MutableList(lpChars.size) { Rect() }
            for (child in lpCells) {
                val idx = (child.tag as? Int) ?: continue
                val loc = IntArray(2)
                child.getLocationOnScreen(loc)

                // Interlocked rows overlap: each cell's touch area ends halfway into the overlap
                val trim = maxOf(0, rowOverlap) / 2
                rects[idx] = Rect(
                    loc[0],
                    loc[1] + trim,
                    loc[0] + child.width,
                    loc[1] + child.height - trim
                )
            }

            lpRects = rects
        }
    }

    /** Called on every RGB animation frame so the popup background animates with the keyboard. */
    fun updateRgbBackground(drawable: Drawable) {
        lpRoot?.background = drawable
    }

    private fun updateLongPressHighlight() {
        val colors = lpColors ?: return

        for (child in lpCells) {
            val idx = (child.tag as? Int) ?: continue

            if (child is KeyView) {
                if (idx == lpSelectedIndex) {
                    child.customBgColor = colors.activeBg
                    child.setTextColor(colors.activeText)
                    child.alpha = 1f
                } else {
                    child.customBgColor = colors.keyBg
                    child.setTextColor(colors.keyText)
                    child.alpha = 0.65f
                }
            } else {
                child.alpha = if (idx == lpSelectedIndex) 1f else 0.65f
            }

            child.scaleX = if (idx == lpSelectedIndex) 1.06f else 1f
            child.scaleY = if (idx == lpSelectedIndex) 1.06f else 1f
        }

        lpPreviewTv?.let { setPreviewText(it, lpChars.getOrNull(lpSelectedIndex) ?: "") }
    }

    private fun setPreviewText(preview: TextView, selected: String) {
        preview.text = selected
        // Custom text can be long: smaller preview, wrapped to a few lines
        preview.textSize = if (selected.length > 12) 16f else 26f
    }

    fun moveLpSelection(dx: Int, dy: Int) {
        if (lpChars.isEmpty() || lpRowSizes.isEmpty()) return

        val rowStarts = lpRowSizes.runningFold(0) { acc, n -> acc + n }
        val curRow = rowStarts.indexOfLast { it <= lpSelectedIndex }.coerceAtMost(lpRowSizes.lastIndex)
        val curCol = lpSelectedIndex - rowStarts[curRow]

        val newRow = (curRow + dy).coerceIn(0, lpRowSizes.lastIndex)
        val newCol = if (newRow == curRow) {
            curCol + dx
        } else {
            // Same horizontal position; indented honeycomb rows sit half a cell to the right
            val x = curCol + rowIndent(curRow) + dx
            (x - rowIndent(newRow) + 0.5f).toInt()
        }.coerceIn(0, lpRowSizes[newRow] - 1)
        val newIndex = rowStarts[newRow] + newCol

        if (newIndex != lpSelectedIndex) {
            lpSelectedIndex = newIndex
            updateLongPressHighlight()
        }
    }

    private fun rowIndent(row: Int): Float = if (lpHoneycomb && row % 2 == 1) 0.5f else 0f

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

    internal companion object {
        const val MAX_COLS = 7
        private const val HONEYCOMB_ROW_STEP = 0.75f
        private val HONEYCOMB_SHAPES = setOf(KeyShape.HEX, KeyShape.HEX_TALL)

        /**
         * Cells per popup row: [cols] in every row, or for a [honeycomb] [cols] and [cols] − 1
         * alternating (a single short row stays one row).
         */
        fun rowSizes(total: Int, cols: Int, honeycomb: Boolean): List<Int> {
            val sizes = mutableListOf<Int>()
            var left = total
            while (left > 0) {
                val rowMax = if (honeycomb && sizes.size % 2 == 1) maxOf(1, cols - 1) else cols
                val n = minOf(rowMax, left)
                sizes.add(n)
                left -= n
            }
            return sizes
        }
    }
}
