package com.example.antarakeyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardConfigTest {

    private fun spaces(cfg: KeyboardConfig) =
        (cfg.specialLeft + cfg.rows.flatMap { it.keys } + cfg.specialRight).filter { it.label == " " }

    @Test
    fun ensureSpaceMarkersMarksFirstSpaceLeftAndOthersRight() {
        val cfg = KeyboardConfig(
            rows = mutableListOf(
                RowConfig(mutableListOf(KeyConfig("a"), KeyConfig(" "), KeyConfig(" ")))
            )
        ).ensureSpaceMarkers()

        val (left, right) = spaces(cfg)
        assertTrue(left.isSpaceLeftMarked())
        assertTrue(right.isSpaceRightMarked())
    }

    @Test
    fun ensureSpaceMarkersKeepsExistingMarkers() {
        val cfg = KeyboardConfig(
            rows = mutableListOf(
                RowConfig(mutableListOf(
                    KeyConfig(" ", mutableListOf(KeyMarkers.SPACE_RIGHT)),
                    KeyConfig(" ", mutableListOf(KeyMarkers.SPACE_LEFT))
                ))
            )
        ).ensureSpaceMarkers()

        val (first, second) = spaces(cfg)
        assertTrue(first.isSpaceRightMarked())
        assertTrue(second.isSpaceLeftMarked())
        assertEquals(1, first.longPressBindings.size)
    }

    @Test
    fun addLongPressDoesNotDuplicate() {
        val cfg = KeyboardConfig(rows = mutableListOf(RowConfig(mutableListOf(KeyConfig("c")))))
        cfg.addLongPress("c", "č")
        cfg.addLongPress("c", "č")
        cfg.addLongPress("missing", "x")
        assertEquals(listOf("č"), cfg.findKey("c")?.longPressBindings)
    }
}
