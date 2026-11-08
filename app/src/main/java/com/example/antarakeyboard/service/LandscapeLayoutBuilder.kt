package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isNotEmpty
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.RowConfig
import com.example.antarakeyboard.ui.KeyView
import kotlin.math.roundToInt

/**
 * Which letter keys go to the left and right half of each landscape row (pure, unit-tested).
 * The split points are fixed per row count; edge ghosts (keys moved to a side button) are skipped.
 */
internal object LandscapeSplit {

    /** Rows 1, 3, 5 (index 0, 2, 4) are the "odd" rows of the honeycomb. */
    fun isOddRow(rowIndex: Int): Boolean = rowIndex % 2 == 0

    fun leftKeys(rowKeys: List<KeyConfig>, rowIndex: Int, rowCount: Int): List<KeyConfig> {
        val visible = visible(rowKeys)
        if (rowCount == 3) {
            return when (rowIndex) {
                1 -> visible.take(5)
                else -> visible.take(6)
            }
        }
        return visible.take(sideCount(visible, rowIndex, rowCount))
    }

    fun rightKeys(rowKeys: List<KeyConfig>, rowIndex: Int, rowCount: Int): List<KeyConfig> {
        val visible = visible(rowKeys)
        if (rowCount == 3) {
            return when (rowIndex) {
                0, 2 -> visible.drop(6).take(5)
                1 -> visible.drop(5).take(6)
                else -> visible.drop(6)
            }
        }
        return visible.takeLast(sideCount(visible, rowIndex, rowCount))
    }

    private fun visible(rowKeys: List<KeyConfig>) =
        rowKeys.filterNot { it.longPressBindings.contains(KeyMarkers.EDGE_GHOST) }

    /** 4 and 5 rows: 3 keys on rows 1, 3, 5 and 4 on the others; 4-row rows 1 and 3 with ≥ 8 keys get 4. */
    private fun sideCount(visible: List<KeyConfig>, rowIndex: Int, rowCount: Int): Int =
        if (rowCount == 4 && rowIndex in setOf(0, 2) && visible.size >= 8) 4
        else if (isOddRow(rowIndex)) 3 else 4
}

/**
 * Landscape layout: the letter rows split into a left and a right half with the Horizontal
 * "island" (center symbols) between them. Sizes and offsets are tuned per row count and shape.
 */
internal class LandscapeLayoutBuilder(
    private val context: Context,
    private val host: Host
) {
    /** What the builder needs from the keyboard service. */
    interface Host {
        val keyboardContainer: LinearLayout
        val themedContext: Context
        val currentShape: KeyShape
        /** Rows of the active layout (alphabet or numeric, with edge keys applied). */
        val currentRows: List<RowConfig>
        fun effectiveShape(): KeyShape
        fun landscapeShape(): KeyShape
        fun keyHeightStretch(): Float
        /** Sizing of the widest portrait row (landscape triangles copy its box and overlap). */
        fun portraitRowSizing(): KeySizing.RowSizing
        /** Drawn shape height of the portrait keys at 100 % on the key height slider. */
        fun portraitBaseShapeHeightPx(): Float
        fun rowSpacingPx(): Int
        fun createKey(key: KeyConfig): KeyView
        fun keyColors(label: String?): Pair<Int, Int>
        /** Colors special keys; returns the next space index (left / right space). */
        fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int): Int
        fun hidePopups()
        fun commitText(text: String)
    }

    private var spaceIndex = 0

    /**
     * Triangles copy the portrait width, tip overlap and up/down alternation; their box is only
     * as tall as the drawn triangle, so no empty space is left above, between or below the rows.
     */
    private fun portraitTriangles(): Boolean = host.currentShape == KeyShape.TRIANGLE

    private fun keyGapPx(): Int = when (host.landscapeShape()) {
        KeyShape.TRIANGLE -> if (portraitTriangles()) -host.portraitRowSizing().triOverlapX else 0
        KeyShape.CIRCLE -> 2.dp(context)
        KeyShape.CUBE -> 2.dp(context)
        else -> 2.dp(context)
    }

    fun build() {
        spaceIndex = 0
        val savedRowCount = KeyboardPrefs.getRowCount(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                when (savedRowCount) {
                    3 -> 2.dp(context)
                    5 -> 10.dp(context)
                    else -> 8.dp(context)
                },
                when (savedRowCount) {
                    4 -> 0.dp(context)
                    else -> 4.dp(context)
                },
                when (savedRowCount) {
                    3 -> 4.dp(context)
                    5 -> 6.dp(context)
                    else -> 8.dp(context)
                },
                4.dp(context)
            )
            clipChildren = false
            clipToPadding = false
        }

        val leftContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                2.2f
            ).apply {
                if (savedRowCount == 5) {
                    leftMargin = 14.dp(context)
                }
            }
        }

        val centerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = if (savedRowCount == 3) -(16.dp(context)) else 8.dp(context)
                rightMargin = if (savedRowCount == 3) 30.dp(context) else 8.dp(context)
            }
        }

        val rightContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (savedRowCount == 3) 1.9f else 2.2f
            ).apply {
                when (savedRowCount) {
                    3 -> {
                        leftMargin = -(24.dp(context))
                        rightMargin = 0.dp(context)
                    }

                    5 -> {
                        leftMargin = 0.dp(context)
                        rightMargin = 14.dp(context)
                    }
                }
            }
        }

        root.addView(leftContainer)
        root.addView(centerContainer)
        root.addView(rightContainer)

        host.keyboardContainer.addView(
            root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        buildLeftLetters(leftContainer)
        buildCenter(centerContainer)
        buildRightLetters(rightContainer)
    }
    private fun createCenterTextKey(label: String): KeyView {
        return KeyView(host.themedContext).apply {
            text = label
            isAllCaps = false

            shape = host.effectiveShape()
            hideFill = true
            hideStroke = true
            customBgColor = Color.TRANSPARENT
            forceSquare = false

            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)

            minWidth = 0
            minimumWidth = 0
            manualLabelSizeSp = 22f

            setTextColor(host.keyColors(label).second)

            // center keys have no split label and no swipe-up alternate
            useSplitLabels = false
            mainLabel = ""
            swipeUpLabel = null

            isClickable = true
            isLongClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            isSelected = false
            highlightColor = Color.TRANSPARENT
            setTextIsSelectable(false)

            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        host.hidePopups()
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()

                        host.commitText(label)
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()
                        true
                    }

                    else -> true
                }
            }
        }
    }
    /**
     * Landscape row overlap (used as a negative top margin). Hexagon rows overlap more as their
     * tips grow with the key height slider; the row spacing slider adds space between rows.
     */
    private fun rowOverlapScaled(keySize: Int): Int {
        val base = rowOverlapPx(keySize)
        val overlap = when (host.currentShape) {
            KeyShape.HEX, KeyShape.HEX_TALL, KeyShape.HEX_HALF_LEFT, KeyShape.HEX_HALF_RIGHT ->
                KeyScale.honeycombOverlap(base, keySize * 0.96f, host.keyHeightStretch())
            else -> base
        }
        return overlap - host.rowSpacingPx()
    }

    /**
     * Width of a letter key. Circles get the portrait key height (a circle, not an ellipse),
     * 4/5-row cubes a square front as tall as in portrait, triangles the portrait width;
     * the height then follows the portrait keys through the height stretch.
     */
    fun keySizePx(): Int {
        val rows = KeyboardPrefs.getRowCount(context)
        return when {
            host.currentShape == KeyShape.CIRCLE ->
                host.portraitBaseShapeHeightPx().roundToInt()
            host.currentShape == KeyShape.CUBE && rows >= 4 ->
                (host.portraitBaseShapeHeightPx() / CUBE_FRONT_FILL).roundToInt()
            portraitTriangles() -> host.portraitRowSizing().keyW
            else -> sideButtonSizePx()
        }
    }

    /** Height of the letter key box before the height stretch. */
    fun keyBoxHeightPx(): Int =
        if (portraitTriangles()) (keySizePx() * TRIANGLE_HEIGHT_RATIO).roundToInt() else keySizePx()

    /** Tuned landscape key size; the side buttons keep it. */
    fun sideButtonSizePx(): Int {
        val savedRowCount = KeyboardPrefs.getRowCount(context)

        return when (host.currentShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when (savedRowCount) {
                    3 -> 33.dp(context)
                    4 -> 38.dp(context)
                    5 -> 36.dp(context)
                    else -> 42.dp(context)
                }
            }

            KeyShape.TRIANGLE -> {
                when (savedRowCount) {
                    3 -> 32.dp(context)
                    5 -> 34.dp(context)
                    else -> 40.dp(context)
                }
            }

            KeyShape.CIRCLE -> {
                when (savedRowCount) {
                    3 -> 32.dp(context)
                    5 -> 34.dp(context)
                    else -> 38.dp(context)
                }
            }

            KeyShape.CUBE -> {
                when (savedRowCount) {
                    3 -> 31.dp(context)
                    5 -> 33.dp(context)
                    else -> 35.dp(context)
                }
            }
        }
    }
    private fun rowOverlapPx(keySize: Int): Int {
        val savedRowCount = KeyboardPrefs.getRowCount(context)

        return when (host.currentShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when (savedRowCount) {
                    5 -> 9.dp(context)
                    4 -> 4.dp(context) // less overlap = rows further apart
                    else -> (keySize * 0.25f).toInt()
                }
            }

            KeyShape.TRIANGLE -> when {
                portraitTriangles() -> -(TRIANGLE_ROW_GAP_DP.dp(context))
                savedRowCount == 5 -> 3.dp(context)
                else -> (keySize * 0.18f).toInt()
            }

            KeyShape.CIRCLE -> if (savedRowCount == 5) 5.dp(context) else 4.dp(context)
            KeyShape.CUBE -> {
                when (savedRowCount) {
                    3 -> -(2.dp(context))
                    5 -> 5.dp(context)
                    else -> 4.dp(context)
                }
            }
        }
    }
    private fun buildLeftLetters(container: LinearLayout) {
        val keySize = keySizePx()
        val rowOverlap = rowOverlapScaled(keySize)
        val keyGap = keyGapPx()
        val keyBoxH = keyBoxHeightPx()
        // Overlapping triangles step by their width minus the overlap
        val halfStep = ((keySize + minOf(keyGap, 0)) / 2f).toInt()

        host.currentRows.forEachIndexed { rowIndex, row ->
            val keys = LandscapeSplit.leftKeys(row.keys, rowIndex, KeyboardPrefs.getRowCount(context))
            if (keys.isEmpty()) return@forEachIndexed

            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            val savedRowCount = KeyboardPrefs.getRowCount(context)

            val baseLeftInset = when (savedRowCount) {
                3 -> 18.dp(context)    // all 3 rows right of the side button
                4 -> 12.dp(context)
                else -> 0
            }

            val middleRowExtraRight = when (savedRowCount) {
                3 -> 9.dp(context)    // only row 2 a bit further right, for centering
                else -> 0
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = -rowOverlap

                leftMargin = when {
                    // 3-row: rows 1 and 3 keep the honeycomb, but all move a little right
                    savedRowCount == 3 && rowIndex in setOf(0, 2) ->
                        -(halfStep / 2) + baseLeftInset

                    // 3-row: row 2 goes right + extra centering
                    savedRowCount == 3 ->
                        baseLeftInset + middleRowExtraRight

                    savedRowCount == 4 && rowIndex in setOf(0, 2) ->
                        -halfStep + baseLeftInset
                    savedRowCount == 4 ->
                        baseLeftInset

                    LandscapeSplit.isOddRow(rowIndex) -> halfStep

                    else -> 0
                }
            }

            val startIndex = 0

            keys.forEachIndexed { i, key ->
                val kv = host.createKey(key)

                spaceIndex = host.applySpecialKeyColors(kv, key, spaceIndex)

                if (kv.shape == KeyShape.TRIANGLE) {
                    // 4/5 rows alternate like portrait (row index included), so the tips interlock
                    val rowFlip = if (portraitTriangles()) rowIndex else 0
                    kv.triangleFlipped = ((rowFlip + startIndex + i) % 2 == 1)
                }

                val lp = LinearLayout.LayoutParams(keySize, keyBoxH).apply {
                    if (i > 0) leftMargin = keyGap
                }

                rowLayout.addView(kv, lp)
            }

            container.addView(rowLayout, rowLp)
        }
    }
    private fun buildRightLetters(container: LinearLayout) {
        val keySize = keySizePx()
        val rowOverlap = rowOverlapScaled(keySize)
        val keyGap = keyGapPx()
        val keyBoxH = keyBoxHeightPx()
        // Overlapping triangles step by their width minus the overlap
        val halfStep = ((keySize + minOf(keyGap, 0)) / 2f).toInt()
        val savedRowCount = KeyboardPrefs.getRowCount(context)
        val baseRightInset = when (savedRowCount) {
            3 -> 14.dp(context)    // all 3 right rows left of the side button
            else -> 0
        }

        host.currentRows.forEachIndexed { rowIndex, row ->
            val visibleRow = row.keys.filterNot {
                it.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
            }

            val keys = LandscapeSplit.rightKeys(row.keys, rowIndex, KeyboardPrefs.getRowCount(context))
            if (keys.isEmpty()) return@forEachIndexed

            val rowShiftX = 0

            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
                translationX = rowShiftX.toFloat()
            }

            val takeCount = when (savedRowCount) {
                3 -> {
                    when (rowIndex) {
                        0 -> 5
                        1 -> 6
                        2 -> 5
                        else -> keys.size
                    }
                }

                else -> {
                    if (LandscapeSplit.isOddRow(rowIndex)) 3 else 4
                }
            }

            val startIndex = (visibleRow.size - takeCount).coerceAtLeast(0)

            keys.forEachIndexed { i, key ->
                val kv = host.createKey(key)

                spaceIndex = host.applySpecialKeyColors(kv, key, spaceIndex)

                if (kv.shape == KeyShape.TRIANGLE) {
                    // 4/5 rows alternate like portrait (row index included), so the tips interlock
                    val rowFlip = if (portraitTriangles()) rowIndex else 0
                    kv.triangleFlipped = ((rowFlip + startIndex + i) % 2 == 1)
                }

                val lp = LinearLayout.LayoutParams(keySize, keyBoxH).apply {
                    if (i > 0) leftMargin = keyGap
                }

                rowLayout.addView(kv, lp)
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) {
                    topMargin = -rowOverlap
                }

                rightMargin = when {
                    // 3-row: all 3 rows to the left, on top of the honeycomb shift
                    savedRowCount == 3 && LandscapeSplit.isOddRow(rowIndex) ->
                        halfStep + baseRightInset

                    savedRowCount == 3 ->
                        baseRightInset

                    savedRowCount == 5 && LandscapeSplit.isOddRow(rowIndex) ->
                        halfStep

                    savedRowCount == 5 ->
                        0

                    LandscapeSplit.isOddRow(rowIndex) ->
                        halfStep

                    else -> 0
                }
            }

            container.addView(rowLayout, rowLp)
        }
    }



    private fun buildCenter(container: LinearLayout) {
        val savedRowCount = KeyboardPrefs.getRowCount(context)

        val cfg = KeyboardPrefs.loadHorizontalCenterLayoutForRowCount(
            context,
            savedRowCount
        )

        // Show every row of the Horizontal (center island) editor
        val centerRows = cfg.rows

        val keySize = when (savedRowCount) {
            3 -> 25.dp(context)
            4 -> 27.dp(context)
            5 -> 28.dp(context)
            else -> 28.dp(context)
        }

        val rowGap = when (savedRowCount) {
            3 -> 4.dp(context)
            4 -> 5.dp(context)
            5 -> 6.dp(context)
            else -> 6.dp(context)
        }

        centerRows.forEach { rowConfig ->
            val rowKeys = rowConfig.keys.filter { key ->
                key.label.isNotBlank() &&
                        !key.longPressBindings.contains("__USER_EMPTY__") &&
                        !key.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
            }

            if (rowKeys.isEmpty()) return@forEach

            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }

            rowKeys.forEachIndexed { i, key ->
                val kv = createCenterTextKey(key.label)

                // Same colors as the regular keys of the main layout
                if (key.label !in setOf("↵", " ", "⇧", "⌫", "😊")) {
                    val (fill, text) = host.keyColors(key.label)
                    kv.customBgColor = fill
                    kv.setTextColor(text)
                }

                val lp = LinearLayout.LayoutParams(
                    keySize,
                    keySize
                ).apply {
                    if (i > 0) leftMargin = 4.dp(context)
                }

                rowLayout.addView(kv, lp)
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (container.isNotEmpty()) topMargin = rowGap
            }

            container.addView(rowLayout, rowLp)
        }
    }

    private companion object {
        /** A cube front is inset 6 % on each side of its box (KeyView.buildCubeFront). */
        const val CUBE_FRONT_FILL = 0.88f

        /** A triangle is 0.4 × 1.4 of its width tall (KeyView.buildTriangle). */
        const val TRIANGLE_HEIGHT_RATIO = 0.56f

        /** Gap between landscape triangle rows. */
        const val TRIANGLE_ROW_GAP_DP = 3
    }
}
