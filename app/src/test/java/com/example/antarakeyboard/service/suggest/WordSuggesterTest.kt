package com.example.antarakeyboard.service.suggest

import com.example.antarakeyboard.data.LongPressPresets
import com.example.antarakeyboard.service.InputTypes
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordSuggesterTest {

    private val small = WordSuggester(
        mapOf(
            "prijatelj" to 900L, "prijatelji" to 500L, "prijava" to 300L, "prije" to 2000L,
            "sutra" to 1000L, "kuća" to 800L, "kuće" to 400L
        )
    )

    @Test
    fun completesByFrequency() {
        assertEquals(listOf("prije", "prijatelj", "prijatelji"), small.suggest("prij"))
    }

    @Test
    fun correctsTyposFirst() {
        assertEquals("prijatelj", small.suggest("prijatlj").first())
        assertEquals("sutra", small.suggest("sutr").first())
    }

    @Test
    fun keepsTypedCapitalization() {
        assertEquals("Prije", small.suggest("Prij").first())
        assertEquals("PRIJE", small.suggest("PRIJ").first())
    }

    @Test
    fun nothingForEmptyOrUnknownShortInput() {
        assertTrue(small.suggest("").isEmpty())
        assertTrue(small.suggest("qx").isEmpty())
    }

    @Test
    fun currentWordIsTheWordBeforeTheCursor() {
        assertEquals("prij", WordSuggester.currentWord("Bok prij", ""))
        assertEquals("", WordSuggester.currentWord("Bok ", ""))
        assertEquals("don't", WordSuggester.currentWord("I don't", ""))
        // a trailing apostrophe is not part of the word
        assertEquals("", WordSuggester.currentWord("I don'", ""))
        // cursor inside a word → no suggestions
        assertEquals("", WordSuggester.currentWord("pri", "jatelj"))
        assertEquals("kuć", WordSuggester.currentWord("(kuć", " i"))
    }

    @Test
    fun parsesDictionaryLines() {
        val parsed = WordSuggester.parse(sequenceOf("je 10", "broken", "da x", "ne 5"))
        assertEquals(mapOf("je" to 10L, "ne" to 5L), parsed)
    }

    @Test
    fun bundledCroatianDictionaryWorks() {
        val file = File("src/main/assets/dictionaries/hr.txt")
        val suggester = WordSuggester(WordSuggester.parse(file.readLines().asSequence()))
        assertTrue(suggester.suggest("prijatlj").contains("prijatelj"))
        assertEquals(3, suggester.suggest("ku").size)
    }

    @Test
    fun everyBundledDictionaryExists() {
        listOf("en", "hr", "sr_latn", "sr_cyrl", "bs", "de", "ru").forEach { name ->
            assertTrue(name, File("src/main/assets/dictionaries/$name.txt").length() > 100_000)
        }
    }

    @Test
    fun dictionaryFollowsScriptAndLanguage() {
        val latin = LongPressPresets.PRESET_LATIN
        assertEquals("hr", SuggestionDictionaries.forKeyboard(latin, "hr-HR"))
        assertEquals("sr_latn", SuggestionDictionaries.forKeyboard(latin, "sr-Latn"))
        assertEquals("de", SuggestionDictionaries.forKeyboard(LongPressPresets.PRESET_SYSTEM, "de"))
        assertEquals("en", SuggestionDictionaries.forKeyboard(latin, "fr"))
        assertEquals("sr_cyrl", SuggestionDictionaries.forKeyboard(LongPressPresets.PRESET_SERBIAN_CYRILLIC, "en"))
        assertEquals("ru", SuggestionDictionaries.forKeyboard(LongPressPresets.PRESET_RUSSIAN_CYRILLIC, "hr"))
        assertNull(SuggestionDictionaries.forKeyboard(LongPressPresets.PRESET_BULGARIAN_CYRILLIC, "bg"))
    }

    @Test
    fun suggestionsOnlyInPlainTextFields() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(InputTypes.allowsSuggestions(text))
        assertTrue(InputTypes.allowsSuggestions(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(InputTypes.allowsSuggestions(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(InputTypes.allowsSuggestions(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(InputTypes.allowsSuggestions(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(InputTypes.allowsSuggestions(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(InputTypes.allowsSuggestions(InputType.TYPE_CLASS_NUMBER))
    }
}
