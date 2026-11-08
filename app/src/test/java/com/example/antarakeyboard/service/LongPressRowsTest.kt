package com.example.antarakeyboard.service

import com.example.antarakeyboard.model.MAX_LONG_PRESS_BINDINGS
import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressRowsTest {

    @Test
    fun gridRowsAreFull() {
        assertEquals(listOf(7, 7, 2), LongPressPopupManager.rowSizes(16, 7, honeycomb = false))
        assertEquals(listOf(2), LongPressPopupManager.rowSizes(2, 2, honeycomb = false))
    }

    @Test
    fun honeycombAlternatesFullAndShorterRows() {
        assertEquals(listOf(7, 6, 7, 6, 7, 6), LongPressPopupManager.rowSizes(39, 7, honeycomb = true))
        assertEquals(listOf(7, 6, 7, 6, 7, 6, 7), LongPressPopupManager.rowSizes(MAX_LONG_PRESS_BINDINGS, 7, honeycomb = true))
        assertEquals(listOf(3), LongPressPopupManager.rowSizes(3, 3, honeycomb = true))
        assertEquals(listOf(2, 1), LongPressPopupManager.rowSizes(3, 2, honeycomb = true))
    }

    @Test
    fun everyCharacterGetsACell() {
        for (total in 1..MAX_LONG_PRESS_BINDINGS) {
            for (honeycomb in listOf(false, true)) {
                val cols = minOf(7, total)
                assertEquals(total, LongPressPopupManager.rowSizes(total, cols, honeycomb).sum())
            }
        }
    }
}
