package com.example.antarakeyboard.service

import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeBindingsTest {

    private fun slot(index: Int, side: EdgePos.Side, type: EdgeActionType) = EdgeSlot(index, side, type)

    @Test
    fun fiveRowsPairSlotsPerSideButtonRow() {
        val slots = listOf(
            slot(0, EdgePos.Side.LEFT, EdgeActionType.SHIFT),
            slot(1, EdgePos.Side.RIGHT, EdgeActionType.BACKSPACE),
            slot(2, EdgePos.Side.LEFT, EdgeActionType.NONE),
            slot(5, EdgePos.Side.RIGHT, EdgeActionType.ENTER)
        )
        val bindings = EdgeOverlayManager.edgeBindings(5, slots)
        assertEquals(
            listOf(0 to EdgePos.Side.LEFT, 0 to EdgePos.Side.RIGHT, 2 to EdgePos.Side.RIGHT),
            bindings.map { it.visualIndex to it.side }
        )
        assertEquals(listOf(0, 2, 4), EdgeOverlayManager.sideButtonKeyRows(5))
    }

    @Test
    fun fourRowsAlternateRightAndLeft() {
        val slots = (0 until 6).map { slot(it, EdgePos.Side.LEFT, EdgeActionType.CHAR) }
        val bindings = EdgeOverlayManager.edgeBindings(4, slots)
        assertEquals(
            listOf(EdgePos.Side.RIGHT, EdgePos.Side.LEFT, EdgePos.Side.RIGHT, EdgePos.Side.LEFT),
            bindings.map { it.side }
        )
        assertEquals(listOf(0, 1, 2, 3), bindings.map { it.visualIndex })
        // The slot takes the side it is drawn on
        assertEquals(bindings.map { it.side }, bindings.map { it.slot.side })
    }

    @Test
    fun threeRowsSkipEmptySlots() {
        val slots = listOf(
            slot(0, EdgePos.Side.LEFT, EdgeActionType.NONE),
            slot(1, EdgePos.Side.RIGHT, EdgeActionType.SHIFT),
            slot(2, EdgePos.Side.LEFT, EdgeActionType.BACKSPACE)
        )
        val bindings = EdgeOverlayManager.edgeBindings(3, slots)
        assertEquals(listOf(1 to EdgePos.Side.LEFT, 2 to EdgePos.Side.RIGHT), bindings.map { it.visualIndex to it.side })
    }
}
