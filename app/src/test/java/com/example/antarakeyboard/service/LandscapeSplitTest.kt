package com.example.antarakeyboard.service

import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LandscapeSplitTest {

    private fun keys(labels: String) = labels.map { KeyConfig(it.toString()) }
    private fun List<KeyConfig>.labels() = joinToString("") { it.label }

    @Test
    fun threeRowsSplitAtFixedPoints() {
        val row = keys("qwertzuiop.")
        assertEquals("qwertz", LandscapeSplit.leftKeys(row, 0, 3).labels())
        assertEquals("uiop.", LandscapeSplit.rightKeys(row, 0, 3).labels())
        assertEquals("qwert", LandscapeSplit.leftKeys(row, 1, 3).labels())
        assertEquals("zuiop.", LandscapeSplit.rightKeys(row, 1, 3).labels())
    }

    @Test
    fun fourAndFiveRowsAlternateThreeAndFourKeys() {
        val row = keys("abcdefg")
        assertEquals(3, LandscapeSplit.leftKeys(row, 0, 5).size)
        assertEquals(4, LandscapeSplit.leftKeys(row, 1, 5).size)
        assertEquals("efg", LandscapeSplit.rightKeys(row, 0, 5).labels())
        // 4-row rows 1 and 3 with at least 8 keys get 4 on each side
        assertEquals(4, LandscapeSplit.leftKeys(keys("abcdefgh"), 0, 4).size)
        assertEquals(3, LandscapeSplit.leftKeys(keys("abcdefg"), 0, 4).size)
    }

    @Test
    fun edgeGhostsAreSkipped() {
        val row = keys("abcdefg").toMutableList()
        row[0] = KeyConfig("⇧", mutableListOf(KeyMarkers.EDGE_GHOST))
        assertFalse(LandscapeSplit.leftKeys(row, 1, 5).any { it.label == "⇧" })
    }

    @Test
    fun everyLetterIsPlacedInTheDefaultLayouts() {
        listOf(3 to defaultThreeRowKeyboardLayoutQwertz, 4 to defaultFourRowKeyboardLayout, 5 to defaultKeyboardLayout)
            .forEach { (rowCount, layout) ->
                val placed = layoutLetters(rowCount, layout.rows.map { it.keys })
                assertEquals("$rowCount rows", ('a'..'z').map { it.toString() }, placed.distinct().sorted())
            }
    }

    @Test
    fun fiveRowsShowTheMiddleLetterOfTheSevenKeyRowsOnBothHalves() {
        // Current behavior: rows 2 and 4 have 7 keys and are split 4 + 4
        val placed = layoutLetters(5, defaultKeyboardLayout.rows.map { it.keys })
        assertEquals(listOf("g", "n"), placed.groupBy { it }.filterValues { it.size > 1 }.keys.sorted())
    }

    private fun layoutLetters(rowCount: Int, rows: List<List<KeyConfig>>) =
        rows.flatMapIndexed { i, row ->
            LandscapeSplit.leftKeys(row, i, rowCount) + LandscapeSplit.rightKeys(row, i, rowCount)
        }.map { it.label }.filter { it.length == 1 && it[0].isLetter() }
}
