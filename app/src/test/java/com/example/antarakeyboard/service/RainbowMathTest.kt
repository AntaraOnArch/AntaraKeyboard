package com.example.antarakeyboard.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RainbowMathTest {

    @Test
    fun rainbowGoesThroughTheSixColors() {
        assertEquals(0xFFFF0000.toInt(), RainbowMath.rainbowColor(0))       // red
        assertEquals(0xFFFFFF00.toInt(), RainbowMath.rainbowColor(255))     // yellow
        assertEquals(0xFF00FF00.toInt(), RainbowMath.rainbowColor(512))     // green
        assertEquals(0xFF00FFFF.toInt(), RainbowMath.rainbowColor(767))     // cyan
        assertEquals(0xFF0000FF.toInt(), RainbowMath.rainbowColor(1024))    // blue
        assertEquals(0xFFFF00FF.toInt(), RainbowMath.rainbowColor(1279))    // magenta
    }

    @Test
    fun rainbowWrapsAroundIncludingNegativeSteps() {
        assertEquals(RainbowMath.rainbowColor(10), RainbowMath.rainbowColor(10 + RainbowMath.CYCLE))
        assertEquals(RainbowMath.rainbowColor(RainbowMath.CYCLE - 10), RainbowMath.rainbowColor(-10))
    }

    @Test
    fun easingStartsAndEndsFlat() {
        assertEquals(0f, RainbowMath.easeInOutCubic(0f), 1e-6f)
        assertEquals(0.5f, RainbowMath.easeInOutCubic(0.5f), 1e-6f)
        assertEquals(1f, RainbowMath.easeInOutCubic(1f), 1e-6f)
    }

    @Test
    fun interpolationTakesTheShorterWayRound() {
        // 1500 → 100 is 136 steps forward across the wrap, not 1400 back
        assertEquals(1500, RainbowMath.interpolateStep(1500, 100, 0f))
        assertEquals(32, RainbowMath.interpolateStep(1500, 100, 0.5f))
        assertEquals(100, RainbowMath.interpolateStep(1500, 100, 1f))
        assertEquals(300, RainbowMath.interpolateStep(100, 500, 0.5f))
    }
}
