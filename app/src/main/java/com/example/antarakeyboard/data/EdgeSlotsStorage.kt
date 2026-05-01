package com.example.antarakeyboard.data

import android.content.Context
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import org.json.JSONArray
import org.json.JSONObject

object EdgeSlotsStorage {
    private const val SP_NAME = "edge_slots"

    private fun keyForRowCount(rowCount: Int): String {
        return when (normalizeRowCount(rowCount)) {
            3 -> "edge_slots_config_3"
            4 -> "edge_slots_config_4"
            else -> "edge_slots_config_5"
        }
    }

    private fun normalizeRowCount(rowCount: Int): Int {
        return when (rowCount) {
            3, 4, 5 -> rowCount
            else -> 5
        }
    }

    private fun slotCountForRowCount(rowCount: Int): Int {
        return when (normalizeRowCount(rowCount)) {
            3 -> 3
            4 -> 4
            else -> 6
        }
    }

    private fun fixedSideForIndex(rowCount: Int, index: Int): EdgePos.Side {
        return when (normalizeRowCount(rowCount)) {
            4 -> when (index) {
                0 -> EdgePos.Side.RIGHT   // row 1
                1 -> EdgePos.Side.LEFT    // row 2
                2 -> EdgePos.Side.RIGHT   // row 3
                else -> EdgePos.Side.LEFT // row 4
            }

            3 -> when (index) {
                0 -> EdgePos.Side.LEFT
                1 -> EdgePos.Side.RIGHT
                else -> EdgePos.Side.LEFT
            }

            else -> if (index % 2 == 0) EdgePos.Side.LEFT else EdgePos.Side.RIGHT
        }
    }

    fun defaultSlots(rowCount: Int): List<EdgeSlot> {
        return when (normalizeRowCount(rowCount)) {
            3 -> listOf(
                // preview-safe defaults dok 3-row još nije gotov
                EdgeSlot(0, EdgePos.Side.LEFT, EdgeActionType.BACKSPACE),
                EdgeSlot(1, EdgePos.Side.RIGHT, EdgeActionType.SHIFT),
                EdgeSlot(2, EdgePos.Side.LEFT, EdgeActionType.NONE),
            )

            4 -> listOf(
                EdgeSlot(0, EdgePos.Side.RIGHT, EdgeActionType.BACKSPACE),
                EdgeSlot(1, EdgePos.Side.LEFT, EdgeActionType.SHIFT),
                EdgeSlot(2, EdgePos.Side.RIGHT, EdgeActionType.NONE),
                EdgeSlot(3, EdgePos.Side.LEFT, EdgeActionType.NONE),
            )

            else -> listOf(
                EdgeSlot(0, EdgePos.Side.LEFT, EdgeActionType.SHIFT),
                EdgeSlot(1, EdgePos.Side.RIGHT, EdgeActionType.BACKSPACE),
                EdgeSlot(2, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(3, EdgePos.Side.RIGHT, EdgeActionType.NONE),
                EdgeSlot(4, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(5, EdgePos.Side.RIGHT, EdgeActionType.NONE),
            )
        }
    }

    fun defaultSlots(ctx: Context): List<EdgeSlot> {
        val rowCount = KeyboardPrefs.getRowCount(ctx)
        return defaultSlots(rowCount)
    }

    fun load(ctx: Context): List<EdgeSlot> {
        val rowCount = normalizeRowCount(KeyboardPrefs.getRowCount(ctx))
        val key = keyForRowCount(rowCount)
        val expectedCount = slotCountForRowCount(rowCount)

        val sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(key, null) ?: return defaultSlots(rowCount)

        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<EdgeSlot>()

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val index = o.optInt("index", -1)
                val type = EdgeActionType.valueOf(o.getString("type"))
                val value = if (o.has("value") && !o.isNull("value")) {
                    o.getString("value")
                } else {
                    null
                }

                if (index !in 0 until expectedCount) continue

                out.add(
                    EdgeSlot(
                        index = index,
                        side = fixedSideForIndex(rowCount, index),
                        type = type,
                        value = value
                    )
                )
            }

            if (out.size != expectedCount) {
                defaultSlots(rowCount)
            } else {
                out.sortedBy { it.index }
            }
        } catch (_: Throwable) {
            defaultSlots(rowCount)
        }
    }

    fun save(ctx: Context, slots: List<EdgeSlot>) {
        val rowCount = normalizeRowCount(KeyboardPrefs.getRowCount(ctx))
        val key = keyForRowCount(rowCount)
        val expectedCount = slotCountForRowCount(rowCount)

        val normalized = if (slots.size == expectedCount) {
            slots
        } else {
            defaultSlots(rowCount)
        }.sortedBy { it.index }
            .mapIndexed { index, slot ->
                slot.copy(
                    index = index,
                    side = fixedSideForIndex(rowCount, index)
                )
            }

        val arr = JSONArray()
        normalized.forEach { slot ->
            val o = JSONObject()
            o.put("index", slot.index)
            o.put("side", slot.side.name)
            o.put("type", slot.type.name)
            if (slot.value != null) {
                o.put("value", slot.value)
            } else {
                o.put("value", JSONObject.NULL)
            }
            arr.put(o)
        }

        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, arr.toString())
            .apply()
    }

    fun reset(ctx: Context) {
        val rowCount = normalizeRowCount(KeyboardPrefs.getRowCount(ctx))
        save(ctx, defaultSlots(rowCount))
    }

    fun resetAll(ctx: Context) {
        val sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .remove(keyForRowCount(3))
            .remove(keyForRowCount(4))
            .remove(keyForRowCount(5))
            .apply()
    }
}