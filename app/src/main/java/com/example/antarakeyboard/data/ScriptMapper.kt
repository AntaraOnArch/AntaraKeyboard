package com.example.antarakeyboard.data

import java.util.Locale

/**
 * Maps Latin key labels to the selected Cyrillic or Greek script. The letter layout stays Latin;
 * each key's label and typed text go through [map]. q/w/x/y carry script-specific letters
 * that have no Latin counterpart.
 */
object ScriptMapper {

    private val serbianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "ј",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        "q" to "љ",
        "w" to "њ",
        "x" to "џ",
        "y" to "ј"
    )

    private val bulgarianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za bugarska slova bez čistog latin para
        "q" to "я",
        "w" to "ш",
        "x" to "х",
        "y" to "ъ"
    )

    private val ukrainianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "і",
        "j" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za ukrajinska slova bez čistog latin para
        "q" to "я",
        "w" to "ш",
        "x" to "ь",
        "y" to "и"
    )

    private val macedonianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "ј",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za makedonska slova bez čistog latin para
        "q" to "љ",
        "w" to "њ",
        "x" to "џ",
        "y" to "ѕ"
    )

    private val russianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za ruska slova bez čistog latin para
        "q" to "я",
        "w" to "ш",
        "x" to "ь",
        "y" to "ы"
    )

    /** Standard Greek layout: u → θ, w → ς, q → ; (the Greek question mark). */
    private val greekDirectMap = mapOf(
        "a" to "α",
        "b" to "β",
        "c" to "ψ",
        "d" to "δ",
        "e" to "ε",
        "f" to "φ",
        "g" to "γ",
        "h" to "η",
        "i" to "ι",
        "j" to "ξ",
        "k" to "κ",
        "l" to "λ",
        "m" to "μ",
        "n" to "ν",
        "o" to "ο",
        "p" to "π",
        "r" to "ρ",
        "s" to "σ",
        "t" to "τ",
        "u" to "θ",
        "v" to "ω",
        "x" to "χ",
        "y" to "υ",
        "z" to "ζ",

        "q" to ";",
        "w" to "ς"
    )

    private fun mapForPreset(presetId: String): Map<String, String>? = when (presetId) {
        LongPressPresets.PRESET_SERBIAN_CYRILLIC -> serbianCyrillicDirectMap
        LongPressPresets.PRESET_BULGARIAN_CYRILLIC -> bulgarianCyrillicDirectMap
        LongPressPresets.PRESET_RUSSIAN_CYRILLIC -> russianCyrillicDirectMap
        LongPressPresets.PRESET_UKRAINIAN_CYRILLIC -> ukrainianCyrillicDirectMap
        LongPressPresets.PRESET_MACEDONIAN_CYRILLIC -> macedonianCyrillicDirectMap
        LongPressPresets.PRESET_GREEK -> greekDirectMap
        else -> null
    }

    /** Returns [text] in the script of [presetId]; anything that isn't a single mapped letter is unchanged. */
    fun map(presetId: String, text: String): String {
        if (text.length != 1) return text

        val map = mapForPreset(presetId) ?: return text
        val mapped = map[text.lowercase(Locale.ROOT)] ?: return text

        val isUpper = text == text.uppercase(Locale.ROOT) && text != text.lowercase(Locale.ROOT)
        return if (isUpper) mapped.uppercase(Locale.ROOT) else mapped
    }
}
