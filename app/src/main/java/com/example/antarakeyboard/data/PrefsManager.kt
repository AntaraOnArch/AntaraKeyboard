package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Theme preferences shared by the main app and the keyboard service (`theme_prefs`).
 */
object PrefsManager {

    private const val PREFS_THEME = "theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_USE_CUSTOM_THEME = "use_custom_theme"

    @Volatile private var themePrefs: SharedPreferences? = null

    fun theme(context: Context): SharedPreferences {
        return themePrefs ?: synchronized(this) {
            themePrefs ?: context.applicationContext
                .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .also { themePrefs = it }
        }
    }

    /** App-level Light/Dark choice (not the OS theme). Defaults to dark. */
    fun isDarkMode(context: Context): Boolean =
        theme(context).getBoolean(KEY_DARK_MODE, true)

    fun setDarkMode(context: Context, isDark: Boolean) {
        theme(context).edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }

    /** True while a saved layout ("Custom" theme) is the active theme. */
    fun isCustomTheme(context: Context): Boolean =
        theme(context).getBoolean(KEY_USE_CUSTOM_THEME, false)

    fun setCustomTheme(context: Context, enabled: Boolean) {
        theme(context).edit().putBoolean(KEY_USE_CUSTOM_THEME, enabled).apply()
    }
}
