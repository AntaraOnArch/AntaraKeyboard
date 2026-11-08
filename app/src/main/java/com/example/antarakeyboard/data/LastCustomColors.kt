package com.example.antarakeyboard.data

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import com.example.antarakeyboard.R

/** "Last custom colors" (`custom_theme_prefs`): the colors kept before a reset, offered in Theme → Custom. */
object LastCustomColors {

    fun exists(context: Context): Boolean {
        // Check if user has saved custom theme colors
        val prefs = context.getSharedPreferences(PrefsManager.PREFS_CUSTOM_THEME, Context.MODE_PRIVATE)
        return prefs.getBoolean("has_custom_theme", false)
    }

    /** Stores the colors drawn now (theme or custom), so restoring looks the same. */
    fun save(context: Context) {
        val customPrefs = context.getSharedPreferences(PrefsManager.PREFS_CUSTOM_THEME, Context.MODE_PRIVATE)

        // Save current keyboard colors as custom theme
        // Colors as drawn (theme or custom), so "Last custom colors" restores what was visible
        val isDark = PrefsManager.isDarkMode(context)
        val (keyFill, keyText) = KeyboardPrefs.resolveKeyColors(context, isDark)
        val (space1, space2) = KeyboardPrefs.resolveSpaceColors(context, isDark)
        customPrefs.edit {
            putBoolean("has_custom_theme", true)
            putInt("custom_key_fill", keyFill)
            putInt("custom_key_text", keyText)
            putInt("custom_background", KeyboardPrefs.resolveKeyboardBackground(context, isDark))
            putInt("custom_space1_bg", space1)
            putInt("custom_space2_bg", space2)
            putInt("custom_enter_bg", KeyboardPrefs.getEnterBg(context))
            putInt("custom_enter_icon", KeyboardPrefs.getEnterIcon(context))
            putInt("custom_side_bg", KeyboardPrefs.getSideButtonsBg(context))
            putInt("custom_side_text", KeyboardPrefs.getSideButtonsTextColor(context))
        }
    }

    /** Applies the stored colors as custom colors (nothing happens if none are stored). */
    fun apply(context: Context) {
        val customPrefs = context.getSharedPreferences(PrefsManager.PREFS_CUSTOM_THEME, Context.MODE_PRIVATE)

        if (!customPrefs.getBoolean("has_custom_theme", false)) return

        // Load and apply custom theme colors
        val keyFill = customPrefs.getInt("custom_key_fill", context.getColor(R.color.key_fill_light))
        val keyText = customPrefs.getInt("custom_key_text", Color.BLACK)
        val background = customPrefs.getInt("custom_background", context.getColor(R.color.keyboard_bg_light))
        val space1Bg = customPrefs.getInt("custom_space1_bg", keyFill)
        val space2Bg = customPrefs.getInt("custom_space2_bg", keyFill)
        val enterBg = customPrefs.getInt("custom_enter_bg", context.getColor(R.color.special_fill))
        val enterIcon = customPrefs.getInt("custom_enter_icon", Color.WHITE)
        val sideBg = customPrefs.getInt("custom_side_bg", Color.TRANSPARENT)
        val sideText = customPrefs.getInt("custom_side_text", Color.BLACK)

        KeyboardPrefs.setKeysColors(context, keyFill, keyText, true, useTheme = false)
        KeyboardPrefs.setBackgroundColor(context, background, false)
        KeyboardPrefs.setSpaceColors(context, space1Bg, space2Bg, space1Bg == space2Bg)
        KeyboardPrefs.setEnterColors(context, enterBg, enterIcon)
        KeyboardPrefs.setSideButtonsColors(context, sideBg, sideText, false)
    }
}
