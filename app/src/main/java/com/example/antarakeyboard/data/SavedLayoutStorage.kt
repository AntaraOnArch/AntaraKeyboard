package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Storage for saved user layouts (keyboard configuration + colors).
 * Allows users to restore their layouts after accidental reset.
 */
object SavedLayoutStorage {

    private const val PREFS_NAME = "saved_layouts_prefs"
    private const val KEY_SAVED_LAYOUTS = "saved_layouts"
    private const val MAX_SAVED_LAYOUTS = 5

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Represents a complete saved layout configuration
     */
    data class SavedLayout(
        val id: Long = System.currentTimeMillis(),
        val name: String,
        val timestamp: Long = System.currentTimeMillis(),
        // Layout data
        val rowCount: Int,
        val keyShape: String,
        val alphabetLayoutJson: String,
        val numericLayoutJson: String,
        val horizontalCenterLayoutJson: String,
        val edgeSlotsJson: String,
        // Color data
        val keyFill: Int,
        val keyText: Int,
        val backgroundColor: Int,
        val backgroundUseTheme: Boolean,
        val space1Bg: Int,
        val space2Bg: Int,
        val spaceLinked: Boolean,
        val enterBg: Int,
        val enterIcon: Int,
        val sideBg: Int,
        val sideText: Int,
        val sideUseTheme: Boolean
    )

    /**
     * Get all saved layouts
     */
    fun getSavedLayouts(context: Context): List<SavedLayout> {
        val json = prefs(context).getString(KEY_SAVED_LAYOUTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedLayout>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save current layout configuration
     */
    fun saveCurrentLayout(context: Context, name: String): SavedLayout {
        val rowCount = KeyboardPrefs.getRowCount(context)

        val savedLayout = SavedLayout(
            name = name,
            rowCount = rowCount,
            keyShape = KeyboardPrefs.getShape(context).name,
            alphabetLayoutJson = gson.toJson(KeyboardPrefs.loadAlphabetLayoutForRowCount(context, rowCount)),
            numericLayoutJson = gson.toJson(KeyboardPrefs.loadNumericLayoutForRowCount(context, rowCount)),
            horizontalCenterLayoutJson = gson.toJson(KeyboardPrefs.loadHorizontalCenterLayoutForRowCount(context, rowCount)),
            edgeSlotsJson = gson.toJson(EdgeSlotsStorage.load(context)),
            keyFill = KeyboardPrefs.getKeysBg(context),
            keyText = KeyboardPrefs.getKeysTextColor(context),
            // Dark / Light / Transparent are stored as their resolved color
            backgroundColor = KeyboardPrefs.resolveKeyboardBackground(context, PrefsManager.isDarkMode(context)),
            backgroundUseTheme = KeyboardPrefs.getBackgroundUseTheme(context),
            space1Bg = KeyboardPrefs.getSpace1Bg(context),
            space2Bg = KeyboardPrefs.getSpace2Bg(context),
            spaceLinked = KeyboardPrefs.isSpaceLinked(context),
            enterBg = KeyboardPrefs.getEnterBg(context),
            enterIcon = KeyboardPrefs.getEnterIcon(context),
            sideBg = KeyboardPrefs.getSideButtonsBg(context),
            sideText = KeyboardPrefs.getSideButtonsTextColor(context),
            sideUseTheme = KeyboardPrefs.getSideButtonsUseThemeBg(context)
        )

        val layouts = getSavedLayouts(context).toMutableList()

        // Add new layout at the beginning
        layouts.add(0, savedLayout)

        // Keep only last MAX_SAVED_LAYOUTS
        while (layouts.size > MAX_SAVED_LAYOUTS) {
            layouts.removeAt(layouts.lastIndex)
        }

        prefs(context).edit()
            .putString(KEY_SAVED_LAYOUTS, gson.toJson(layouts))
            .apply()

        return savedLayout
    }

    /**
     * Restore a saved layout
     */
    fun restoreLayout(context: Context, savedLayout: SavedLayout) {
        // Restore row count and shape
        KeyboardPrefs.setRowCount(context, savedLayout.rowCount)
        KeyboardPrefs.setShape(context, KeyShape.valueOf(savedLayout.keyShape))

        // Restore layouts
        val alphabetLayout: KeyboardConfig = gson.fromJson(
            savedLayout.alphabetLayoutJson,
            KeyboardConfig::class.java
        )
        val numericLayout: KeyboardConfig = gson.fromJson(
            savedLayout.numericLayoutJson,
            KeyboardConfig::class.java
        )
        val horizontalCenterLayout: KeyboardConfig = gson.fromJson(
            savedLayout.horizontalCenterLayoutJson,
            KeyboardConfig::class.java
        )

        KeyboardPrefs.saveAlphabetLayoutForRowCount(context, savedLayout.rowCount, alphabetLayout)
        KeyboardPrefs.saveNumericLayoutForRowCount(context, savedLayout.rowCount, numericLayout)
        KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(context, savedLayout.rowCount, horizontalCenterLayout)

        // Restore edge slots
        val edgeSlotsType = object : TypeToken<List<EdgeSlot>>() {}.type
        val edgeSlots: List<EdgeSlot> = gson.fromJson(savedLayout.edgeSlotsJson, edgeSlotsType)
        EdgeSlotsStorage.save(context, edgeSlots)

        applyColors(context, savedLayout)
    }

    /** Applies only the colors of a saved layout to the keyboard (layout and shape stay as they are). */
    fun applyColors(context: Context, savedLayout: SavedLayout) {
        KeyboardPrefs.setKeysColors(context, savedLayout.keyFill, savedLayout.keyText, true)
        KeyboardPrefs.setBackgroundColor(context, savedLayout.backgroundColor, savedLayout.backgroundUseTheme)
        KeyboardPrefs.setSpaceColors(context, savedLayout.space1Bg, savedLayout.space2Bg, savedLayout.spaceLinked)
        KeyboardPrefs.setEnterColors(context, savedLayout.enterBg, savedLayout.enterIcon)
        KeyboardPrefs.setSideButtonsColors(context, savedLayout.sideBg, savedLayout.sideText, savedLayout.sideUseTheme)
    }

    /**
     * Delete a saved layout by ID
     */
    fun deleteLayout(context: Context, layoutId: Long) {
        val layouts = getSavedLayouts(context).toMutableList()
        layouts.removeAll { it.id == layoutId }
        prefs(context).edit()
            .putString(KEY_SAVED_LAYOUTS, gson.toJson(layouts))
            .apply()
    }

    /**
     * Rename a saved layout
     */
    fun renameLayout(context: Context, layoutId: Long, newName: String) {
        val layouts = getSavedLayouts(context).toMutableList()
        val index = layouts.indexOfFirst { it.id == layoutId }
        if (index >= 0) {
            layouts[index] = layouts[index].copy(name = newName)
            prefs(context).edit()
                .putString(KEY_SAVED_LAYOUTS, gson.toJson(layouts))
                .apply()
        }
    }

    /**
     * Update colors of a saved layout
     */
    fun updateLayoutColors(
        context: Context,
        layoutId: Long,
        keyFill: Int,
        keyText: Int,
        spaceBg: Int,
        enterBg: Int,
        enterIcon: Int,
        backgroundColor: Int
    ) {
        val layouts = getSavedLayouts(context).toMutableList()
        val index = layouts.indexOfFirst { it.id == layoutId }
        if (index >= 0) {
            layouts[index] = layouts[index].copy(
                keyFill = keyFill,
                keyText = keyText,
                space1Bg = spaceBg,
                space2Bg = spaceBg,
                enterBg = enterBg,
                enterIcon = enterIcon,
                backgroundColor = backgroundColor
            )
            prefs(context).edit()
                .putString(KEY_SAVED_LAYOUTS, gson.toJson(layouts))
                .apply()
        }
    }

    /**
     * Format timestamp for display
     */
    fun formatTimestamp(timestamp: Long): String {
        // Date/time order and separators follow the device locale
        val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        return format.format(java.util.Date(timestamp))
    }
}
