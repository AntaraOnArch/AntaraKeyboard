package com.example.antarakeyboard.service

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.antarakeyboard.data.ClassicLayouts
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.RowConfig
import com.example.antarakeyboard.ui.KeyView
import kotlin.math.roundToInt

/**
 * Classic layouts ([KeyShape.CLASSIC], [KeyShape.CLASSIC_NUMBERS]) in both orientations:
 * full-width rows, centered, every key as wide as [ClassicLayouts.keyWidths] says.
 * Side buttons sit at the ends of their row (same look as on the other shapes: no key, just the
 * icon), so the row makes room for them; the keys get narrower rather than buttons over the edge.
 */
internal class ClassicLayoutBuilder(
    private val context: Context,
    private val host: Host
) {
    /** What the builder needs from the keyboard service. */
    interface Host {
        val keyboardContainer: LinearLayout
        val currentShape: KeyShape
        /** Rows of the active layout (alphabet or numeric, with edge keys applied). */
        val currentRows: List<RowConfig>
        val landscape: Boolean
        fun availableWidthPx(): Int
        /** Key height slider factor (1 = default). */
        fun keyHeightFactor(): Float
        fun rowSpacingPx(): Int
        /** Side buttons of the layout that are not empty. */
        fun sideSlots(): List<EdgeSlot>
        fun createKey(key: KeyConfig): KeyView
        /** Colors special keys; returns the next space index. */
        fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int): Int
        /** Side button colors with a thin outline on a key (Shift / ⌫ inside a row). */
        fun styleAsSideButton(kv: KeyView)
        /** A side button view, looking and acting like every other side button. */
        fun createSideButton(slot: EdgeSlot): View
    }

    private class Line(
        val keys: List<KeyConfig>,
        val widths: List<Float>,
        val left: EdgeSlot?,
        val right: EdgeSlot?
    )

    fun build() {
        val shape = host.currentShape
        val slots = host.sideSlots()
        fun slot(row: Int, side: EdgePos.Side) =
            slots.firstOrNull { ClassicLayouts.sideSlotRow(it.index) == row && it.side == side }

        val rowSlots = host.currentRows.mapIndexed { rowIndex, row ->
            Triple(
                row.keys.filterNot { it.longPressBindings.contains(KeyMarkers.EDGE_GHOST) },
                slot(rowIndex, EdgePos.Side.LEFT),
                slot(rowIndex, EdgePos.Side.RIGHT)
            )
        }
        // The widest row sets the key size; space keys stretch every row to the full width
        val (units, widths) = ClassicLayouts.layoutWidths(shape, rowSlots.map { (keys, left, right) ->
            ClassicLayouts.RowSpec(
                keys.map { it.label },
                listOfNotNull(left, right).size * ClassicLayouts.SIDE_BUTTON_UNITS
            )
        })
        val lines = rowSlots.mapIndexed { i, (keys, left, right) -> Line(keys, widths[i], left, right) }
        val unitPx = host.availableWidthPx() / units
        val baseHeightDp = if (host.landscape) LANDSCAPE_KEY_HEIGHT_DP else PORTRAIT_KEY_HEIGHT_DP
        val keyH = (baseHeightDp.dp(context) * host.keyHeightFactor()).roundToInt()

        var spaceIndex = 0
        lines.forEachIndexed { rowIndex, line ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }

            fun add(kv: KeyView, widthUnits: Float) {
                row.addView(kv, LinearLayout.LayoutParams((unitPx * widthUnits).roundToInt(), keyH))
            }

            fun addSide(slot: EdgeSlot) {
                row.addView(
                    host.createSideButton(slot),
                    LinearLayout.LayoutParams((unitPx * ClassicLayouts.SIDE_BUTTON_UNITS).roundToInt(), keyH)
                )
            }

            line.left?.let(::addSide)
            line.keys.forEachIndexed { i, key ->
                val kv = prepare(host.createKey(key), key)
                spaceIndex = host.applySpecialKeyColors(kv, key, spaceIndex)
                // Shift and ⌫ in the row look like side buttons, outlined so the key stays visible
                if (key.label in SIDE_LOOK_LABELS) host.styleAsSideButton(kv)
                add(kv, line.widths[i])
            }
            line.right?.let(::addSide)

            host.keyboardContainer.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) topMargin = ROW_GAP_DP.dp(context) + host.rowSpacingPx()
                }
            )
        }
    }

    /**
     * Classic keys fill their box: no square forcing, the slider sets the height itself.
     * The thin Shift arrow is drawn bigger, as on a side button.
     */
    private fun prepare(kv: KeyView, key: KeyConfig): KeyView = kv.apply {
        forceSquare = false
        heightStretch = 1f
        if (key.label == "⇧") textSize = if (host.landscape) SHIFT_TEXT_SP_LANDSCAPE else SHIFT_TEXT_SP
    }

    private companion object {
        const val PORTRAIT_KEY_HEIGHT_DP = 50
        const val LANDSCAPE_KEY_HEIGHT_DP = 40
        /** Extra space between rows; the rounded keys already leave a small gap. */
        const val ROW_GAP_DP = 2
        val SIDE_LOOK_LABELS = setOf("⇧", "⌫")
        const val SHIFT_TEXT_SP = 22f
        const val SHIFT_TEXT_SP_LANDSCAPE = 18f
    }
}
