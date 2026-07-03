package com.example.antarakeyboard.data

import android.content.Context
import android.os.Build
import java.util.Locale

object LongPressPresets {

    const val PRESET_SYSTEM = "system"
    const val PRESET_LATIN = "latin"
    const val PRESET_SERBIAN_CYRILLIC = "sr_cyrl"
    const val PRESET_BULGARIAN_CYRILLIC = "bg_cyrl"
    const val PRESET_RUSSIAN_CYRILLIC = "ru_cyrl"
    const val PRESET_UKRAINIAN_CYRILLIC = "uk_cyrl"
    const val PRESET_MACEDONIAN_CYRILLIC = "mk_cyrl"

    fun getForSystemLanguage(context: Context): Map<String, List<String>> {
        val locale = getSystemLocale(context)

        val preset = when {
            usesSerbianCyrillic(locale) -> serbianCyrillic()
            usesBulgarianCyrillic(locale) -> bulgarianCyrillic()
            usesRussianCyrillic(locale) -> russianCyrillic()
            usesUkrainianCyrillic(locale) -> ukrainianCyrillic()
            usesMacedonianCyrillic(locale) -> macedonianCyrillic()
            usesLatinScript(locale) -> basicLatin()
            else -> emptyMap()
        }

        return withUppercase(preset)
    }
    fun getById(context: Context, presetId: String): Map<String, List<String>> {
        return when (presetId) {
            PRESET_LATIN -> withUppercase(basicLatin())
            PRESET_SERBIAN_CYRILLIC -> withUppercase(serbianCyrillic())
            PRESET_BULGARIAN_CYRILLIC -> withUppercase(bulgarianCyrillic())
            PRESET_RUSSIAN_CYRILLIC -> withUppercase(russianCyrillic())
            PRESET_UKRAINIAN_CYRILLIC -> withUppercase(ukrainianCyrillic())
            PRESET_MACEDONIAN_CYRILLIC -> withUppercase(macedonianCyrillic())
            PRESET_SYSTEM -> getForSystemLanguage(context)
            else -> getForSystemLanguage(context)
        }
    }
    private fun getSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    private fun usesSerbianCyrillic(locale: Locale): Boolean {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "sr") return false

        val script = locale.script
        val tag = locale.toLanguageTag()

        if (script.equals("Latn", ignoreCase = true)) return false
        if (tag.contains("Latn", ignoreCase = true)) return false

        if (script.equals("Cyrl", ignoreCase = true)) return true
        if (tag.contains("Cyrl", ignoreCase = true)) return true

        // Ako je srpski, ali script nije eksplicitno naveden,
        // tretiramo ga kao ćirilicu osim ako gore nije bio Latn.
        return true
    }

    private fun usesBulgarianCyrillic(locale: Locale): Boolean {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "bg") return false

        val script = locale.script
        val tag = locale.toLanguageTag()

        if (script.equals("Latn", ignoreCase = true)) return false
        if (tag.contains("Latn", ignoreCase = true)) return false

        return true
    }

    private fun usesRussianCyrillic(locale: Locale): Boolean {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "ru") return false

        val script = locale.script
        val tag = locale.toLanguageTag()

        if (script.equals("Latn", ignoreCase = true)) return false
        if (tag.contains("Latn", ignoreCase = true)) return false

        return true
    }
    private fun usesUkrainianCyrillic(locale: Locale): Boolean {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "uk") return false

        val script = locale.script
        val tag = locale.toLanguageTag()

        if (script.equals("Latn", ignoreCase = true)) return false
        if (tag.contains("Latn", ignoreCase = true)) return false

        return true
    }

    private fun usesMacedonianCyrillic(locale: Locale): Boolean {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "mk") return false

        val script = locale.script
        val tag = locale.toLanguageTag()

        if (script.equals("Latn", ignoreCase = true)) return false
        if (tag.contains("Latn", ignoreCase = true)) return false

        return true
    }

    private fun usesLatinScript(locale: Locale): Boolean {
        val script = locale.script

        if (script.equals("Latn", ignoreCase = true)) {
            return true
        }

        if (script.isNotBlank() && !script.equals("Latn", ignoreCase = true)) {
            return false
        }

        val language = locale.language.lowercase(Locale.ROOT)

        return language in setOf(
            "hr", "bs",
            "en", "de", "fr", "it", "es", "pt",
            "nl", "sv", "no", "da", "fi",
            "pl", "cs", "sk", "sl",
            "hu", "ro", "tr",
            "id", "ms", "vi"
        )
    }

    private fun serbianCyrillic(): Map<String, List<String>> {
        return mapOf(
            // Latin layout fallback
            "a" to listOf("а"),
            "b" to listOf("б"),
            "v" to listOf("в"),
            "g" to listOf("г"),
            "d" to listOf("д", "ђ", "џ"),
            "e" to listOf("е"),
            "z" to listOf("з", "ж"),
            "i" to listOf("и"),
            "j" to listOf("ј"),
            "k" to listOf("к"),
            "l" to listOf("л", "љ"),
            "m" to listOf("м"),
            "n" to listOf("н", "њ"),
            "o" to listOf("о"),
            "p" to listOf("п"),
            "r" to listOf("р"),
            "s" to listOf("с", "ш"),
            "t" to listOf("т", "ћ"),
            "u" to listOf("у"),
            "f" to listOf("ф"),
            "h" to listOf("х"),
            "c" to listOf("ц", "ч", "ћ"),

            // Tipke koje postoje na latin layoutu, ali nisu dio srpske latinice
            "q" to listOf("љ"),
            "w" to listOf("њ"),
            "x" to listOf("џ"),
            "y" to listOf("ј"),

            // Future Cyrillic layout support
            "л" to listOf("љ"),
            "н" to listOf("њ"),
            "д" to listOf("ђ", "џ"),
            "т" to listOf("ћ"),
            "ч" to listOf("џ")
        )
    }

    private fun bulgarianCyrillic(): Map<String, List<String>> {
        return mapOf(
            // Latin layout fallback
            "a" to listOf("а", "я"),
            "b" to listOf("б"),
            "v" to listOf("в"),
            "g" to listOf("г"),
            "d" to listOf("д"),
            "e" to listOf("е"),
            "z" to listOf("з", "ж"),
            "i" to listOf("и", "й"),
            "j" to listOf("й"),
            "k" to listOf("к"),
            "l" to listOf("л"),
            "m" to listOf("м"),
            "n" to listOf("н"),
            "o" to listOf("о"),
            "p" to listOf("п"),
            "r" to listOf("р"),
            "s" to listOf("с", "ш", "щ"),
            "t" to listOf("т"),
            "u" to listOf("у", "ю"),
            "f" to listOf("ф"),
            "h" to listOf("х"),
            "c" to listOf("ц", "ч"),

            // Dodatne tipke za bugarska slova bez čistog latin para
            "q" to listOf("я"),
            "w" to listOf("ш", "щ"),
            "x" to listOf("х", "ь"),
            "y" to listOf("ъ", "ь"),

            // Future Cyrillic layout support
            "з" to listOf("ж"),
            "ц" to listOf("ч"),
            "с" to listOf("ш", "щ"),
            "у" to listOf("ю"),
            "а" to listOf("я"),
            "и" to listOf("й"),
            "ъ" to listOf("ь")
        )
    }

    private fun russianCyrillic(): Map<String, List<String>> {
        return mapOf(
            // Latin layout fallback
            "a" to listOf("а", "я"),
            "b" to listOf("б"),
            "v" to listOf("в"),
            "g" to listOf("г"),
            "d" to listOf("д"),
            "e" to listOf("е", "ё", "э"),
            "z" to listOf("з", "ж"),
            "i" to listOf("и"),
            "j" to listOf("й"),
            "k" to listOf("к"),
            "l" to listOf("л"),
            "m" to listOf("м"),
            "n" to listOf("н"),
            "o" to listOf("о"),
            "p" to listOf("п"),
            "r" to listOf("р"),
            "s" to listOf("с", "ш", "щ"),
            "t" to listOf("т"),
            "u" to listOf("у", "ю"),
            "f" to listOf("ф"),
            "h" to listOf("х"),
            "c" to listOf("ц", "ч"),

            // Dodatne tipke za ruska slova bez čistog latin para
            "q" to listOf("я"),
            "w" to listOf("ш", "щ"),
            "x" to listOf("х", "ь"),
            "y" to listOf("ы", "ъ", "ь"),

            // Future Cyrillic layout support
            "е" to listOf("ё", "э"),
            "з" to listOf("ж"),
            "ц" to listOf("ч"),
            "с" to listOf("ш", "щ"),
            "у" to listOf("ю"),
            "а" to listOf("я"),
            "ы" to listOf("ъ", "ь"),
            "х" to listOf("ь")
        )
    }

    private fun ukrainianCyrillic(): Map<String, List<String>> {
        return mapOf(
            // Latin layout fallback
            "a" to listOf("а", "я"),
            "b" to listOf("б"),
            "v" to listOf("в"),
            "g" to listOf("г", "ґ"),
            "d" to listOf("д"),
            "e" to listOf("е", "є"),
            "z" to listOf("з", "ж"),
            "i" to listOf("і", "и", "ї"),
            "j" to listOf("й", "ї"),
            "k" to listOf("к"),
            "l" to listOf("л"),
            "m" to listOf("м"),
            "n" to listOf("н"),
            "o" to listOf("о"),
            "p" to listOf("п"),
            "r" to listOf("р"),
            "s" to listOf("с", "ш", "щ"),
            "t" to listOf("т"),
            "u" to listOf("у", "ю"),
            "f" to listOf("ф"),
            "h" to listOf("х"),
            "c" to listOf("ц", "ч"),

            // Dodatne tipke za ukrajinska slova bez čistog latin para
            "q" to listOf("я"),
            "w" to listOf("ш", "щ"),
            "x" to listOf("х", "ь"),
            "y" to listOf("и", "й"),

            // Future Cyrillic layout support
            "г" to listOf("ґ"),
            "е" to listOf("є"),
            "і" to listOf("ї"),
            "и" to listOf("й"),
            "з" to listOf("ж"),
            "ц" to listOf("ч"),
            "с" to listOf("ш", "щ"),
            "у" to listOf("ю"),
            "а" to listOf("я"),
            "х" to listOf("ь")
        )
    }


    private fun macedonianCyrillic(): Map<String, List<String>> {
        return mapOf(
            // Latin layout fallback
            "a" to listOf("а"),
            "b" to listOf("б"),
            "v" to listOf("в"),
            "g" to listOf("г", "ѓ"),
            "d" to listOf("д", "џ"),
            "e" to listOf("е"),
            "z" to listOf("з", "ж", "ѕ"),
            "i" to listOf("и"),
            "j" to listOf("ј"),
            "k" to listOf("к", "ќ"),
            "l" to listOf("л", "љ"),
            "m" to listOf("м"),
            "n" to listOf("н", "њ"),
            "o" to listOf("о"),
            "p" to listOf("п"),
            "r" to listOf("р"),
            "s" to listOf("с", "ш"),
            "t" to listOf("т"),
            "u" to listOf("у"),
            "f" to listOf("ф"),
            "h" to listOf("х"),
            "c" to listOf("ц", "ч"),

            // Dodatne tipke za makedonska slova bez čistog latin para
            "q" to listOf("љ"),
            "w" to listOf("њ"),
            "x" to listOf("џ"),
            "y" to listOf("ѕ"),

            // Future Cyrillic layout support
            "г" to listOf("ѓ"),
            "к" to listOf("ќ"),
            "з" to listOf("ж", "ѕ"),
            "д" to listOf("џ"),
            "л" to listOf("љ"),
            "н" to listOf("њ"),
            "ц" to listOf("ч"),
            "с" to listOf("ш")
        )
    }
    private fun basicLatin(): Map<String, List<String>> {
        return mapOf(
            "a" to listOf("á", "à", "â", "ä", "ã", "å", "ā", "æ"),
            "c" to listOf("ç", "č", "ć", "℃", "ℂ", "₡", "ⓒ"),
            "d" to listOf("đ", "ď"),
            "e" to listOf("é", "è", "ê", "ë", "ē", "ė", "ę"),
            "g" to listOf("ĝ", "ğ", "ġ"),
            "h" to listOf("ĥ", "ȟ", "ḣ", "ḥ", "ḧ", "ḩ", "ḫ", "ẖ"),
            "i" to listOf("í", "ì", "î", "ï", "ī", "į"),
            "j" to listOf("ĵ", "ǰ", "ɉ"),
            "k" to listOf("ķ", "ǩ", "ḱ", "ḳ", "ḵ", "ꝁ"),
            "l" to listOf("ĺ", "ļ", "ľ", "ŀ", "ł"),
            "n" to listOf("ñ", "ń", "ņ", "ň", "ŉ", "ǹ"),
            "o" to listOf("ó", "ò", "ô", "ö", "õ", "ø", "ō"),
            "r" to listOf("ŕ", "ŗ", "ř", "ȑ", "ȓ", "ɍ"),
            "s" to listOf("ß", "š", "ṡ", "ṧ", "ṥ", "ṣ", "ṩ", "ş"),
            "t" to listOf("ť", "ţ", "ŧ", "ƭ", "ƫ", "ț", "ȶ", "ṫ", "ṭ"),
            "u" to listOf("ú", "ù", "û", "ü", "ū", "µ", "ŭ", "ů", "ű", "ų"),
            "y" to listOf("ý", "ÿ"),
            "z" to listOf("ž", "ź", "ż", "ƶ", "ẑ", "ẓ", "ẕ")
        )
    }

    private fun withUppercase(source: Map<String, List<String>>): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()

        source.forEach { (key, values) ->
            result[key] = values
            result[key.uppercase(Locale.ROOT)] = values.map {
                it.uppercase(Locale.ROOT)
            }
        }

        return result
    }
}