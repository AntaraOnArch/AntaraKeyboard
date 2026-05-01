package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import com.example.antarakeyboard.ui.defaultNumericLayout
import com.example.antarakeyboard.ui.defaultFourRowNumericLayout
import com.example.antarakeyboard.ui.defaultThreeRowNumericLayout
import com.example.antarakeyboard.ui.defaultHorizontalCenterLayout
import com.google.gson.Gson

object KeyboardPrefs {

    private const val PREFS_NAME = "keyboard_prefs"

    private const val KEY_SCALE = "key_scale"
    private const val KEY_SHAPE = "key_shape"
    private const val KEY_LAYOUT_JSON = "layout_json"
    private const val KEY_NUMERIC_LAYOUT_JSON = "numeric_layout_json"
    private const val KEY_HORIZONTAL_CENTER_LAYOUT_JSON = "horizontal_center_layout_json"
    private const val KEY_HEIGHT_PX = "key_height_px"

    // Space colors
    private const val SPACE_LINKED = "space_linked"
    private const val SPACE1_BG = "space1_bg"
    private const val SPACE2_BG = "space2_bg"

    // Enter colors
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

    /* ───────── KEY HEIGHT (px) ───────── */

    fun getKeyHeightPx(context: Context): Int =
        prefs(context).getInt(KEY_HEIGHT_PX, 0) // 0 = auto

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
        val name = prefs(context).getString(KEY_SHAPE, KeyShape.HEX.name) ?: KeyShape.HEX.name
        return KeyShape.valueOf(name)
    }

    fun setShape(context: Context, shape: KeyShape) {
        prefs(context).edit().putString(KEY_SHAPE, shape.name).apply()
    }

    /* ───────── ALPHABET LAYOUT ───────── */
    fun getRowCount(context: Context): Int =
        prefs(context).getInt(KEY_ROW_COUNT, 3)

    fun setRowCount(context: Context, rowCount: Int) {
        prefs(context).edit().putInt(KEY_ROW_COUNT, rowCount).apply()
    }

    fun clearRowCount(context: Context) {
        prefs(context).edit().remove(KEY_ROW_COUNT).apply()
    }
    private fun defaultAlphabetLayoutForRowCount(rowCount: Int): KeyboardConfig {
        return when (rowCount) {
            3 -> defaultThreeRowKeyboardLayoutQwertz
            4 -> defaultFourRowKeyboardLayout
            else -> defaultKeyboardLayout
        }
    }

    private fun defaultNumericLayoutForRowCount(rowCount: Int): KeyboardConfig {
        return when (rowCount) {
            3 -> defaultThreeRowNumericLayout
            4 -> defaultFourRowNumericLayout
            else -> defaultNumericLayout
        }
    }
    fun saveLayout(context: Context, layout: KeyboardConfig) {
        val json = gson.toJson(layout)
        prefs(context).edit().putString(KEY_LAYOUT_JSON, json).apply()
    }

    fun loadLayout(context: Context): KeyboardConfig {
        val rowCount = getRowCount(context)
        val fallback = defaultAlphabetLayoutForRowCount(rowCount)

        val json = prefs(context).getString(KEY_LAYOUT_JSON, null)
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

    fun clearLayout(context: Context) {
        prefs(context).edit().remove(KEY_LAYOUT_JSON).apply()
    }
    /*edge mode*/
    fun edgeKeyForRowCount(rowCount: Int): String {
        return when (rowCount) {
            3 -> KEY_EDGE_MODE_3
            4 -> KEY_EDGE_MODE_4
            else -> KEY_EDGE_MODE_5
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

        val json = prefs(context).getString(KEY_NUMERIC_LAYOUT_JSON, null)
        return if (json.isNullOrBlank()) {
            fallback
        } else {
            runCatching {
                gson.fromJson(json, KeyboardConfig::class.java)
            }.getOrElse {
                fallback
            }
        }
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
        val json = prefs(context).getString(KEY_HORIZONTAL_CENTER_LAYOUT_JSON, null)
        return if (json.isNullOrBlank()) {
            defaultHorizontalCenterLayout
        } else {
            runCatching {
                gson.fromJson(json, KeyboardConfig::class.java)
            }.getOrElse {
                defaultHorizontalCenterLayout
            }
        }
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