package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized SharedPreferences manager.
 * Provides cached access to all preference files used in the app.
 *
 * Usage:
 *   val prefs = PrefsManager.keyboard(context)
 *   prefs.getString("key", "default")
 */
object PrefsManager {

    private const val PREFS_KEYBOARD = "keyboard_prefs"
    private const val PREFS_THEME = "theme_prefs"
    private const val PREFS_EMOJI = "emoji_picker_prefs"
    private const val PREFS_EDGE_SLOTS = "edge_slots"
    private const val PREFS_EDGE_KEYS = "edge_key_prefs"
    private const val PREFS_GLOBAL_LONG_PRESS = "global_long_press_bindings"

    // Cached preferences instances
    @Volatile private var keyboardPrefs: SharedPreferences? = null
    @Volatile private var themePrefs: SharedPreferences? = null
    @Volatile private var emojiPrefs: SharedPreferences? = null
    @Volatile private var edgeSlotsPrefs: SharedPreferences? = null
    @Volatile private var edgeKeysPrefs: SharedPreferences? = null
    @Volatile private var globalLongPressPrefs: SharedPreferences? = null

    /**
     * Main keyboard preferences (layouts, shapes, colors)
     */
    fun keyboard(context: Context): SharedPreferences {
        return keyboardPrefs ?: synchronized(this) {
            keyboardPrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_KEYBOARD, Context.MODE_PRIVATE)
                .also { keyboardPrefs = it }
        }
    }

    /**
     * Theme preferences (dark/light mode)
     */
    fun theme(context: Context): SharedPreferences {
        return themePrefs ?: synchronized(this) {
            themePrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .also { themePrefs = it }
        }
    }

    /**
     * Emoji picker preferences (recent emojis, button order)
     */
    fun emoji(context: Context): SharedPreferences {
        return emojiPrefs ?: synchronized(this) {
            emojiPrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_EMOJI, Context.MODE_PRIVATE)
                .also { emojiPrefs = it }
        }
    }

    /**
     * Edge slots preferences (side button configuration)
     */
    fun edgeSlots(context: Context): SharedPreferences {
        return edgeSlotsPrefs ?: synchronized(this) {
            edgeSlotsPrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_EDGE_SLOTS, Context.MODE_PRIVATE)
                .also { edgeSlotsPrefs = it }
        }
    }

    /**
     * Edge keys preferences
     */
    fun edgeKeys(context: Context): SharedPreferences {
        return edgeKeysPrefs ?: synchronized(this) {
            edgeKeysPrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_EDGE_KEYS, Context.MODE_PRIVATE)
                .also { edgeKeysPrefs = it }
        }
    }

    /**
     * Global long press bindings
     */
    fun globalLongPress(context: Context): SharedPreferences {
        return globalLongPressPrefs ?: synchronized(this) {
            globalLongPressPrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_GLOBAL_LONG_PRESS, Context.MODE_PRIVATE)
                .also { globalLongPressPrefs = it }
        }
    }

    /**
     * Check if dark mode is enabled
     */
    fun isDarkMode(context: Context): Boolean {
        return theme(context).getBoolean("dark_mode", true)
    }

    /**
     * Set dark mode
     */
    fun setDarkMode(context: Context, isDark: Boolean) {
        theme(context).edit().putBoolean("dark_mode", isDark).apply()
    }
}
