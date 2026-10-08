package com.example.antarakeyboard.service.suggest

import com.example.antarakeyboard.data.LongPressPresets

/** Which bundled dictionary (assets/dictionaries/<name>.txt) the suggestions use. */
object SuggestionDictionaries {

    /**
     * Dictionary for the keyboard script and the app/device language.
     * Cyrillic scripts type Cyrillic text, so they pick the matching Cyrillic dictionary
     * (none bundled for Bulgarian, Ukrainian and Macedonian → no suggestions).
     * The Latin keyboard follows the language; unsupported languages fall back to English.
     */
    fun forKeyboard(scriptPreset: String, languageTag: String): String? = when (scriptPreset) {
        LongPressPresets.PRESET_SERBIAN_CYRILLIC -> "sr_cyrl"
        LongPressPresets.PRESET_RUSSIAN_CYRILLIC -> "ru"
        LongPressPresets.PRESET_GREEK -> "el"
        LongPressPresets.PRESET_BULGARIAN_CYRILLIC,
        LongPressPresets.PRESET_UKRAINIAN_CYRILLIC,
        LongPressPresets.PRESET_MACEDONIAN_CYRILLIC -> null
        else -> when (languageTag.substringBefore('-').substringBefore('_').lowercase()) {
            "hr" -> "hr"
            "sr" -> "sr_latn"
            "bs" -> "bs"
            "de" -> "de"
            else -> "en"
        }
    }
}
