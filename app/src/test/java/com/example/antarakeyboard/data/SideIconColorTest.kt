package com.example.antarakeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SideIconColorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val lightBg = 0xFFFFF7B0.toInt()
    private val darkBg = 0xFF1F3A12.toInt()

    @Test
    fun lightIconOnLightBackgroundTurnsDark() {
        assertEquals(black, KeyboardPrefs.contrastingIconColor(lightBg, white))
    }

    @Test
    fun darkIconOnDarkBackgroundTurnsLight() {
        assertEquals(white, KeyboardPrefs.contrastingIconColor(darkBg, black))
    }

    @Test
    fun contrastingIconStays() {
        val green = 0xFF8EF0A0.toInt()
        assertEquals(green, KeyboardPrefs.contrastingIconColor(darkBg, green))
        assertEquals(black, KeyboardPrefs.contrastingIconColor(lightBg, black))
    }

    @Test
    fun transparentBackgroundKeepsThemeIcon() {
        assertEquals(white, KeyboardPrefs.contrastingIconColor(0x00000000, white))
    }
}
