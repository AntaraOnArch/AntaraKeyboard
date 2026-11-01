package com.example.antarakeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPressPresetsTest {

    @Test
    fun latinKeyboardUsesTheLanguagesLetters() {
        val hr = LongPressPresets.defaultsFor(LongPressPresets.PRESET_LATIN, "hr-HR")
        assertEquals(listOf("č", "ć"), hr["c"])
        assertEquals(listOf("đ"), hr["d"])
        assertEquals(listOf("Č", "Ć"), hr["C"])
        assertNull("no binding on plain keys", hr["a"])

        val de = LongPressPresets.defaultsFor(LongPressPresets.PRESET_SYSTEM, "de")
        assertEquals(listOf("ä"), de["a"])
        assertEquals(listOf("ß"), de["s"])
    }

    @Test
    fun systemPresetIsAlwaysLatin() {
        // The "system" keyboard types Latin, so even a Serbian device gets Latin letters
        val sr = LongPressPresets.defaultsFor(LongPressPresets.PRESET_SYSTEM, "sr-RS")
        assertEquals(listOf("č", "ć"), sr["c"])
    }

    @Test
    fun unknownLanguagesGetCommonAccentsWithoutSymbols() {
        val en = LongPressPresets.defaultsFor(LongPressPresets.PRESET_LATIN, "en-US")
        assertTrue(en["e"]!!.contains("é"))
        assertFalse(en.values.flatten().any { it in listOf("℃", "ℂ", "₡", "ⓒ") })
    }

    @Test
    fun cyrillicBindsOnlySpecialLetters() {
        val sr = LongPressPresets.defaultsFor(LongPressPresets.PRESET_SERBIAN_CYRILLIC, "hr")
        assertEquals(listOf("ч", "ћ"), sr["c"])
        assertEquals(listOf("ђ", "џ"), sr["d"])
        assertEquals(listOf("љ"), sr["l"])
        // the "a" key already types "а" – nothing to bind
        assertNull(sr["a"])
        assertNull(sr["b"])

        val ru = LongPressPresets.defaultsFor(LongPressPresets.PRESET_RUSSIAN_CYRILLIC, "hr")
        assertEquals(listOf("ё", "э"), ru["e"])
    }

    @Test
    fun switchingDefaultsKeepsUserBinds() {
        val latinHr = LongPressPresets.defaultsFor(LongPressPresets.PRESET_LATIN, "hr")
        val current = latinHr + mapOf(
            "c" to listOf("č", "ć", "©"),           // user added ©
            "e" to listOf("moj@mail.com")          // user custom text
        )
        val serbian = LongPressPresets.defaultsFor(LongPressPresets.PRESET_SERBIAN_CYRILLIC, "hr")

        val merged = LongPressPresets.mergeDefaults(current, latinHr, serbian)
        assertEquals(listOf("ч", "ћ", "©"), merged["c"])
        assertEquals(listOf("moj@mail.com"), merged["e"])
        assertNull("Latin š removed, Serbian has ш", merged["s"]?.find { it == "š" })
        assertEquals(listOf("ш"), merged["s"])
    }

    @Test
    fun migrationRemovesOldDefaultsButKeepsCustomBinds() {
        // Old installs: every key bound to its Cyrillic twin plus big Latin lists
        val oldBinds = mapOf(
            "a" to listOf("а"),
            "c" to listOf("ц", "ч", "ћ", "℃", "★"),   // ★ added by the user
            "e" to listOf("pozdrav")
        )
        val legacy = LongPressPresets.withUppercaseSets(LegacyLongPressPresets.allDefaults())
        val hr = LongPressPresets.defaultsFor(LongPressPresets.PRESET_LATIN, "hr")

        val merged = LongPressPresets.mergeDefaults(oldBinds, legacy, hr)
        assertNull(merged["a"])
        assertEquals(listOf("č", "ć", "★"), merged["c"])
        assertEquals(listOf("pozdrav"), merged["e"])
    }

    @Test
    fun pickerListsContainEveryScript() {
        val cyr = LongPressPresets.allCyrillicLetters()
        listOf("ђ", "ѓ", "ґ", "ё", "Љ").forEach { assertTrue(it, it in cyr) }
        val lat = LongPressPresets.allLatinLetters()
        listOf("č", "ß", "ø", "ő", "Ž").forEach { assertTrue(it, it in lat) }
    }

    @Test
    fun eachScriptShowsOnlyItsOwnLetters() {
        val binds = listOf("č", "ч", "Ж", "Š", "😁", "moj tekst", "@", "5")
        assertEquals(
            listOf("č", "Š", "😁", "moj tekst", "@", "5"),
            LongPressPresets.visibleFor(LongPressPresets.PRESET_LATIN, binds)
        )
        assertEquals(
            listOf("ч", "Ж", "😁", "moj tekst", "@", "5"),
            LongPressPresets.visibleFor(LongPressPresets.PRESET_SERBIAN_CYRILLIC, binds)
        )
        assertEquals(
            listOf("ά", "Ώ", "😁"),
            LongPressPresets.visibleFor(LongPressPresets.PRESET_GREEK, listOf("ά", "č", "ч", "Ώ", "😁"))
        )
        assertEquals(listOf("č"), LongPressPresets.visibleFor(LongPressPresets.PRESET_LATIN, listOf("č", "ά")))
    }

    @Test
    fun greekDefaultsAreAccentedVowels() {
        val greek = LongPressPresets.defaultsFor(LongPressPresets.PRESET_GREEK, "el")
        assertEquals(listOf("ά"), greek["a"])
        assertEquals(listOf("Ί", "Ϊ", "ΐ"), greek["I"])
        assertTrue("ΐ" in LongPressPresets.allGreekLetters())
    }

    @Test
    fun greekLettersWithoutUppercaseStayOnShiftedKey() {
        val greek = LongPressPresets.defaultsFor(LongPressPresets.PRESET_GREEK, "el")
        assertEquals(listOf("ί", "ϊ", "ΐ"), greek["i"])
        assertEquals(listOf("Ί", "Ϊ", "ΐ"), greek["I"])
    }

    @Test
    fun everyTranslatedLatinLanguageHasItsOwnLetters() {
        val common = LongPressPresets.latinFor("en")
        AppLanguages.TAGS
            .filter { it != "en" && AppLanguages.scriptPresetFor(it) == LongPressPresets.PRESET_LATIN }
            .forEach { tag -> assertTrue(tag, LongPressPresets.latinFor(tag) != common) }
    }

    @Test
    fun czechAndCroatianExamples() {
        assertEquals(listOf("š"), LongPressPresets.defaultsFor(LongPressPresets.PRESET_LATIN, "hr")["s"])
        assertEquals(listOf("á"), LongPressPresets.defaultsFor(LongPressPresets.PRESET_LATIN, "cs")["a"])
    }

    /** Keys + long press reach the whole alphabet of every Cyrillic and the Greek script. */
    @Test
    fun scriptKeyboardsReachTheirWholeAlphabet() {
        val alphabets = mapOf(
            LongPressPresets.PRESET_SERBIAN_CYRILLIC to "абвгдђежзијклљмнњопрстћуфхцчџш",
            LongPressPresets.PRESET_MACEDONIAN_CYRILLIC to "абвгдѓежзѕијклљмнњопрстќуфхцчџш",
            LongPressPresets.PRESET_RUSSIAN_CYRILLIC to "абвгдеёжзийклмнопрстуфхцчшщъыьэюя",
            LongPressPresets.PRESET_UKRAINIAN_CYRILLIC to "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя",
            LongPressPresets.PRESET_BULGARIAN_CYRILLIC to "абвгдежзийклмнопрстуфхцчшщъьюя",
            LongPressPresets.PRESET_BELARUSIAN_CYRILLIC to "абвгдеёжзійклмнопрстуўфхцчшыьэюя",
            LongPressPresets.PRESET_GREEK to "αβγδεζηθικλμνξοπρσςτυφχψωάέήίόύώϊϋΐΰ"
        )
        alphabets.forEach { (preset, alphabet) ->
            val reachable = ('a'..'z').map { ScriptMapper.map(preset, it.toString()) }.toSet() +
                LongPressPresets.defaultsFor(preset, "en").values.flatten()
            alphabet.forEach { letter -> assertTrue("$preset $letter", letter.toString() in reachable) }
        }
    }
}
