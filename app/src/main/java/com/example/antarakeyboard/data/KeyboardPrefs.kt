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

    const val PREFS_NAME = "keyboard_prefs"

    private const val KEY_SHAPE = "key_shape"
    private const val LONG_PRESS_DEFAULTS_VERSION = 4
    private const val KEY_LONG_PRESS_DEFAULTS_VERSION = "long_press_defaults_version"

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


    private const val SPACE_LINKED = "space_linked"
    private const val SPACE1_BG = "space1_bg"
    private const val SPACE2_BG = "space2_bg"

    private const val ENTER_BG = "enter_bg"
    private const val ENTER_ICON = "enter_icon"

    private const val KEY_ROW_COUNT = "row_count"

    private const val KEY_SELECTED_LONG_PRESS_PRESET = "selected_long_press_preset"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ───────── KEY SIZE ───────── */

    private const val KEY_SCALE = "key_scale"

    /** Key size factor from the main app slider (1.0 = default size). */
    fun getKeyScale(context: Context): Float =
        prefs(context).getFloat(KEY_SCALE, 1f)

    fun setKeyScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    /* ───────── ROW SPACING ───────── */

    private const val KEY_ROW_SPACING_DP = "row_spacing_dp"

    /** Extra vertical space between rows in dp from the main app slider (0 = default). */
    fun getRowSpacingDp(context: Context): Int =
        prefs(context).getInt(KEY_ROW_SPACING_DP, 0)

    fun setRowSpacingDp(context: Context, dp: Int) {
        prefs(context).edit().putInt(KEY_ROW_SPACING_DP, dp).apply()
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
    private const val KEYS_USE_THEME = "keys_use_theme"
    private const val KEYS_BG = "keys_bg"
    private const val KEYS_TEXT_COLOR = "keys_text_color"

    fun getKeysAllSameColor(context: Context): Boolean =
        prefs(context).getBoolean(KEYS_ALL_SAME_COLOR, true)

    fun getKeysUseTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEYS_USE_THEME, true)

    fun getKeysBg(context: Context): Int =
        prefs(context).getInt(KEYS_BG, 0xFF3E3E3E.toInt())

    fun getKeysTextColor(context: Context): Int =
        prefs(context).getInt(KEYS_TEXT_COLOR, 0xFFFFFFFF.toInt())

    fun setKeysColors(context: Context, bg: Int, textColor: Int, allSame: Boolean, useTheme: Boolean = false) {
        prefs(context).edit()
            .putInt(KEYS_BG, bg)
            .putInt(KEYS_TEXT_COLOR, textColor)
            .putBoolean(KEYS_ALL_SAME_COLOR, allSame)
            .putBoolean(KEYS_USE_THEME, useTheme)
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
            .putString(BACKGROUND_MODE, (if (useTheme) BackgroundMode.THEME else BackgroundMode.CUSTOM).name)
            .apply()
    }

    /** Keyboard background source chosen in Colors → Background. */
    enum class BackgroundMode { THEME, DARK, LIGHT, CUSTOM, TRANSPARENT }

    private const val BACKGROUND_MODE = "background_mode"

    fun getBackgroundMode(context: Context): BackgroundMode {
        val stored = prefs(context).getString(BACKGROUND_MODE, null)
            ?.let { name -> runCatching { BackgroundMode.valueOf(name) }.getOrNull() }
        // Older installs only have the use-theme flag
        return stored ?: if (getBackgroundUseTheme(context)) BackgroundMode.THEME else BackgroundMode.CUSTOM
    }

    fun setBackgroundMode(context: Context, mode: BackgroundMode) {
        prefs(context).edit()
            .putString(BACKGROUND_MODE, mode.name)
            .putBoolean(BACKGROUND_USE_THEME, mode == BackgroundMode.THEME)
            .apply()
    }

    /** The color the keyboard background is drawn with (transparent for [BackgroundMode.TRANSPARENT]). */
    fun resolveKeyboardBackground(context: Context, isDark: Boolean): Int =
        when (getBackgroundMode(context)) {
            BackgroundMode.THEME -> getThemeDefaultsForMode(context, isDark).keyboardBg
            BackgroundMode.DARK -> getThemeDarkKeyboardBg(context)
            BackgroundMode.LIGHT -> getThemeLightKeyboardBg(context)
            BackgroundMode.CUSTOM -> getBackgroundColor(context)
            BackgroundMode.TRANSPARENT -> 0x00000000
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

    fun getSelectedLongPressPreset(context: Context): String {
        return prefs(context).getString(
            KEY_SELECTED_LONG_PRESS_PRESET,
            LongPressPresets.PRESET_SYSTEM
        ) ?: LongPressPresets.PRESET_SYSTEM
    }

    fun applyLongPressPreset(context: Context, presetId: String) {
        val defaults = LongPressPresets.getById(context, presetId)

        GlobalLongPressStorage.saveAlphabetBinds(context, defaults)

        prefs(context).edit()
            .putString(KEY_SELECTED_LONG_PRESS_PRESET, presetId)
            .putInt(KEY_LONG_PRESS_DEFAULTS_VERSION, LONG_PRESS_DEFAULTS_VERSION)
            .remove("long_press_defaults_initialized")
            .apply()
    }

    // puni bindove automatski
    fun ensureDefaultLongPress(context: Context) {
        val sp = prefs(context)

        val currentVersion = sp.getInt(KEY_LONG_PRESS_DEFAULTS_VERSION, 0)
        if (currentVersion >= LONG_PRESS_DEFAULTS_VERSION) return

        val selectedPreset = getSelectedLongPressPreset(context)

        val defaults = if (selectedPreset == LongPressPresets.PRESET_SYSTEM) {
            LongPressPresets.getForSystemLanguage(context)
        } else {
            LongPressPresets.getById(context, selectedPreset)
        }

        defaults.forEach { (key, values) ->
            val existing = GlobalLongPressStorage.getAlphabetBind(context, key)

            if (existing.isEmpty()) {
                GlobalLongPressStorage.saveAlphabetBind(context, key, values)
            }
        }

        sp.edit()
            .putInt(KEY_LONG_PRESS_DEFAULTS_VERSION, LONG_PRESS_DEFAULTS_VERSION)
            .remove("long_press_defaults_initialized")
            .apply()
    }

    /* ───────── WORD SUGGESTIONS ───────── */

    private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"

    /** Word suggestion strip above the keyboard (off by default). */
    fun isSuggestionsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUGGESTIONS_ENABLED, false)

    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUGGESTIONS_ENABLED, enabled).apply()
    }

    /* ───────── VIBRATION ───────── */

    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"

    fun isVibrationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    /* ───────── THEME DEFAULTS ───────── */
    // Custom default colors for light and dark mode themes

    private const val THEME_DEFAULTS_CUSTOM = "theme_defaults_custom"

    // Light mode defaults
    private const val THEME_LIGHT_KEY_FILL = "theme_light_key_fill"
    private const val THEME_LIGHT_KEY_TEXT = "theme_light_key_text"
    private const val THEME_LIGHT_SPACE_FILL = "theme_light_space_fill"
    private const val THEME_LIGHT_KEYBOARD_BG = "theme_light_keyboard_bg"

    // Dark mode defaults
    private const val THEME_DARK_KEY_FILL = "theme_dark_key_fill"
    private const val THEME_DARK_KEY_TEXT = "theme_dark_key_text"
    private const val THEME_DARK_SPACE_FILL = "theme_dark_space_fill"
    private const val THEME_DARK_KEYBOARD_BG = "theme_dark_keyboard_bg"

    // Factory defaults
    object FactoryDefaults {
        // Light mode
        const val LIGHT_KEY_FILL = 0xFFF0F0F0.toInt()
        const val LIGHT_KEY_TEXT = 0xFF000000.toInt()
        const val LIGHT_SPACE_FILL = 0xFFD3CAC8.toInt()
        const val LIGHT_KEYBOARD_BG = 0xFFFFFFFF.toInt()

        // Dark mode
        const val DARK_KEY_FILL = 0xFF3E3E3E.toInt()
        const val DARK_KEY_TEXT = 0xFFFFFFFF.toInt()
        const val DARK_SPACE_FILL = 0xFFF5E7E4.toInt()
        const val DARK_KEYBOARD_BG = 0xFF000000.toInt()
    }

    fun hasCustomThemeDefaults(context: Context): Boolean =
        prefs(context).getBoolean(THEME_DEFAULTS_CUSTOM, false)

    // Light mode getters
    fun getThemeLightKeyFill(context: Context): Int =
        prefs(context).getInt(THEME_LIGHT_KEY_FILL, FactoryDefaults.LIGHT_KEY_FILL)

    fun getThemeLightKeyText(context: Context): Int =
        prefs(context).getInt(THEME_LIGHT_KEY_TEXT, FactoryDefaults.LIGHT_KEY_TEXT)

    fun getThemeLightSpaceFill(context: Context): Int =
        prefs(context).getInt(THEME_LIGHT_SPACE_FILL, FactoryDefaults.LIGHT_SPACE_FILL)

    fun getThemeLightKeyboardBg(context: Context): Int =
        prefs(context).getInt(THEME_LIGHT_KEYBOARD_BG, FactoryDefaults.LIGHT_KEYBOARD_BG)

    // Dark mode getters
    fun getThemeDarkKeyFill(context: Context): Int =
        prefs(context).getInt(THEME_DARK_KEY_FILL, FactoryDefaults.DARK_KEY_FILL)

    fun getThemeDarkKeyText(context: Context): Int =
        prefs(context).getInt(THEME_DARK_KEY_TEXT, FactoryDefaults.DARK_KEY_TEXT)

    fun getThemeDarkSpaceFill(context: Context): Int =
        prefs(context).getInt(THEME_DARK_SPACE_FILL, FactoryDefaults.DARK_SPACE_FILL)

    fun getThemeDarkKeyboardBg(context: Context): Int =
        prefs(context).getInt(THEME_DARK_KEYBOARD_BG, FactoryDefaults.DARK_KEYBOARD_BG)

    // Setters
    fun setThemeLightDefaults(context: Context, keyFill: Int, keyText: Int, spaceFill: Int, keyboardBg: Int) {
        prefs(context).edit()
            .putInt(THEME_LIGHT_KEY_FILL, keyFill)
            .putInt(THEME_LIGHT_KEY_TEXT, keyText)
            .putInt(THEME_LIGHT_SPACE_FILL, spaceFill)
            .putInt(THEME_LIGHT_KEYBOARD_BG, keyboardBg)
            .putBoolean(THEME_DEFAULTS_CUSTOM, true)
            .apply()
    }

    fun setThemeDarkDefaults(context: Context, keyFill: Int, keyText: Int, spaceFill: Int, keyboardBg: Int) {
        prefs(context).edit()
            .putInt(THEME_DARK_KEY_FILL, keyFill)
            .putInt(THEME_DARK_KEY_TEXT, keyText)
            .putInt(THEME_DARK_SPACE_FILL, spaceFill)
            .putInt(THEME_DARK_KEYBOARD_BG, keyboardBg)
            .putBoolean(THEME_DEFAULTS_CUSTOM, true)
            .apply()
    }

    fun resetThemeDefaultsToFactory(context: Context) {
        prefs(context).edit()
            .remove(THEME_LIGHT_KEY_FILL)
            .remove(THEME_LIGHT_KEY_TEXT)
            .remove(THEME_LIGHT_SPACE_FILL)
            .remove(THEME_LIGHT_KEYBOARD_BG)
            .remove(THEME_DARK_KEY_FILL)
            .remove(THEME_DARK_KEY_TEXT)
            .remove(THEME_DARK_SPACE_FILL)
            .remove(THEME_DARK_KEYBOARD_BG)
            .remove(THEME_DEFAULTS_CUSTOM)
            .apply()
    }

    // Convenience method to get colors for current mode
    fun getThemeDefaultsForMode(context: Context, isDark: Boolean): ThemeColors {
        return if (isDark) {
            ThemeColors(
                keyFill = getThemeDarkKeyFill(context),
                keyText = getThemeDarkKeyText(context),
                spaceFill = getThemeDarkSpaceFill(context),
                keyboardBg = getThemeDarkKeyboardBg(context)
            )
        } else {
            ThemeColors(
                keyFill = getThemeLightKeyFill(context),
                keyText = getThemeLightKeyText(context),
                spaceFill = getThemeLightSpaceFill(context),
                keyboardBg = getThemeLightKeyboardBg(context)
            )
        }
    }

    data class ThemeColors(
        val keyFill: Int,
        val keyText: Int,
        val spaceFill: Int,
        val keyboardBg: Int
    )

    /* ───────── RGB SMOOTH ───────── */

    private const val RGB_SMOOTH_ENABLED = "rgb_rainbow_enabled"  // Keep old key for compatibility
    private const val RGB_SMOOTH_SPEED = "rgb_rainbow_speed"
    private const val RGB_SMOOTH_SATURATION = "rgb_rainbow_saturation"
    private const val RGB_SMOOTH_BRIGHTNESS = "rgb_rainbow_brightness"

    // Speed is in milliseconds for full cycle (360 degrees of hue)
    const val RGB_SPEED_MIN = 2000      // 2 seconds - very fast
    const val RGB_SPEED_MAX = 60000     // 60 seconds - very slow
    const val RGB_SPEED_DEFAULT = 10000 // 10 seconds - medium

    fun isRgbSmoothEnabled(context: Context): Boolean =
        prefs(context).getBoolean(RGB_SMOOTH_ENABLED, false)

    fun setRgbSmoothEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(RGB_SMOOTH_ENABLED, enabled).apply()
    }

    fun getRgbSmoothSpeed(context: Context): Int =
        prefs(context).getInt(RGB_SMOOTH_SPEED, RGB_SPEED_DEFAULT)

    fun setRgbSmoothSpeed(context: Context, speedMs: Int) {
        prefs(context).edit().putInt(RGB_SMOOTH_SPEED, speedMs.coerceIn(RGB_SPEED_MIN, RGB_SPEED_MAX)).apply()
    }

    fun getRgbSmoothSaturation(context: Context): Float =
        prefs(context).getFloat(RGB_SMOOTH_SATURATION, 0.7f)

    fun setRgbSmoothSaturation(context: Context, saturation: Float) {
        prefs(context).edit().putFloat(RGB_SMOOTH_SATURATION, saturation.coerceIn(0.1f, 1.0f)).apply()
    }

    fun getRgbSmoothBrightness(context: Context): Float =
        prefs(context).getFloat(RGB_SMOOTH_BRIGHTNESS, 0.3f)

    fun setRgbSmoothBrightness(context: Context, brightness: Float) {
        prefs(context).edit().putFloat(RGB_SMOOTH_BRIGHTNESS, brightness.coerceIn(0.1f, 1.0f)).apply()
    }

    /* ───────── RGB WILD ───────── */

    private const val RGB_WILD_ENABLED = "rgb_wild_enabled"
    private const val RGB_WILD_SPEED = "rgb_wild_speed"
    private const val RGB_WILD_SATURATION = "rgb_wild_saturation"
    private const val RGB_WILD_BRIGHTNESS = "rgb_wild_brightness"

    // Wild mode speed - much faster!
    const val RGB_WILD_SPEED_MIN = 50       // 50ms - insane
    const val RGB_WILD_SPEED_MAX = 1000     // 1 second - chill wild
    const val RGB_WILD_SPEED_DEFAULT = 200  // 200ms - aggressive

    fun isRgbWildEnabled(context: Context): Boolean =
        prefs(context).getBoolean(RGB_WILD_ENABLED, false)

    fun setRgbWildEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(RGB_WILD_ENABLED, enabled).apply()
    }

    fun getRgbWildSpeed(context: Context): Int =
        prefs(context).getInt(RGB_WILD_SPEED, RGB_WILD_SPEED_DEFAULT)

    fun setRgbWildSpeed(context: Context, speedMs: Int) {
        prefs(context).edit().putInt(RGB_WILD_SPEED, speedMs.coerceIn(RGB_WILD_SPEED_MIN, RGB_WILD_SPEED_MAX)).apply()
    }

    fun getRgbWildSaturation(context: Context): Float =
        prefs(context).getFloat(RGB_WILD_SATURATION, 1.0f)

    fun setRgbWildSaturation(context: Context, saturation: Float) {
        prefs(context).edit().putFloat(RGB_WILD_SATURATION, saturation.coerceIn(0.3f, 1.0f)).apply()
    }

    fun getRgbWildBrightness(context: Context): Float =
        prefs(context).getFloat(RGB_WILD_BRIGHTNESS, 0.8f)

    fun setRgbWildBrightness(context: Context, brightness: Float) {
        prefs(context).edit().putFloat(RGB_WILD_BRIGHTNESS, brightness.coerceIn(0.3f, 1.0f)).apply()
    }

    // Helper to check if any RGB mode is active
    fun isAnyRgbModeEnabled(context: Context): Boolean =
        isRgbSmoothEnabled(context) || isRgbWildEnabled(context)

    // Disable all RGB modes
    fun disableAllRgbModes(context: Context) {
        prefs(context).edit()
            .putBoolean(RGB_SMOOTH_ENABLED, false)
            .putBoolean(RGB_WILD_ENABLED, false)
            .apply()
    }
}