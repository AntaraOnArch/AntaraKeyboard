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
