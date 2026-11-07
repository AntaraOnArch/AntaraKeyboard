package com.example.antarakeyboard.data

import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LayoutPresetsTest {

    private val defaults = listOf(defaultThreeRowKeyboardLayoutQwertz, defaultFourRowKeyboardLayout, defaultKeyboardLayout)
    private val alphabet = ('a'..'z').map { it.toString() }

    @Test
    fun everyPresetHasEachLetterOnce() {
        LayoutPresets.ALL.forEach { preset ->
            val letters = preset.rows.joinToString("").filter { it in 'a'..'z' }.map { it.toString() }
            assertEquals(preset.name, alphabet, letters.sorted())
        }
    }

    @Test
    fun letterMapIsAPermutation() {
        LayoutPresets.ALL.forEach { preset ->
            val map = LayoutPresets.letterMap(preset)
            assertEquals(preset.name, alphabet, map.keys.sorted())
            assertEquals(preset.name, alphabet, map.values.sorted())
        }
    }

    @Test
    fun qwertzKeepsTheDefaults() {
        defaults.forEach { layout ->
            assertEquals(labels(layout), labels(LayoutPresets.apply(layout, LayoutPresets.QWERTZ)))
        }
    }

    @Test
    fun qwertyOnThreeRowsSwapsYAndZ() {
        val rows = labels(LayoutPresets.apply(defaultThreeRowKeyboardLayoutQwertz, LayoutPresets.QWERTY))
        assertEquals("qwertyuiop.", rows[0].joinToString(""))
        assertEquals("zxcvb  n😊123↵", rows[2].joinToString(""))
    }

    @Test
    fun azertyOnThreeRowsIsExact() {
        val rows = labels(LayoutPresets.apply(defaultThreeRowKeyboardLayoutQwertz, LayoutPresets.AZERTY))
        assertEquals("azertyuiop.", rows[0].joinToString(""))
        assertEquals("qsdfghjklm?", rows[1].joinToString(""))
    }

    @Test
    fun specialKeysStay() {
        defaults.forEach { layout ->
            LayoutPresets.ALL.forEach { preset ->
                val applied = labels(LayoutPresets.apply(layout, preset)).flatten()
                val specials = labels(layout).flatten().filter { it !in alphabet }
                assertEquals(specials, applied.filter { it !in alphabet })
                assertEquals(alphabet, applied.filter { it in alphabet }.sorted())
            }
        }
    }

    @Test
    fun detectFindsTheAppliedPreset() {
        defaults.forEach { layout ->
            LayoutPresets.ALL.forEach { preset ->
                assertSame(preset, LayoutPresets.detect(LayoutPresets.apply(layout, preset), layout))
            }
        }
        val custom = LayoutPresets.apply(defaultKeyboardLayout, LayoutPresets.QWERTY)
        val first = custom.rows[0].keys
        val tmp = first[0]; first[0] = first[1]; first[1] = tmp
        assertNull(LayoutPresets.detect(custom, defaultKeyboardLayout))
    }

    private fun labels(layout: com.example.antarakeyboard.model.KeyboardConfig) =
        layout.rows.map { row -> row.keys.map { it.label } }
}
