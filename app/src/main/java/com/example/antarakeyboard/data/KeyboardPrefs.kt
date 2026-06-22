package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.example.antarakeyboard.model.KeyConfig
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

    // NOVO: Svaki row count ima svoj alphabet layout ključ
    private const val KEY_ALPHABET_LAYOUT_3 = "alphabet_layout_3"
    private const val KEY_ALPHABET_LAYOUT_4 = "alphabet_layout_4"
    private const val KEY_ALPHABET_LAYOUT_5 = "alphabet_layout_5"

    // NOVO: Svaki row count ima svoj numeric layout ključ
    private const val KEY_NUMERIC_LAYOUT_3 = "numeric_layout_3"
    private const val KEY_NUMERIC_LAYOUT_4 = "numeric_layout_4"
    private const val KEY_NUMERIC_LAYOUT_5 = "numeric_layout_5"

    // Horizontal center layout ključevi
    private const val KEY_HORIZONTAL_CENTER_LAYOUT_3 = "horizontal_center_layout_3"
    private const val KEY_HORIZONTAL_CENTER_LAYOUT_4 = "horizontal_center_layout_4"
    private const val KEY_HORIZONTAL_CENTER_LAYOUT_5 = "horizontal_center_layout_5"

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

    private fun alphabetLayoutKeyForRowCount(rowCount: Int): String {
        return when (rowCount) {
            3 -> KEY_ALPHABET_LAYOUT_3
            4 -> KEY_ALPHABET_LAYOUT_4
            5 -> KEY_ALPHABET_LAYOUT_5
            else -> KEY_ALPHABET_LAYOUT_3
        }
    }

    fun saveAlphabetLayoutForRowCount(
        context: Context,
        rowCount: Int,
        config: KeyboardConfig
    ) {
        val key = alphabetLayoutKeyForRowCount(rowCount)
        saveLayoutWithKey(context, key, config)
    }

    fun loadAlphabetLayoutForRowCount(
        context: Context,
        rowCount: Int
    ): KeyboardConfig {
        val key = alphabetLayoutKeyForRowCount(rowCount)
        val fallback = defaultAlphabetLayoutForRowCount(rowCount)
        return loadLayoutWithKey(context, key, fallback)
    }

    fun clearAlphabetLayoutForRowCount(context: Context, rowCount: Int) {
        val key = alphabetLayoutKeyForRowCount(rowCount)
        prefs(context).edit().remove(key).apply()
    }

    // STARI KLJUČEVI - zadržani za kompatibilnost, ali se ne koriste za nove save-ove
    @Deprecated("Koristi saveAlphabetLayoutForRowCount")
    fun saveLayout(context: Context, layout: KeyboardConfig) {
        val rowCount = getRowCount(context)
        saveAlphabetLayoutForRowCount(context, rowCount, layout)
    }

    @Deprecated("Koristi loadAlphabetLayoutForRowCount")
    fun loadLayout(context: Context): KeyboardConfig {
        val rowCount = getRowCount(context)
        return loadAlphabetLayoutForRowCount(context, rowCount)
    }

    @Deprecated("Koristi clearAlphabetLayoutForRowCount")
    fun clearLayout(context: Context) {
        val rowCount = getRowCount(context)
        clearAlphabetLayoutForRowCount(context, rowCount)
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

    private fun numericLayoutKeyForRowCount(rowCount: Int): String {
        return when (rowCount) {
            3 -> KEY_NUMERIC_LAYOUT_3
            4 -> KEY_NUMERIC_LAYOUT_4
            5 -> KEY_NUMERIC_LAYOUT_5
            else -> KEY_NUMERIC_LAYOUT_3
        }
    }

    fun saveNumericLayoutForRowCount(
        context: Context,
        rowCount: Int,
        config: KeyboardConfig
    ) {
        val key = numericLayoutKeyForRowCount(rowCount)
        saveLayoutWithKey(context, key, config)
    }

    fun loadNumericLayoutForRowCount(
        context: Context,
        rowCount: Int
    ): KeyboardConfig {
        val key = numericLayoutKeyForRowCount(rowCount)
        val fallback = defaultNumericLayoutForRowCount(rowCount)
        return loadLayoutWithKey(context, key, fallback)
    }

    fun clearNumericLayoutForRowCount(context: Context, rowCount: Int) {
        val key = numericLayoutKeyForRowCount(rowCount)
        prefs(context).edit().remove(key).apply()
    }

    // STARI KLJUČEVI - zadržani za kompatibilnost
    @Deprecated("Koristi saveNumericLayoutForRowCount")
    fun saveNumericLayout(context: Context, config: KeyboardConfig) {
        val rowCount = getRowCount(context)
        saveNumericLayoutForRowCount(context, rowCount, config)
    }

    @Deprecated("Koristi loadNumericLayoutForRowCount")
    fun loadNumericLayout(context: Context): KeyboardConfig {
        val rowCount = getRowCount(context)
        return loadNumericLayoutForRowCount(context, rowCount)
    }

    @Deprecated("Koristi clearNumericLayoutForRowCount")
    fun clearNumericLayout(context: Context) {
        val rowCount = getRowCount(context)
        clearNumericLayoutForRowCount(context, rowCount)
    }

    /* ───────── HORIZONTAL CENTER LAYOUT ───────── */

    private fun horizontalCenterLayoutKeyForRowCount(rowCount: Int): String {
        return when (rowCount) {
            3 -> KEY_HORIZONTAL_CENTER_LAYOUT_3
            4 -> KEY_HORIZONTAL_CENTER_LAYOUT_4
            5 -> KEY_HORIZONTAL_CENTER_LAYOUT_5
            else -> KEY_HORIZONTAL_CENTER_LAYOUT_3
        }
    }

    fun saveHorizontalCenterLayoutForRowCount(
        context: Context,
        rowCount: Int,
        config: KeyboardConfig
    ) {
        val key = horizontalCenterLayoutKeyForRowCount(rowCount)
        saveLayoutWithKey(context, key, config)
    }

    fun loadHorizontalCenterLayoutForRowCount(
        context: Context,
        rowCount: Int
    ): KeyboardConfig {
        val key = horizontalCenterLayoutKeyForRowCount(rowCount)
        return loadLayoutWithKey(context, key, defaultHorizontalCenterLayout)
    }

    fun clearHorizontalCenterLayoutForRowCount(context: Context, rowCount: Int) {
        val key = horizontalCenterLayoutKeyForRowCount(rowCount)
        prefs(context).edit().remove(key).apply()
    }

    // STARI KLJUČEVI
    @Deprecated("Koristi saveHorizontalCenterLayoutForRowCount")
    fun saveHorizontalCenterLayout(context: Context, config: KeyboardConfig) {
        val rowCount = getRowCount(context)
        saveHorizontalCenterLayoutForRowCount(context, rowCount, config)
    }

    @Deprecated("Koristi loadHorizontalCenterLayoutForRowCount")
    fun loadHorizontalCenterLayout(context: Context): KeyboardConfig {
        val rowCount = getRowCount(context)
        return loadHorizontalCenterLayoutForRowCount(context, rowCount)
    }

    @Deprecated("Koristi clearHorizontalCenterLayoutForRowCount")
    fun clearHorizontalCenterLayout(context: Context) {
        val rowCount = getRowCount(context)
        clearHorizontalCenterLayoutForRowCount(context, rowCount)
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
    /* ───────── SIDE BUTTONS COLORS ───────── */
    private const val SIDE_BUTTONS_USE_THEME_BG = "side_buttons_use_theme_bg"
    private const val SIDE_BUTTONS_BG = "side_buttons_bg"
    private const val SIDE_BUTTONS_TEXT_COLOR = "side_buttons_text_color"

    fun getSideButtonsUseThemeBg(context: Context): Boolean =
        prefs(context).getBoolean(SIDE_BUTTONS_USE_THEME_BG, true)

    fun getSideButtonsBg(context: Context): Int =
        prefs(context).getInt(SIDE_BUTTONS_BG, 0xFF3E3E3E.toInt())

    fun getSideButtonsTextColor(context: Context): Int =
        prefs(context).getInt(SIDE_BUTTONS_TEXT_COLOR, 0xFFFFFFFF.toInt())

    fun setSideButtonsColors(context: Context, bg: Int, textColor: Int, useThemeBg: Boolean) {
        prefs(context).edit()
            .putInt(SIDE_BUTTONS_BG, bg)
            .putInt(SIDE_BUTTONS_TEXT_COLOR, textColor)
            .putBoolean(SIDE_BUTTONS_USE_THEME_BG, useThemeBg)
            .apply()
    }

    /* ───────── KEYS COLORS ───────── */
    private const val KEYS_ALL_SAME_COLOR = "keys_all_same_color"
    private const val KEYS_BG = "keys_bg"
    private const val KEYS_TEXT_COLOR = "keys_text_color"

    fun getKeysAllSameColor(context: Context): Boolean =
        prefs(context).getBoolean(KEYS_ALL_SAME_COLOR, true)

    fun getKeysBg(context: Context): Int =
        prefs(context).getInt(KEYS_BG, 0xFF3E3E3E.toInt())

    fun getKeysTextColor(context: Context): Int =
        prefs(context).getInt(KEYS_TEXT_COLOR, 0xFFFFFFFF.toInt())

    fun setKeysColors(context: Context, bg: Int, textColor: Int, allSame: Boolean) {
        prefs(context).edit()
            .putInt(KEYS_BG, bg)
            .putInt(KEYS_TEXT_COLOR, textColor)
            .putBoolean(KEYS_ALL_SAME_COLOR, allSame)
            .apply()
    }

    /* ───────── BACKGROUND COLOR ───────── */
    private const val BACKGROUND_USE_THEME = "background_use_theme"
    private const val BACKGROUND_COLOR = "background_color"
    /* ───────── INDIVIDUAL KEY COLORS ───────── */
    private const val KEY_INDIVIDUAL_COLORS_PREFIX = "key_individual_"

    fun getKeyIndividualColors(context: Context, keyLabel: String): Pair<Int, Int>? {
        val bg = prefs(context).getInt("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_bg", 0)
        val text = prefs(context).getInt("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_text", 0)
        return if (bg != 0 && text != 0) Pair(bg, text) else null
    }

    fun getKeyIndividualBg(context: Context, keyLabel: String): Int? {
        val bg = prefs(context).getInt("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_bg", 0)
        return if (bg != 0) bg else null
    }

    fun getKeyIndividualTextColor(context: Context, keyLabel: String): Int? {
        val text = prefs(context).getInt("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_text", 0)
        return if (text != 0) text else null
    }

    fun setKeyIndividualColors(context: Context, keyLabel: String, bg: Int, textColor: Int) {
        prefs(context).edit()
            .putInt("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_bg", bg)
            .putInt("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_text", textColor)
            .apply()
    }

    fun clearKeyIndividualColors(context: Context, keyLabel: String) {
        prefs(context).edit()
            .remove("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_bg")
            .remove("${KEY_INDIVIDUAL_COLORS_PREFIX}${keyLabel}_text")
            .apply()
    }

    fun getBackgroundUseTheme(context: Context): Boolean =
        prefs(context).getBoolean(BACKGROUND_USE_THEME, true)

    fun getBackgroundColor(context: Context): Int =
        prefs(context).getInt(BACKGROUND_COLOR, 0xFF1A1A1A.toInt())

    fun setBackgroundColor(context: Context, color: Int, useTheme: Boolean) {
        prefs(context).edit()
            .putInt(BACKGROUND_COLOR, color)
            .putBoolean(BACKGROUND_USE_THEME, useTheme)
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

    /* ───────── PROSLJEĐIVANJE BINDOVA ───────── */
    // NOVO: Kopiraj bindove iz jednog layouta u drugi kad mijenjaš broj redova

    /**
     * Kopira long press bindove iz izvornog layouta u ciljni layout.
     * Mapira bindove prema labelu tipke.
     */
    /* ───────── GLOBAL BINDS INTEGRATION ───────── */

    /**
     * Učitaj layout i automatski apliciraj globalne bindove.
     * Ovo se koristi za alphabet layout.
     */
    fun loadAlphabetLayoutWithGlobalBinds(context: Context, rowCount: Int): KeyboardConfig {
        val baseLayout = loadAlphabetLayoutForRowCount(context, rowCount)
        return GlobalLongPressStorage.applyAlphabetBindsToLayout(context, baseLayout)
    }

    /**
     * Učitaj layout i automatski apliciraj globalne bindove.
     * Ovo se koristi za numeric layout.
     */
    fun loadNumericLayoutWithGlobalBinds(context: Context, rowCount: Int): KeyboardConfig {
        val baseLayout = loadNumericLayoutForRowCount(context, rowCount)
        return GlobalLongPressStorage.applyNumericBindsToLayout(context, baseLayout)
    }

    /**
     * Spremi layout i izvuci bindove u globalni storage.
     * Ovo se koristi za alphabet layout.
     */
    fun saveAlphabetLayoutWithGlobalBinds(context: Context, rowCount: Int, layout: KeyboardConfig) {
        // Prvo spremi normalno
        saveAlphabetLayoutForRowCount(context, rowCount, layout)
        // Onda izvuci i spremi globalne bindove
        GlobalLongPressStorage.extractAndSaveAlphabetBinds(context, layout)
    }

    /**
     * Spremi layout i izvuci bindove u globalni storage.
     * Ovo se koristi za numeric layout.
     */
    fun saveNumericLayoutWithGlobalBinds(context: Context, rowCount: Int, layout: KeyboardConfig) {
        saveNumericLayoutForRowCount(context, rowCount, layout)
        GlobalLongPressStorage.extractAndSaveNumericBinds(context, layout)
    }
}