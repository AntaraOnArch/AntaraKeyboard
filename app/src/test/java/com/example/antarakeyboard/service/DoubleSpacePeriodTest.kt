package com.example.antarakeyboard.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleSpacePeriodTest {

    @Test
    fun spaceAfterWordBecomesPeriod() {
        assertTrue(DoubleSpacePeriod.shouldReplace("o "))
        assertTrue(DoubleSpacePeriod.shouldReplace("7 "))
        assertTrue(DoubleSpacePeriod.shouldReplace("ž "))
        assertTrue(DoubleSpacePeriod.shouldReplace(") "))
        assertTrue(DoubleSpacePeriod.shouldReplace("” "))
    }

    @Test
    fun noPeriodAfterPunctuationOrSpaces() {
        assertFalse(DoubleSpacePeriod.shouldReplace(". "))
        assertFalse(DoubleSpacePeriod.shouldReplace(", "))
        assertFalse(DoubleSpacePeriod.shouldReplace("  "))
        assertFalse(DoubleSpacePeriod.shouldReplace("ab"))
        assertFalse(DoubleSpacePeriod.shouldReplace(" "))
        assertFalse(DoubleSpacePeriod.shouldReplace(""))
        assertFalse(DoubleSpacePeriod.shouldReplace(null))
    }
}
