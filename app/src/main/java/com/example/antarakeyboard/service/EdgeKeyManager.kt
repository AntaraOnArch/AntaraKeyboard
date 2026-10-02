package com.example.antarakeyboard.service

import android.content.Context
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyboardConfig

/**
 * Manages edge key configuration for the keyboard layout.
 * Handles hiding main layout keys when they appear as edge/side buttons.
 * Extracted from MyKeyboardService for better separation of concerns.
 */
class EdgeKeyManager(private val context: Context) {

    /**
     * Applies edge key configuration to the keyboard layout.
     * Keys that are configured as edge buttons are replaced with ghost placeholders.
     */
    fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        val copy = cfg.copy(
            rows = cfg.rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        key.copy(longPressBindings = key.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = cfg.specialLeft.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList(),
            specialRight = cfg.specialRight.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList()
        )

        val slots = EdgeSlotsStorage.load(context).filter { it.type != EdgeActionType.NONE }
        if (slots.isEmpty()) return copy

        val labelsToHideFromMainLayout = buildSet<String> {
            slots.forEach { s ->
                when (s.type) {
                    EdgeActionType.SHIFT -> add("⇧")
                    EdgeActionType.BACKSPACE -> add("⌫")
                    // ENTER stays in both main layout and as side button
                    EdgeActionType.ENTER -> Unit
                    EdgeActionType.SPACE -> Unit
                    EdgeActionType.CHAR -> Unit
                    EdgeActionType.EMOJI_PICKER -> Unit
                    EdgeActionType.NONE -> Unit
                }
            }
        }

        fun replaceWithGhostPlaceholder(list: MutableList<KeyConfig>) {
            for (i in list.indices) {
                val key = list[i]
                if (key.label in labelsToHideFromMainLayout) {
                    list[i] = key.copy(
                        label = "",
                        longPressBindings = mutableListOf(KeyMarkers.EDGE_GHOST)
                    )
                }
            }
        }

        copy.rows.forEach { replaceWithGhostPlaceholder(it.keys) }
        replaceWithGhostPlaceholder(copy.specialLeft)
        replaceWithGhostPlaceholder(copy.specialRight)

        return copy
    }
}
