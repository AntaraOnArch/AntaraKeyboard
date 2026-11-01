package com.example.antarakeyboard.service

import kotlin.math.roundToInt

/**
 * Key height and row spacing sliders (main app).
 *
 * Key height changes only the drawn key height ([KeyView.heightStretch]); key widths and the
 * spacing between keys and rows stay as tuned. Row spacing adds (or removes) vertical space
 * between rows and touches nothing else.
 */
object KeyScale {
    const val MIN = 0.6f
    const val MAX = 1.5f
    const val DEFAULT = 1f

    /** 3-row portrait triangles are drawn this much taller at 100 % on the slider. */
    const val THREE_ROW_TRIANGLE_DEFAULT = 1.5f

    /** Row spacing slider range in dp (0 = tuned default). */
    const val ROW_SPACING_MIN_DP = -8
    const val ROW_SPACING_MAX_DP = 16

    fun clamp(requested: Float): Float = requested.coerceIn(MIN, MAX)

    fun clampRowSpacing(dp: Int): Int = dp.coerceIn(ROW_SPACING_MIN_DP, ROW_SPACING_MAX_DP)

    /**
     * Honeycomb row overlap for hexagons of height [hexHeight] (at 100 %) stretched by [stretch].
     * A hexagon tip is a quarter of its height; the overlap grows by exactly the tip growth,
     * so neighbouring rows stay interlocked with the same gap.
     */
    fun honeycombOverlap(baseOverlap: Int, hexHeight: Float, stretch: Float): Int =
        (baseOverlap + hexHeight * 0.25f * (stretch - 1f)).roundToInt()
}
