package com.example.antarakeyboard.data

import java.util.Locale

/**
 * Languages the app is translated into (one `res/values-*` folder each, English is the default).
 * Tags are BCP 47, as used by per-app language settings.
 */
object AppLanguages {

    val TAGS = listOf(
        "en", "bg", "be", "bs", "ca", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga",
        "hr", "hu", "is", "it", "lt", "lv", "mk", "mt", "nb", "nl", "pl", "pt", "ro", "ru",
        "sk", "sl", "sq", "sr", "sr-Latn", "sv", "uk"
    )

    /**
     * Keyboard script for a language: Cyrillic languages type their Cyrillic (Belarusian uses the
     * closest one, Russian), Greek types Greek; Serbian Latin and every other language type Latin.
     */
    fun scriptPresetFor(languageTag: String): String {
        val locale = Locale.forLanguageTag(languageTag)
        return when (locale.language) {
            "sr" -> if (locale.script == "Latn") LongPressPresets.PRESET_LATIN else LongPressPresets.PRESET_SERBIAN_CYRILLIC
            "mk" -> LongPressPresets.PRESET_MACEDONIAN_CYRILLIC
            "ru", "be" -> LongPressPresets.PRESET_RUSSIAN_CYRILLIC
            "uk" -> LongPressPresets.PRESET_UKRAINIAN_CYRILLIC
            "bg" -> LongPressPresets.PRESET_BULGARIAN_CYRILLIC
            "el" -> LongPressPresets.PRESET_GREEK
            else -> LongPressPresets.PRESET_LATIN
        }
    }

    /** Name of the language in that language itself ("Deutsch", "Русский", "srpski (latinica)"). */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Matches a stored/system tag ("de-DE", "sr-Latn-RS") to one of [TAGS], or null. */
    fun match(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val locale = Locale.forLanguageTag(tag)
        val withScript = if (locale.script.isNotEmpty()) "${locale.language}-${locale.script}" else null
        return TAGS.firstOrNull { it == withScript } ?: TAGS.firstOrNull { it == locale.language }
    }
}
