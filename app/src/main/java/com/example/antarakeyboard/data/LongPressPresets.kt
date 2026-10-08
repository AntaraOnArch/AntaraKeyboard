package com.example.antarakeyboard.data

import java.util.Locale

/**
 * Default long-press letters, keyed by the (Latin) key label.
 *
 * - Latin keyboard: the special letters of the user's language on their base letter
 *   (Croatian c → č ć, German a → ä, …); languages without own letters get common accents.
 * - Greek keyboard: the accented vowels (ά έ ή ί ϊ ό ύ ϋ ώ) on their vowel key.
 * - Cyrillic keyboard: only the special letters of that script on the matching key
 *   (Serbian c → ч ћ, d → ђ џ, …). Plain letters are not bound: the key already types them
 *   ([ScriptMapper]).
 *
 * Uppercase keys get the uppercase letters. User-added bindings are kept separately by
 * [KeyboardPrefs.applyLongPressPreset] when the defaults change.
 */
object LongPressPresets {

    const val PRESET_SYSTEM = "system"
    const val PRESET_LATIN = "latin"
    const val PRESET_SERBIAN_CYRILLIC = "sr_cyrl"
    const val PRESET_BULGARIAN_CYRILLIC = "bg_cyrl"
    const val PRESET_RUSSIAN_CYRILLIC = "ru_cyrl"
    const val PRESET_UKRAINIAN_CYRILLIC = "uk_cyrl"
    const val PRESET_MACEDONIAN_CYRILLIC = "mk_cyrl"
    const val PRESET_GREEK = "el"

    /**
     * Defaults for a keyboard script preset. [languageTag] (app or device language) decides the
     * letters of the Latin keyboard (PRESET_LATIN and PRESET_SYSTEM both type Latin).
     */
    fun defaultsFor(presetId: String, languageTag: String): Map<String, List<String>> =
        withUppercase(
            when (presetId) {
                PRESET_SERBIAN_CYRILLIC -> SERBIAN_CYRILLIC
                PRESET_BULGARIAN_CYRILLIC -> BULGARIAN_CYRILLIC
                PRESET_RUSSIAN_CYRILLIC -> RUSSIAN_CYRILLIC
                PRESET_UKRAINIAN_CYRILLIC -> UKRAINIAN_CYRILLIC
                PRESET_MACEDONIAN_CYRILLIC -> MACEDONIAN_CYRILLIC
                PRESET_GREEK -> GREEK
                else -> latinFor(languageTag)
            }
        )

    /** Latin special letters of [languageTag]; common accents for languages without own letters. */
    fun latinFor(languageTag: String): Map<String, List<String>> {
        val language = languageTag.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        return LATIN_BY_LANGUAGE[language] ?: COMMON_LATIN
    }

    /** Every special Latin letter of any language (lower + upper case) – for the bind picker. */
    fun allLatinLetters(): List<String> =
        withUppercase(
            (LATIN_BY_LANGUAGE.values + COMMON_LATIN)
                .flatMap { it.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, lists) -> lists.flatten().distinct() }
        ).toSortedMap().values.flatten().distinct()

    /** Full alphabets of all supported Cyrillic scripts (lower + upper case) – for the bind picker. */
    fun allCyrillicLetters(): List<String> {
        val lower = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя" + "ђјљњћџ" + "ѓќѕ" + "ґєії" + "ў"
        return lower.map { it.toString() }.flatMap { listOf(it, it.uppercase(Locale.ROOT)) }.distinct()
    }

    /** Greek alphabet with tonos and dialytika (lower + upper case) – for the bind picker. */
    fun allGreekLetters(): List<String> {
        val lower = "αβγδεζηθικλμνξοπρσςτυφχψω" + "άέήίόύώϊϋΐΰ"
        // ς and ΐ ΰ have no single-letter uppercase
        return lower.map { it.toString() }
            .flatMap { if (it in setOf("ς", "ΐ", "ΰ")) listOf(it) else listOf(it, it.uppercase(Locale.ROOT)) }
            .distinct()
    }

    fun isCyrillicPreset(presetId: String): Boolean = presetId in setOf(
        PRESET_SERBIAN_CYRILLIC, PRESET_BULGARIAN_CYRILLIC, PRESET_RUSSIAN_CYRILLIC,
        PRESET_UKRAINIAN_CYRILLIC, PRESET_MACEDONIAN_CYRILLIC
    )

    /** Unicode script the keyboard types with [presetId]. */
    fun scriptOf(presetId: String): Character.UnicodeScript = when {
        isCyrillicPreset(presetId) -> Character.UnicodeScript.CYRILLIC
        presetId == PRESET_GREEK -> Character.UnicodeScript.GREEK
        else -> Character.UnicodeScript.LATIN
    }

    /**
     * Long-press entries shown for the active script: single letters of the other scripts
     * (Latin, Cyrillic, Greek) are hidden, so each keyboard offers only its own letters.
     * Custom text, emoji, digits and symbols are shown in all of them.
     */
    fun visibleFor(presetId: String, binds: List<String>): List<String> {
        val own = scriptOf(presetId)
        val scripts = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.GREEK)
        return binds.filterNot { bind ->
            if (bind.codePointCount(0, bind.length) != 1 || !Character.isLetter(bind.codePointAt(0))) return@filterNot false
            val script = Character.UnicodeScript.of(bind.codePointAt(0))
            script != own && script in scripts
        }
    }

    /**
     * New long-press binds after the defaults changed (script, language or app update):
     * [oldDefaults] are removed from [current], [newDefaults] come first, and everything the user
     * bound themselves (custom text, extra letters, emoji) stays after them in its order.
     */
    fun mergeDefaults(
        current: Map<String, List<String>>,
        oldDefaults: Map<String, Collection<String>>,
        newDefaults: Map<String, List<String>>
    ): Map<String, List<String>> =
        (current.keys + newDefaults.keys).associateWith { key ->
            val old = oldDefaults[key].orEmpty()
            val userBinds = current[key].orEmpty().filter { it !in old }
            (newDefaults[key].orEmpty() + userBinds).distinct()
        }.filterValues { it.isNotEmpty() }

    /** Same as [withUppercase] for sets (used for the old defaults of the migration). */
    internal fun withUppercaseSets(source: Map<String, Set<String>>): Map<String, Set<String>> {
        val result = mutableMapOf<String, Set<String>>()
        source.forEach { (key, values) ->
            result[key] = values
            result[key.uppercase(Locale.ROOT)] = values.map { it.uppercase(Locale.ROOT) }.toSet()
        }
        return result
    }

    private fun withUppercase(source: Map<String, List<String>>): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        source.forEach { (key, values) ->
            result[key] = values
            result[key.uppercase(Locale.ROOT)] = values.map { it.uppercase(Locale.ROOT) }
        }
        return result
    }

    /* ───────── LATIN, PER LANGUAGE ───────── */

    private val SOUTH_SLAVIC_LATIN = mapOf(
        "c" to listOf("č", "ć"), "d" to listOf("đ"), "s" to listOf("š"), "z" to listOf("ž")
    )

    private val LATIN_BY_LANGUAGE: Map<String, Map<String, List<String>>> = mapOf(
        "hr" to SOUTH_SLAVIC_LATIN,
        "bs" to SOUTH_SLAVIC_LATIN,
        "sr" to SOUTH_SLAVIC_LATIN,
        "sl" to mapOf("c" to listOf("č"), "s" to listOf("š"), "z" to listOf("ž")),
        "de" to mapOf("a" to listOf("ä"), "o" to listOf("ö"), "u" to listOf("ü"), "s" to listOf("ß")),
        "fr" to mapOf(
            "a" to listOf("à", "â", "æ"), "c" to listOf("ç"), "e" to listOf("é", "è", "ê", "ë"),
            "i" to listOf("î", "ï"), "o" to listOf("ô", "œ"), "u" to listOf("ù", "û", "ü"), "y" to listOf("ÿ")
        ),
        "es" to mapOf(
            "a" to listOf("á"), "e" to listOf("é"), "i" to listOf("í"), "n" to listOf("ñ"),
            "o" to listOf("ó"), "u" to listOf("ú", "ü")
        ),
        "it" to mapOf(
            "a" to listOf("à"), "e" to listOf("è", "é"), "i" to listOf("ì"), "o" to listOf("ò"), "u" to listOf("ù")
        ),
        "pt" to mapOf(
            "a" to listOf("á", "à", "â", "ã"), "c" to listOf("ç"), "e" to listOf("é", "ê"),
            "i" to listOf("í"), "o" to listOf("ó", "ô", "õ"), "u" to listOf("ú")
        ),
        "ca" to mapOf(
            "a" to listOf("à"), "c" to listOf("ç"), "e" to listOf("è", "é"), "i" to listOf("í", "ï"),
            "o" to listOf("ò", "ó"), "u" to listOf("ú", "ü")
        ),
        "pl" to mapOf(
            "a" to listOf("ą"), "c" to listOf("ć"), "e" to listOf("ę"), "l" to listOf("ł"),
            "n" to listOf("ń"), "o" to listOf("ó"), "s" to listOf("ś"), "z" to listOf("ż", "ź")
        ),
        "cs" to mapOf(
            "a" to listOf("á"), "c" to listOf("č"), "d" to listOf("ď"), "e" to listOf("é", "ě"),
            "i" to listOf("í"), "n" to listOf("ň"), "o" to listOf("ó"), "r" to listOf("ř"),
            "s" to listOf("š"), "t" to listOf("ť"), "u" to listOf("ú", "ů"), "y" to listOf("ý"), "z" to listOf("ž")
        ),
        "sk" to mapOf(
            "a" to listOf("á", "ä"), "c" to listOf("č"), "d" to listOf("ď"), "e" to listOf("é"),
            "i" to listOf("í"), "l" to listOf("ĺ", "ľ"), "n" to listOf("ň"), "o" to listOf("ó", "ô"),
            "r" to listOf("ŕ"), "s" to listOf("š"), "t" to listOf("ť"), "u" to listOf("ú"),
            "y" to listOf("ý"), "z" to listOf("ž")
        ),
        "hu" to mapOf(
            "a" to listOf("á"), "e" to listOf("é"), "i" to listOf("í"),
            "o" to listOf("ó", "ö", "ő"), "u" to listOf("ú", "ü", "ű")
        ),
        "ro" to mapOf("a" to listOf("ă", "â"), "i" to listOf("î"), "s" to listOf("ș"), "t" to listOf("ț")),
        "nl" to mapOf("e" to listOf("é", "ë"), "i" to listOf("ï"), "o" to listOf("ö")),
        "sv" to mapOf("a" to listOf("å", "ä"), "o" to listOf("ö")),
        "fi" to mapOf("a" to listOf("ä", "å"), "o" to listOf("ö")),
        "da" to mapOf("a" to listOf("å", "æ"), "o" to listOf("ø")),
        "nb" to mapOf("a" to listOf("å", "æ"), "o" to listOf("ø")),
        "no" to mapOf("a" to listOf("å", "æ"), "o" to listOf("ø")),
        "is" to mapOf(
            "a" to listOf("á", "æ"), "d" to listOf("ð"), "e" to listOf("é"), "i" to listOf("í"),
            "o" to listOf("ó", "ö"), "t" to listOf("þ"), "u" to listOf("ú"), "y" to listOf("ý")
        ),
        "et" to mapOf(
            "a" to listOf("ä"), "o" to listOf("õ", "ö"), "u" to listOf("ü"), "s" to listOf("š"), "z" to listOf("ž")
        ),
        "lv" to mapOf(
            "a" to listOf("ā"), "c" to listOf("č"), "e" to listOf("ē"), "g" to listOf("ģ"),
            "i" to listOf("ī"), "k" to listOf("ķ"), "l" to listOf("ļ"), "n" to listOf("ņ"),
            "s" to listOf("š"), "u" to listOf("ū"), "z" to listOf("ž")
        ),
        "lt" to mapOf(
            "a" to listOf("ą"), "c" to listOf("č"), "e" to listOf("ę", "ė"), "i" to listOf("į"),
            "s" to listOf("š"), "u" to listOf("ų", "ū"), "z" to listOf("ž")
        ),
        "sq" to mapOf("c" to listOf("ç"), "e" to listOf("ë")),
        "mt" to mapOf(
            "a" to listOf("à"), "c" to listOf("ċ"), "e" to listOf("è"), "g" to listOf("ġ"),
            "h" to listOf("ħ"), "i" to listOf("ì"), "o" to listOf("ò"), "u" to listOf("ù"), "z" to listOf("ż")
        ),
        "ga" to mapOf(
            "a" to listOf("á"), "e" to listOf("é"), "i" to listOf("í"), "o" to listOf("ó"), "u" to listOf("ú")
        ),
        "tr" to mapOf(
            "c" to listOf("ç"), "g" to listOf("ğ"), "i" to listOf("ı"), "o" to listOf("ö"),
            "s" to listOf("ş"), "u" to listOf("ü")
        )
    )

    /** English and languages without own letters: the most common European accents. */
    private val COMMON_LATIN = mapOf(
        "a" to listOf("á", "à", "â", "ä", "ã", "å", "æ"),
        "c" to listOf("ç", "č", "ć"),
        "d" to listOf("đ"),
        "e" to listOf("é", "è", "ê", "ë"),
        "i" to listOf("í", "ì", "î", "ï"),
        "n" to listOf("ñ"),
        "o" to listOf("ó", "ò", "ô", "ö", "õ", "ø", "œ"),
        "s" to listOf("š", "ß"),
        "u" to listOf("ú", "ù", "û", "ü"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž")
    )

    /* ───────── CYRILLIC, PER SCRIPT (keys are the Latin labels) ───────── */

    private val SERBIAN_CYRILLIC = mapOf(
        "c" to listOf("ч", "ћ"), "d" to listOf("ђ", "џ"), "s" to listOf("ш"),
        "z" to listOf("ж"), "l" to listOf("љ"), "n" to listOf("њ")
    )

    private val MACEDONIAN_CYRILLIC = mapOf(
        "c" to listOf("ч"), "d" to listOf("џ"), "g" to listOf("ѓ"), "k" to listOf("ќ"),
        "s" to listOf("ш"), "z" to listOf("ж", "ѕ"), "l" to listOf("љ"), "n" to listOf("њ")
    )

    private val RUSSIAN_CYRILLIC = mapOf(
        "a" to listOf("я"), "c" to listOf("ч"), "e" to listOf("ё", "э"), "s" to listOf("ш", "щ"),
        "u" to listOf("ю"), "z" to listOf("ж"), "x" to listOf("ъ")
    )

    private val UKRAINIAN_CYRILLIC = mapOf(
        "a" to listOf("я"), "c" to listOf("ч"), "e" to listOf("є"), "g" to listOf("ґ"),
        "i" to listOf("ї"), "s" to listOf("ш", "щ"), "u" to listOf("ю"), "z" to listOf("ж")
    )

    private val BULGARIAN_CYRILLIC = mapOf(
        "a" to listOf("я"), "c" to listOf("ч"), "s" to listOf("ш", "щ"), "u" to listOf("ю"),
        "z" to listOf("ж"), "y" to listOf("ь")
    )

    /* ───────── GREEK (keys are the Latin labels, see ScriptMapper) ───────── */

    /** Accented vowels (tonos, dialytika) on their vowel key; ς has its own key (w). */
    private val GREEK = mapOf(
        "a" to listOf("ά"), "e" to listOf("έ"), "h" to listOf("ή"), "i" to listOf("ί", "ϊ"),
        "o" to listOf("ό"), "y" to listOf("ύ", "ϋ"), "v" to listOf("ώ")
    )
}
