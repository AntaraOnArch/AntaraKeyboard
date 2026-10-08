package com.example.antarakeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptMapperTest {

    @Test
    fun latinPresetLeavesTextUnchanged() {
        assertEquals("a", ScriptMapper.map(LongPressPresets.PRESET_LATIN, "a"))
        assertEquals("Q", ScriptMapper.map(LongPressPresets.PRESET_LATIN, "Q"))
    }

    @Test
    fun serbianMapsLettersAndKeepsCase() {
        val serbian = LongPressPresets.PRESET_SERBIAN_CYRILLIC
        assertEquals("а", ScriptMapper.map(serbian, "a"))
        assertEquals("Б", ScriptMapper.map(serbian, "B"))
        assertEquals("љ", ScriptMapper.map(serbian, "q"))
        assertEquals("Њ", ScriptMapper.map(serbian, "W"))
        assertEquals("џ", ScriptMapper.map(serbian, "x"))
    }

    @Test
    fun scriptSpecificLetters() {
        assertEquals("ы", ScriptMapper.map(LongPressPresets.PRESET_RUSSIAN_CYRILLIC, "y"))
        assertEquals("і", ScriptMapper.map(LongPressPresets.PRESET_UKRAINIAN_CYRILLIC, "i"))
        assertEquals("ъ", ScriptMapper.map(LongPressPresets.PRESET_BULGARIAN_CYRILLIC, "y"))
        assertEquals("ѕ", ScriptMapper.map(LongPressPresets.PRESET_MACEDONIAN_CYRILLIC, "y"))
    }

    @Test
    fun greekUsesTheStandardGreekLayout() {
        val greek = LongPressPresets.PRESET_GREEK
        assertEquals("α", ScriptMapper.map(greek, "a"))
        assertEquals("Ψ", ScriptMapper.map(greek, "C"))
        assertEquals("θ", ScriptMapper.map(greek, "u"))
        assertEquals("ς", ScriptMapper.map(greek, "w"))
        assertEquals("Ω", ScriptMapper.map(greek, "V"))
    }

    @Test
    fun nonLettersAndMultiCharLabelsAreUnchanged() {
        val serbian = LongPressPresets.PRESET_SERBIAN_CYRILLIC
        assertEquals("1", ScriptMapper.map(serbian, "1"))
        assertEquals("?", ScriptMapper.map(serbian, "?"))
        assertEquals("123", ScriptMapper.map(serbian, "123"))
        assertEquals("ABC", ScriptMapper.map(serbian, "ABC"))
        assertEquals("", ScriptMapper.map(serbian, ""))
    }

    @Test
    fun alreadyCyrillicTextIsUnchanged() {
        assertEquals("ж", ScriptMapper.map(LongPressPresets.PRESET_RUSSIAN_CYRILLIC, "ж"))
    }
}
