package com.example.antarakeyboard.data

import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicLayoutsTest {

    private val ids = listOf(ClassicLayouts.LAYOUT_CLASSIC, ClassicLayouts.LAYOUT_CLASSIC_NUMBERS)
    private val shapes = mapOf(
        ClassicLayouts.LAYOUT_CLASSIC to KeyShape.CLASSIC,
        ClassicLayouts.LAYOUT_CLASSIC_NUMBERS to KeyShape.CLASSIC_NUMBERS
    )
    private val alphabet = ('a'..'z').map { it.toString() }

    private fun labels(layout: KeyboardConfig) = layout.rows.map { row -> row.keys.map { it.label } }

    @Test
    fun layoutIdFollowsShape() {
        assertEquals(ClassicLayouts.LAYOUT_CLASSIC, ClassicLayouts.layoutId(KeyShape.CLASSIC, 3))
        assertEquals(ClassicLayouts.LAYOUT_CLASSIC_NUMBERS, ClassicLayouts.layoutId(KeyShape.CLASSIC_NUMBERS, 5))
        assertEquals(4, ClassicLayouts.layoutId(KeyShape.HEX, 4))
        assertEquals(4, ClassicLayouts.rowCountOf(KeyShape.CLASSIC))
        assertEquals(5, ClassicLayouts.rowCountOf(KeyShape.CLASSIC_NUMBERS))
    }

    @Test
    fun defaultRowsFitOneRowWidth() {
        ids.forEach { id ->
            listOf(ClassicLayouts.defaultAlphabet(id), ClassicLayouts.defaultNumeric(id)).forEach { layout ->
                labels(layout).forEach { row ->
                    val sum = ClassicLayouts.keyWidths(shapes.getValue(id), row).sum()
                    assertTrue("$id $row = $sum", sum <= ClassicLayouts.ROW_UNITS + 0.001f)
                    // A row with the space key fills the whole width
                    if (" " in row) assertEquals(ClassicLayouts.ROW_UNITS, sum, 0.001f)
                }
            }
        }
    }

    @Test
    fun spaceTakesWhatIsLeftButNeverLessThanTwo() {
        val widths = ClassicLayouts.keyWidths(KeyShape.CLASSIC, listOf("123", "😊", " ", ".", "↵"))
        assertEquals(listOf(1.25f, 1.25f, 4.5f, 1f, 2f), widths)
        val crowded = ClassicLayouts.keyWidths(KeyShape.CLASSIC, List(10) { "a" } + " ")
        assertEquals(2f, crowded.last(), 0.001f)
        // Side buttons take their room from the space
        val withSides = ClassicLayouts.keyWidths(KeyShape.CLASSIC, listOf("123", " ", "↵"), sideUnits = 3f)
        assertEquals(10f - 3f - 1.25f - 2f, withSides[1], 0.001f)
    }

    @Test
    fun spaceStretchesEveryRowToTheWidestRow() {
        // Ten letters with a side button on both ends make the keyboard 12 units wide
        val (units, widths) = ClassicLayouts.layoutWidths(
            KeyShape.CLASSIC,
            listOf(
                ClassicLayouts.RowSpec(List(10) { "a" }, sideUnits = 2f),
                ClassicLayouts.RowSpec(listOf("123", "😊", " ", ".", "↵"))
            )
        )
        assertEquals(12f, units, 0.001f)
        assertEquals(10f, widths[0].sum(), 0.001f)
        assertEquals(12f, widths[1].sum(), 0.001f)
        // Without side buttons the keyboard stays 10 units wide
        assertEquals(10f, ClassicLayouts.layoutWidths(KeyShape.CLASSIC, listOf(ClassicLayouts.RowSpec(List(10) { "a" }))).first, 0.001f)
    }

    @Test
    fun alphabetHasEveryLetterAndTheSpecialKeys() {
        ids.forEach { id ->
            val keys = labels(ClassicLayouts.defaultAlphabet(id)).flatten()
            assertEquals(alphabet, keys.filter { it.length == 1 && it[0] in 'a'..'z' }.sorted())
            listOf("⇧", "⌫", "123", "😊", " ", ".", "↵").forEach { assertTrue("$id $it", it in keys) }
        }
        assertTrue(labels(ClassicLayouts.defaultAlphabet(ClassicLayouts.LAYOUT_CLASSIC_NUMBERS)).first() ==
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
    }

    @Test
    fun numericHasTheProtectedKeysOnce() {
        val protected = listOf("abc", "↵") + (0..9).map { it.toString() }
        ids.forEach { id ->
            val keys = labels(ClassicLayouts.defaultNumeric(id)).flatten()
            protected.forEach { assertEquals("$id $it", 1, keys.count { k -> k == it }) }
            // No side buttons on the number-row layout: ⌫ must be a key
            assertTrue("⌫" in keys)
        }
    }

    @Test
    fun periodCarriesQuestionAndExclamationMark() {
        ids.forEach { id ->
            val period = ClassicLayouts.defaultAlphabet(id).rows.flatMap { it.keys }.first { it.label == "." }
            assertEquals(ClassicLayouts.PERIOD_LONG_PRESS, period.longPressBindings)
        }
        assertEquals(listOf("?", "!", "\"", ",", ":", ";"), ClassicLayouts.withPunctuationOnPeriod(null, 46))
        assertEquals(listOf("?", "!", ",", "x"), ClassicLayouts.withPunctuationOnPeriod(listOf(",", "x"), 46))
        assertEquals(listOf("!", "?", "x"), ClassicLayouts.withPunctuationOnPeriod(listOf("!", "?", "x"), 46))
        // No room: the user's binds stay as they are
        val full = List(46) { "c$it" }
        assertEquals(full, ClassicLayouts.withPunctuationOnPeriod(full, 46))
    }

    @Test
    fun sideButtonsOnlyOnTheClassicLayout() {
        val slots = EdgeSlotsStorage.defaultSlots(ClassicLayouts.LAYOUT_CLASSIC)
        assertEquals(6, slots.size)
        assertEquals(EdgeActionType.SHIFT, slots[4].type)
        assertEquals(EdgeActionType.BACKSPACE, slots[5].type)
        assertEquals(2, ClassicLayouts.sideSlotRow(4))
        assertEquals(0, EdgeSlotsStorage.slotCountForRowCount(ClassicLayouts.LAYOUT_CLASSIC_NUMBERS))
        assertTrue(EdgeSlotsStorage.defaultSlots(ClassicLayouts.LAYOUT_CLASSIC_NUMBERS).isEmpty())
    }

    @Test
    fun presetsKeepEveryLetterAndSpecialKey() {
        ids.forEach { id ->
            val default = ClassicLayouts.defaultAlphabet(id)
            val specials = labels(default).flatten().filterNot { it.length == 1 && it[0] in 'a'..'z' }
            LayoutPresets.ALL.forEach { preset ->
                val keys = labels(LayoutPresets.apply(default, preset)).flatten()
                assertEquals(preset.name, alphabet, keys.filter { it.length == 1 && it[0] in 'a'..'z' }.sorted())
                assertEquals(preset.name, specials, keys.filterNot { it.length == 1 && it[0] in 'a'..'z' })
            }
        }
    }
}
