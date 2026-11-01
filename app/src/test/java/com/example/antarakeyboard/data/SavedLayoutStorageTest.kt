package com.example.antarakeyboard.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedLayoutStorageTest {

    private val gson = Gson()

    private fun layout(keyScale: Float?, rowSpacingDp: Int?) = SavedLayoutStorage.SavedLayout(
        id = 1L, name = "Test", timestamp = 1L, rowCount = 4, keyShape = "HEX",
        alphabetLayoutJson = "{}", numericLayoutJson = "{}", horizontalCenterLayoutJson = "{}",
        edgeSlotsJson = "[]", keyFill = 1, keyText = 2, backgroundColor = 3,
        backgroundUseTheme = false, space1Bg = 4, space2Bg = 5, spaceLinked = true,
        enterBg = 6, enterIcon = 7, sideBg = 8, sideText = 9, sideUseTheme = true,
        keyScale = keyScale, rowSpacingDp = rowSpacingDp
    )

    @Test
    fun keySizingRoundTrips() {
        val json = gson.toJson(layout(1.25f, -3))
        val restored = gson.fromJson(json, SavedLayoutStorage.SavedLayout::class.java)
        assertEquals(1.25f, restored.keyScale!!, 0.0001f)
        assertEquals(-3, restored.rowSpacingDp)
    }

    @Test
    fun layoutsSavedBeforeTheSlidersHaveNoValues() {
        // JSON of an older layout: the two fields don't exist yet
        val old = gson.toJson(layout(null, null))
            .replace(",\"keyScale\":null", "")
            .replace(",\"rowSpacingDp\":null", "")
        val restored = gson.fromJson(old, SavedLayoutStorage.SavedLayout::class.java)
        assertNull(restored.keyScale)
        assertNull(restored.rowSpacingDp)
        assertEquals("Test", restored.name)
    }
}
