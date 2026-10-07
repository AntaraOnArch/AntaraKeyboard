package com.example.antarakeyboard.service

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyScaleTest {

    @Test
    fun honeycombOverlapGrowsWithHexTips() {
        // 100 px hexagon, tips 25 px; at 140 % the tips grow by 10 px
        assertEquals(30, KeyScale.honeycombOverlap(20, 100f, 1.4f))
        // shorter hexagons need less overlap
        assertEquals(10, KeyScale.honeycombOverlap(20, 100f, 0.6f))
        assertEquals(20, KeyScale.honeycombOverlap(20, 100f, 1f))
    }

    @Test
    fun slidersAreClampedToTheirRanges() {
        assertEquals(KeyScale.MIN, KeyScale.clamp(0.1f), 0.0001f)
        assertEquals(KeyScale.MAX, KeyScale.clamp(5f), 0.0001f)
        assertEquals(KeyScale.ROW_SPACING_MIN_DP, KeyScale.clampRowSpacing(-100))
        assertEquals(KeyScale.ROW_SPACING_MAX_DP, KeyScale.clampRowSpacing(100))
        assertEquals(0, KeyScale.clampRowSpacing(0))
    }
}
