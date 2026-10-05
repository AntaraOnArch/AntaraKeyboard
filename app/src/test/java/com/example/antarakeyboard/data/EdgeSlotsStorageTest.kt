package com.example.antarakeyboard.data

import com.example.antarakeyboard.model.EdgeActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSlotsStorageTest {

    @Test
    fun slotCountFollowsRowCount() {
        assertEquals(3, EdgeSlotsStorage.defaultSlots(3).size)
        assertEquals(4, EdgeSlotsStorage.defaultSlots(4).size)
        assertEquals(6, EdgeSlotsStorage.defaultSlots(5).size)
    }

    @Test
    fun unknownRowCountFallsBackToFiveRows() {
        assertEquals(6, EdgeSlotsStorage.defaultSlots(7).size)
    }

    @Test
    fun defaultsContainShiftAndBackspaceOnce() {
        listOf(3, 4, 5).forEach { rows ->
            val types = EdgeSlotsStorage.defaultSlots(rows).map { it.type }
            assertEquals(1, types.count { it == EdgeActionType.SHIFT })
            assertEquals(1, types.count { it == EdgeActionType.BACKSPACE })
        }
    }

    @Test
    fun indicesAreSequential() {
        listOf(3, 4, 5).forEach { rows ->
            val slots = EdgeSlotsStorage.defaultSlots(rows)
            assertTrue(slots.map { it.index } == slots.indices.toList())
        }
    }
}
