package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultFourRowNumericLayout
import com.example.antarakeyboard.ui.defaultHorizontalCenterLayout
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultNumericLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import com.example.antarakeyboard.ui.defaultThreeRowNumericLayout
import com.google.gson.Gson

object KeyboardPrefs {

    private const val PREFS_NAME = "keyboard_prefs"

    private const val KEY_SCALE = "key_scale"
    private const val KEY_SHAPE = "key_shape"

    private const val KEY_LAYOUT_JSON = "layout_json"
    private const val KEY_ALPHABET_LAYOUT_3 = "alphabet_layout_3"
    private const val KEY_ALPHABET_LAYOUT_4 = "alphabet_layout_4"

    private const val KEY_NUMERIC_LAYOUT_JSON = "numeric_layout_json"
    private const val KEY_HORIZONTAL_CENTER_LAYOUT_JSON = "horizontal_center_layout_json"
    private const val KEY_HEIGHT_PX = "key_height_px"

    private const val SPACE_LINKED = "space_linked"
    private const val SPACE1_BG = "space1_bg"
    private const val SPACE2_BG = "space2_bg"

    private const val ENTER_BG = "enter_bg"
    private const val ENTER_ICON = "enter_icon"

    private const val KEY_ROW_COUNT = "row_count"

    private const val KEY_EDGE_MODE = "edge_mode"
    private const val KEY_EDGE_MODE_3 = "edge_mode_3"
    private const val KEY_EDGE_MODE_4 = "edge_mode_4"
    private const val KEY_EDGE_MODE_5 = "edge_mode_5"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ───────── KEY HEIGHT ───────── */

    fun getKeyHeightPx(context: Context): Int =
        prefs(context).getInt(KEY_HEIGHT_PX, 0)

    fun setKeyHeightPx(context: Context, px: Int) {
        prefs(context).edit().putInt(KEY_HEIGHT_PX, px).apply()
    }

    fun clearKeyHeightPx(context: Context) {
        prefs(context).edit().remove(KEY_HEIGHT_PX).apply()
    }

    /* ───────── SCALE ───────── */

    fun getScale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1.0f)

    fun setScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    /* ───────── SHAPE ───────── */

    fun getShape(context: Context): KeyShape {
        val name = prefs(context).getString(KEY_SHAPE, KeyShape.HEX.name)
            ?: KeyShape.HEX.name

        return runCatching {
            KeyShape.valueOf(name)
        }.getOrDefault(KeyShape.HEX)
    }

    fun setShape(context: Context, shape: KeyShape) {
        prefs(context).edit().putString(KEY_SHAPE, shape.name).apply()
    }

    /* ───────── ROW COUNT ───────── */

    fun getRowCount(context: Context): Int =
        prefs(context).getInt(KEY_ROW_COUNT, 3)

    fun setRowCount(context: Context, rowCount: Int) {
        prefs(context).edit().putInt(KEY_ROW_COUNT, rowCount).apply()
    }

    fun clearRowCount(context: Context) {
        prefs(context).edit().remove(KEY_ROW_COUNT).apply()
    }

    /* ───────── DEFAULT LAYOUTS ───────── */

    private fun defaultAlphabetLayoutForRowCount(rowCount: Int): KeyboardConfig {
        return when (rowCount) {
            3 -> defaultThreeRowKeyboardLayoutQwertz
            4 -> defaultFourRowKeyboardLayout
            5 -> defaultKeyboardLayout
            else -> defaultThreeRowKeyboardLayoutQwertz
        }
    }

    private fun defaultNumericLayoutForRowCount(rowCount: Int): KeyboardConfig {
        return when (rowCount) {
            3 -> defaultThreeRowNumericLayout
            4 -> defaultFourRowNumericLayout
            5 -> defaultNumericLayout
            else -> defaultThreeRowNumericLayout
        }
    }

    /* ───────── GENERIC JSON SAVE / LOAD ───────── */

    private fun saveLayoutWithKey(
        context: Context,
        key: String,
        layout: KeyboardConfig
    ) {
        prefs(context).edit()
            .putString(key, gson.toJson(layout))
            .apply()
    }

    private fun loadLayoutWithKey(
        context: Context,
        key: String,
        fallback: KeyboardConfig
    ): KeyboardConfig {
        val json = prefs(context).getString(key, null)

        return if (!json.isNullOrBlank()) {
            runCatching {
                gson.fromJson(json, KeyboardConfig::class.java)
            }.getOrElse {
                fallback
            }
        } else {
            fallback
        }
    }

    /* ───────── ALPHABET LAYOUT ───────── */

    fun saveAlphabetLayoutForRowCount(
        context: Context,
        rowCount: Int,
        config: KeyboardConfig
    ) {
        when (rowCount) {
            3 -> saveLayoutWithKey(context, KEY_ALPHABET_LAYOUT_3, config)
            4 -> saveLayoutWithKey(context, KEY_ALPHABET_LAYOUT_4, config)
            5 -> saveLayout(context, config)
            else -> saveLayoutWithKey(context, KEY_ALPHABET_LAYOUT_3, config)
        }
    }

    fun loadAlphabetLayoutForRowCount(
        context: Context,
        rowCount: Int
    ): KeyboardConfig {
        return when (rowCount) {
            3 -> loadLayoutWithKey(
                context,
                KEY_ALPHABET_LAYOUT_3,
                defaultThreeRowKeyboardLayoutQwertz
            )

            4 -> loadLayoutWithKey(
                context,
                KEY_ALPHABET_LAYOUT_4,
                defaultFourRowKeyboardLayout
            )

            5 -> loadLayout(context)

            else -> defaultThreeRowKeyboardLayoutQwertz
        }
    }

    fun clearAlphabetLayoutForRowCount(context: Context, rowCount: Int) {
        when (rowCount) {
            3 -> prefs(context).edit().remove(KEY_ALPHABET_LAYOUT_3).apply()
            4 -> prefs(context).edit().remove(KEY_ALPHABET_LAYOUT_4).apply()
            5 -> clearLayout(context)
            else -> prefs(context).edit().remove(KEY_ALPHABET_LAYOUT_3).apply()
        }
    }

    fun saveLayout(context: Context, layout: KeyboardConfig) {
        prefs(context).edit()
            .putString(KEY_LAYOUT_JSON, gson.toJson(layout))
            .apply()
    }

    fun loadLayout(context: Context): KeyboardConfig {
        val rowCount = getRowCount(context)
        val fallback = defaultAlphabetLayoutForRowCount(rowCount)

        return loadLayoutWithKey(
            context = context,
            key = KEY_LAYOUT_JSON,
            fallback = fallback
        )
    }

    fun clearLayout(context: Context) {
        prefs(context).edit().remove(KEY_LAYOUT_JSON).apply()
    }

    /* ───────── EDGE MODE ───────── */

    fun edgeKeyForRowCount(rowCount: Int): String {
        return when (rowCount) {
            3 -> KEY_EDGE_MODE_3
            4 -> KEY_EDGE_MODE_4
            5 -> KEY_EDGE_MODE_5
            else -> KEY_EDGE_MODE_3
        }
    }

    fun getEdgeModeKey(context: Context): String {
        val rowCount = getRowCount(context)
        return edgeKeyForRowCount(rowCount)
    }

    fun setEdgeModeKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_EDGE_MODE, key).apply()
    }

    fun getSavedEdgeModeKey(context: Context): String {
        return prefs(context).getString(KEY_EDGE_MODE, getEdgeModeKey(context))
            ?: getEdgeModeKey(context)
    }

    /* ───────── NUMERIC LAYOUT ───────── */

    fun saveNumericLayout(context: Context, config: KeyboardConfig) {
        prefs(context).edit()
            .putString(KEY_NUMERIC_LAYOUT_JSON, gson.toJson(config))
            .apply()
    }

    fun loadNumericLayout(context: Context): KeyboardConfig {
        val rowCount = getRowCount(context)
        val fallback = defaultNumericLayoutForRowCount(rowCount)

        return loadLayoutWithKey(
            context = context,
            key = KEY_NUMERIC_LAYOUT_JSON,
            fallback = fallback
        )
    }

    fun clearNumericLayout(context: Context) {
        prefs(context).edit().remove(KEY_NUMERIC_LAYOUT_JSON).apply()
    }

    /* ───────── HORIZONTAL CENTER LAYOUT ───────── */

    fun saveHorizontalCenterLayout(context: Context, config: KeyboardConfig) {
        prefs(context).edit()
            .putString(KEY_HORIZONTAL_CENTER_LAYOUT_JSON, gson.toJson(config))
            .apply()
    }

    fun loadHorizontalCenterLayout(context: Context): KeyboardConfig {
        return loadLayoutWithKey(
            context = context,
            key = KEY_HORIZONTAL_CENTER_LAYOUT_JSON,
            fallback = defaultHorizontalCenterLayout
        )
    }

    fun clearHorizontalCenterLayout(context: Context) {
        prefs(context).edit().remove(KEY_HORIZONTAL_CENTER_LAYOUT_JSON).apply()
    }

    /* ───────── SPACE COLORS ───────── */

    fun isSpaceLinked(context: Context): Boolean =
        prefs(context).getBoolean(SPACE_LINKED, true)

    fun setSpaceLinked(context: Context, linked: Boolean) {
        prefs(context).edit().putBoolean(SPACE_LINKED, linked).apply()
    }

    fun getSpace1Bg(context: Context): Int =
        prefs(context).getInt(SPACE1_BG, 0xFF3E3E3E.toInt())

    fun getSpace2Bg(context: Context): Int =
        prefs(context).getInt(SPACE2_BG, 0xFF3E3E3E.toInt())

    fun setSpace1Bg(context: Context, color: Int) {
        prefs(context).edit().putInt(SPACE1_BG, color).apply()
    }

    fun setSpace2Bg(context: Context, color: Int) {
        prefs(context).edit().putInt(SPACE2_BG, color).apply()
    }

    fun setSpaceColors(context: Context, c1: Int, c2: Int, linked: Boolean) {
        prefs(context).edit()
            .putInt(SPACE1_BG, c1)
            .putInt(SPACE2_BG, c2)
            .putBoolean(SPACE_LINKED, linked)
            .apply()
    }

    /* ───────── ENTER COLORS ───────── */

    fun getEnterBg(context: Context): Int =
        prefs(context).getInt(ENTER_BG, 0xFF2E55E7.toInt())

    fun getEnterIcon(context: Context): Int =
        prefs(context).getInt(ENTER_ICON, 0xFFFFFFFF.toInt())

    fun setEnterBg(context: Context, color: Int) {
        prefs(context).edit().putInt(ENTER_BG, color).apply()
    }

    fun setEnterIcon(context: Context, color: Int) {
        prefs(context).edit().putInt(ENTER_ICON, color).apply()
    }

    fun setEnterColors(context: Context, bg: Int, icon: Int) {
        prefs(context).edit()
            .putInt(ENTER_BG, bg)
            .putInt(ENTER_ICON, icon)
            .apply()
    }
}