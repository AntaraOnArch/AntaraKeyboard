package com.example.antarakeyboard.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList

/**
 * The app language – one setting shared by the main app (Language dropdown), the keyboard
 * (dual-space language picker) and Android 13+ system settings. The keyboard script, default
 * long-press letters and suggestion dictionary all follow it.
 */
object AppLanguageSettings {

    /** Chosen app language tag, "" = follow the device. */
    fun selectedTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: the system setting is the truth (it can also be changed in Settings)
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return ""
            val tag = locales[0].toLanguageTag()
            return AppLanguages.match(tag) ?: tag
        }
        return PrefsManager.getAppLanguage(context)
    }

    /** Device language (not affected by the app language). */
    fun deviceTag(): String = Resources.getSystem().configuration.locales[0]?.toLanguageTag() ?: "en"

    /** Language actually in use: the chosen one, or the device language. */
    fun effectiveTag(context: Context): String = selectedTag(context).ifEmpty { deviceTag() }

    /**
     * Sets the app language from anywhere (also from the keyboard service) and switches the
     * keyboard script to match. The main app additionally applies it through AppCompat.
     */
    fun select(context: Context, tag: String) {
        PrefsManager.setAppLanguage(context, tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
        KeyboardPrefs.applyLongPressPreset(context, AppLanguages.scriptPresetFor(tag.ifEmpty { deviceTag() }))
    }
}
