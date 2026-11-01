package com.example.antarakeyboard.data

import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.RowConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalLongPressStorageTest {

    private fun layout(vararg keys: Pair<String, List<String>>) = KeyboardConfig(
        rows = mutableListOf(RowConfig(keys.map { (label, binds) -> KeyConfig(label, binds.toMutableList()) }.toMutableList())),
        specialLeft = mutableListOf(),
        specialRight = mutableListOf()
    )

    private fun KeyboardConfig.binds(label: String) = rows.flatMap { it.keys }.first { it.label == label }.longPressBindings

    @Test
    fun staleSnapshotInStoredLayoutIsIgnored() {
        // Stored layout still has Czech á on a; the Croatian global binds have nothing there
        val stored = layout("a" to listOf("á"), "c" to listOf("č"), "." to emptyList())
        val factory = layout("a" to emptyList(), "c" to emptyList(), "." to listOf(",", ":"))
        val global = mapOf("c" to listOf("č", "ć"))

        val result = GlobalLongPressStorage.applyBindsToLayout(stored, global, factory)
        assertEquals(emptyList<String>(), result.binds("a"))
        assertEquals(listOf("č", "ć"), result.binds("c"))
        assertEquals("missing key gets the factory binds", listOf(",", ":"), result.binds("."))
    }

    @Test
    fun explicitlyClearedKeyStaysEmpty() {
        val factory = layout("." to listOf(",", ":"))
        val result = GlobalLongPressStorage.applyBindsToLayout(layout("." to emptyList()), mapOf("." to emptyList()), factory)
        assertEquals(emptyList<String>(), result.binds("."))
    }

    @Test
    fun markersOnBlankKeysAreKept() {
        val stored = layout(" " to listOf(KeyMarkers.SPACE_LEFT), "" to listOf(KeyMarkers.USER_EMPTY))
        val result = GlobalLongPressStorage.applyBindsToLayout(stored, mapOf("a" to listOf("á")), layout())
        assertEquals(listOf(KeyMarkers.SPACE_LEFT), result.binds(" "))
        assertEquals(listOf(KeyMarkers.USER_EMPTY), result.binds(""))
    }

    @Test
    fun extractKeepsEmptyKeysAndSkipsBlankLabels() {
        val extracted = GlobalLongPressStorage.extractBindsFromLayout(
            layout("a" to emptyList(), "c" to listOf("č"), " " to listOf(KeyMarkers.SPACE_LEFT))
        )
        assertEquals(mapOf("a" to emptyList(), "c" to listOf("č")), extracted)
    }

    @Test
    fun mergeDefaultsKeepsKeysTheUserCleared() {
        val merged = LongPressPresets.mergeDefaults(
            current = mapOf("." to emptyList(), "c" to listOf("č", "ć")),
            oldDefaults = mapOf("c" to listOf("č", "ć")),
            newDefaults = mapOf("a" to listOf("á"))
        )
        assertEquals(mapOf("." to emptyList(), "a" to listOf("á")), merged)
    }
}
