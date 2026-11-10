package com.example.antarakeyboard.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.antarakeyboard.R
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.data.ClassicLayouts
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot

/** Set layout → Side buttons page: tap a slot to pick its action, drag to swap. Autosaves. */
class SideButtonsPage(private val activity: Activity) {

    fun setup(container: LinearLayout, onChanged: () -> Unit) {
        container.removeAllViews()

        // Layout id: 3 / 4 / 5 rows, or a classic shape (6 slots like 5 rows: left + right per letter row)
        val selectedRowCount = KeyboardPrefs.getLayoutId(activity)

        val title = TextView(activity).apply {
            text = activity.getString(R.string.side_title)
            textSize = 18f
            setPadding(0, 0, 0, 10.dp(this))
        }

        val hint = TextView(activity).apply {
            text = when (selectedRowCount) {
                4 -> activity.getString(R.string.side_hint_4)
                3 -> activity.getString(R.string.side_hint_3)
                ClassicLayouts.LAYOUT_CLASSIC -> activity.getString(R.string.side_hint_classic)
                else -> activity.getString(R.string.side_hint_5)
            }
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, 10.dp(this))
        }

        val grid = GridLayout(activity).apply {
            rowCount = when (selectedRowCount) {
                3 -> 3
                4 -> 4
                else -> 3
            }
            columnCount = 2
        }

        val slotCount = when (selectedRowCount) {
            3 -> 3
            4 -> 4
            else -> 6
        }

        val slots = EdgeSlotsStorage.load(activity).toMutableList()

        while (slots.size < slotCount) {
            val i = slots.size
            slots.add(
                EdgeSlot(
                    index = i,
                    side = fixedSideForRowCount(selectedRowCount, i),
                    type = EdgeActionType.NONE
                )
            )
        }

        while (slots.size > slotCount) {
            slots.removeAt(slots.lastIndex)
        }

        fun fixSlotPosition(i: Int, s: EdgeSlot): EdgeSlot {
            return s.copy(
                index = i,
                side = fixedSideForRowCount(selectedRowCount, i)
            )
        }

        fun startDragCompat(v: View, fromIndex: Int) {
            val data = ClipData.newPlainText("fromIndex", fromIndex.toString())
            val shadow = View.DragShadowBuilder(v)

            v.startDragAndDrop(data, shadow, null, 0)
        }

        fun parseFromIndex(e: DragEvent): Int? {
            val item = e.clipData?.getItemAt(0)?.text?.toString() ?: return null
            return item.toIntOrNull()
        }

        fun persist() {
            EdgeSlotsStorage.save(activity, slots)
            onChanged()
        }

        fun rebuild() {
            grid.removeAllViews()

            slots.forEachIndexed { index, slot ->
                val btn = Button(activity).apply {
                    text = when (selectedRowCount) {
                        4 -> {
                            val rowLabel = activity.getString(R.string.side_row_label, index.coerceAtMost(3) + 1)
                            "$rowLabel • ${slot.label.ifBlank { activity.getString(R.string.edge_none) }}"
                        }

                        else -> slot.label.ifBlank { activity.getString(R.string.edge_none) }
                    }

                    isAllCaps = false
                    tag = index
                    setPadding(8.dp(this), 14.dp(this), 8.dp(this), 14.dp(this))

                    setOnClickListener {
                        showEdgeTypePicker(slot) { newSlot ->
                            val normalized = fixSlotPosition(index, normalizeSlot(newSlot))
                            enforceNoDuplicates(slots, index, normalized)
                            slots[index] = normalized
                            persist()
                            rebuild()
                        }
                    }

                    setOnLongClickListener { v ->
                        startDragCompat(v, index)
                        true
                    }

                    setOnDragListener { v, e ->
                        when (e.action) {
                            DragEvent.ACTION_DRAG_STARTED -> {
                                e.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                            }

                            DragEvent.ACTION_DRAG_ENTERED -> {
                                v.alpha = 0.65f
                                true
                            }

                            DragEvent.ACTION_DRAG_EXITED -> {
                                v.alpha = 1f
                                true
                            }

                            DragEvent.ACTION_DROP -> {
                                v.alpha = 1f

                                val from = parseFromIndex(e) ?: return@setOnDragListener true
                                val to = (v.tag as? Int) ?: return@setOnDragListener true
                                if (from == to) return@setOnDragListener true

                                val tmp = slots[from]
                                slots[from] = slots[to]
                                slots[to] = tmp

                                slots[from] = fixSlotPosition(from, slots[from])
                                slots[to] = fixSlotPosition(to, slots[to])

                                persist()
                                rebuild()
                                true
                            }

                            DragEvent.ACTION_DRAG_ENDED -> {
                                v.alpha = 1f
                                true
                            }

                            else -> true
                        }
                    }
                }

                val col = if (fixedSideForRowCount(selectedRowCount, index) == EdgePos.Side.LEFT) {
                    0
                } else {
                    1
                }

                val row = when (selectedRowCount) {
                    4 -> index
                    else -> index / 2
                }

                val lp = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(col, 1f)
                    rowSpec = GridLayout.spec(row)
                    setMargins(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
                }

                grid.addView(btn, lp)
            }
        }

        rebuild()

        container.addView(title)
        container.addView(hint)
        container.addView(grid)
    }

    private fun fixedSideForRowCount(rowCount: Int, index: Int): EdgePos.Side {
        return when (rowCount) {
            4 -> when (index) {
                0 -> EdgePos.Side.RIGHT
                1 -> EdgePos.Side.LEFT
                2 -> EdgePos.Side.RIGHT
                else -> EdgePos.Side.LEFT
            }

            else -> if (index % 2 == 0) EdgePos.Side.LEFT else EdgePos.Side.RIGHT
        }
    }

    private fun normalizeSlot(s: EdgeSlot): EdgeSlot {
        return when (s.type) {
            EdgeActionType.CHAR -> s.copy(value = s.value?.takeIf { it.isNotBlank() })
            else -> s.copy(value = null)
        }
    }

    private fun enforceNoDuplicates(
        slots: MutableList<EdgeSlot>,
        changedIndex: Int,
        chosen: EdgeSlot
    ) {
        val uniqueTypes = setOf(
            EdgeActionType.SHIFT,
            EdgeActionType.BACKSPACE,
            EdgeActionType.ENTER,
            EdgeActionType.SPACE
        )

        if (chosen.type in uniqueTypes) {
            for (i in slots.indices) {
                if (i != changedIndex && slots[i].type == chosen.type) {
                    slots[i] = slots[i].copy(type = EdgeActionType.NONE, value = null)
                }
            }
        }

        if (chosen.type == EdgeActionType.CHAR && !chosen.value.isNullOrBlank()) {
            for (i in slots.indices) {
                if (i != changedIndex &&
                    slots[i].type == EdgeActionType.CHAR &&
                    slots[i].value == chosen.value
                ) {
                    slots[i] = slots[i].copy(type = EdgeActionType.NONE, value = null)
                }
            }
        }
    }

    private fun showEdgeTypePicker(
        oldSlot: EdgeSlot,
        onSelected: (EdgeSlot) -> Unit
    ) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 8.dp(this))
        }

        val actions = listOf(
            EdgeActionType.SHIFT to activity.getString(R.string.edge_shift),
            EdgeActionType.BACKSPACE to activity.getString(R.string.edge_backspace),
            EdgeActionType.ENTER to activity.getString(R.string.edge_enter),
            EdgeActionType.SPACE to activity.getString(R.string.edge_space),
            EdgeActionType.EMOJI_PICKER to activity.getString(R.string.edge_emoji),
            EdgeActionType.NONE to activity.getString(R.string.edge_none)
        )

        val actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Picking an action or a character closes the picker
        var dialog: AlertDialog? = null
        fun select(slot: EdgeSlot) {
            onSelected(slot)
            dialog?.dismiss()
        }

        actions.forEach { (type, label) ->
            val b = Button(activity).apply {
                text = label
                isAllCaps = false
            }
            b.setOnClickListener {
                select(oldSlot.copy(type = type, value = null))
            }
            actionsRow.addView(b)
        }

        root.addView(actionsRow)

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.edge_special_chars)
            setPadding(0, 10.dp(this), 0, 6.dp(this))
        })

        val scroll = ScrollView(activity)
        val grid = GridLayout(activity).apply {
            columnCount = 6
        }

        SpecialChars.ALL.forEach { ch ->
            val b = Button(activity).apply {
                text = ch
                isAllCaps = false
                minHeight = 44.dp(this)
                minWidth = 44.dp(this)
                setPadding(0, 0, 0, 0)
            }
            b.setOnClickListener {
                select(oldSlot.copy(type = EdgeActionType.CHAR, value = ch))
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4.dp(activity), 4.dp(activity), 4.dp(activity), 4.dp(activity))
            }
            grid.addView(b, lp)
        }

        scroll.addView(grid)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                320.dp(activity)
            )
        )

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.edge_choose_title)
            .setView(root)
            .setNegativeButton(R.string.action_close, null)
            .show()
    }
}
