package com.example.antarakeyboard.service

import com.example.antarakeyboard.model.KeyShape
import kotlin.math.max

/**
 * Tuned key and row sizes of the portrait (and landscape hexagon) layout. Pure: everything the
 * numbers depend on comes in through [Spec], so the sizes are unit-tested.
 */
internal object KeySizing {

    /** Honeycomb rows overlap by this part of the key height. */
    const val OVERLAP_RATIO = 0.18f

    /** What the sizes depend on: stored row count, layout shape, orientation, screen density. */
    data class Spec(
        val rowCount: Int,
        val shape: KeyShape,
        val landscape: Boolean,
        val density: Float
    )

    data class RowSizing(
        val keyW: Int,
        val keyH: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int,
        val triOverlapX: Int,
        val triOverlapY: Int
    )

    /** Same rounding as the dp extensions. */
    private fun Int.dp(spec: Spec): Int = (this * spec.density).toInt()

    /** Tuned key height for [rows] visible rows in [usableH] px of keyboard height. */
    fun keyHeight(spec: Spec, rows: Int, usableH: Int): Int {
        val savedRowCount = spec.rowCount
        val activeShape = spec.shape
        val usableFactor = if (spec.landscape) 0.88f else 0.92f
        val usableForKeys = (usableH * usableFactor).toInt()

        if (!spec.landscape) {
            when (savedRowCount) {
                3 -> {
                    return when(activeShape) {
                        KeyShape.HEX,
                        KeyShape.HEX_TALL,
                        KeyShape.HEX_HALF_LEFT,
                        KeyShape.HEX_HALF_RIGHT -> (usableForKeys / 3.15f).toInt().coerceAtLeast(52.dp(spec))

                        KeyShape.TRIANGLE -> (usableForKeys / 3.35f).toInt().coerceAtLeast(48.dp(spec))
                        KeyShape.CIRCLE -> (usableForKeys / 3.30f).toInt().coerceAtLeast(50.dp(spec))
                        KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> (usableForKeys / 3.30f).toInt().coerceAtLeast(50.dp(spec))
                    }
                }

                4 -> {
                    return when (activeShape) {
                        KeyShape.HEX,
                        KeyShape.HEX_TALL,
                        KeyShape.HEX_HALF_LEFT,
                        KeyShape.HEX_HALF_RIGHT -> (usableForKeys / 4.05f).toInt().coerceAtLeast(44.dp(spec))

                        KeyShape.TRIANGLE -> (usableForKeys / 4.25f).toInt().coerceAtLeast(42.dp(spec))
                        KeyShape.CIRCLE -> (usableForKeys / 4.20f).toInt().coerceAtLeast(43.dp(spec))
                        KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> (usableForKeys / 4.20f).toInt().coerceAtLeast(43.dp(spec))
                    }
                }
            }
        }

        val denominator = when (activeShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> (rows - (rows - 1) * OVERLAP_RATIO).coerceAtLeast(1f)

            KeyShape.TRIANGLE -> rows.toFloat()
            KeyShape.CIRCLE -> rows.toFloat()
            KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> rows.toFloat()
        }

        val baseH = (usableForKeys / denominator).toInt().coerceAtLeast(
            if (spec.landscape) 28.dp(spec) else 36.dp(spec)
        )

        return when (activeShape){
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> (baseH * 1.08f).toInt()

            KeyShape.TRIANGLE -> if (spec.landscape) {
                (baseH * 0.84f).toInt()
            } else {
                (baseH * 0.92f).toInt()
            }

            KeyShape.CIRCLE -> if (spec.landscape) {
                (baseH * 0.88f).toInt()
            } else {
                (baseH * 0.96f).toInt()
            }

            KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> if (spec.landscape) {
                (baseH * 0.88f).toInt()
            } else {
                (baseH * 0.96f).toInt()
            }
        }
    }

    /**
     * Tuned row sizing for a row of [count] keys in [availW] px, before the key height slider.
     * [keyHeight] is only asked for when the shape needs it.
     */
    fun baseRowSizing(spec: Spec, count: Int, availW: Int, keyHeight: () -> Int): RowSizing {
        val savedRowCount = spec.rowCount
        val layoutShape = spec.shape

        /*
         * Portrait TRIANGLE: every key gets the size of the reference row with 6 keys.
         * A row with 7 keys overlaps horizontally to fit the same width without
         * shrinking the keys.
         */
        if (!spec.landscape && layoutShape == KeyShape.TRIANGLE) {
            val usableW = when (savedRowCount) {
                // Leave a little safe space at the left and right edge
                3 -> (availW - 20.dp(spec)).coerceAtLeast(240.dp(spec))

                // 4-row and 5-row (tuned)
                else -> (availW * 0.98f).toInt()
            }

            val referenceColumns = when (savedRowCount) {
                // 3-row has 11–12 keys per row, so it needs smaller triangles
                3 -> 8.4f

                // 4-row and 5-row sizing
                else -> 6f
            }

            val minTriangleWidth = when (savedRowCount) {
                3 -> 30.dp(spec)
                else -> 40.dp(spec)
            }

            val keyW = (usableW / referenceColumns)
                .toInt()
                .coerceAtLeast(minTriangleWidth)

            /*
             * 7 keys: 7 * keyW is too wide, so the excess is spread over the six gaps
             * between the keys.
             */
            // A small constant overlap applies to rows with 6 keys too.
            val baseOverlapX = (keyW * 0.22f).toInt()

            // Extra overlap needed for a row with 7 keys to fit.
            val fitOverlapX = if (count > 1) {
                ((count * keyW - usableW) / (count - 1))
                    .coerceAtLeast(0)
            } else {
                0
            }

            val overlapX = when (savedRowCount) {
                3 -> {
                    /*
                     * No more subtracting 2.dp(spec): it widened the whole row and pushed
                     * the outer keys off the screen.
                     */
                    maxOf(baseOverlapX, fitOverlapX)
                }

                else -> {
                    maxOf(baseOverlapX, fitOverlapX) + 2.dp(spec)
                }
            }

            val usedW =
                count * keyW -
                        (count - 1).coerceAtLeast(0) * overlapX

            val outerPad = ((availW - usedW) / 2)
                .coerceAtLeast(0)

            return RowSizing(
                keyW = keyW,
                keyH = (keyW * 0.92f).toInt(),
                gapPx = 0,
                outerPadPx = outerPad,
                overlapPx = 0,
                triOverlapX = overlapX,
                triOverlapY = 0
            )
        }

        val gap = when (layoutShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when {
                    spec.landscape -> 0.dp(spec)
                    savedRowCount == 3 -> 0.dp(spec)
                    savedRowCount == 4 -> 0.dp(spec)
                    else -> 1.dp(spec)
                }
            }

            KeyShape.TRIANGLE -> 0.dp(spec)
            KeyShape.CIRCLE -> {
                when {
                    spec.landscape -> 2.dp(spec)
                    savedRowCount == 3 -> 2.dp(spec)
                    else -> 4.dp(spec)
                }
            }
            KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> {
                when {
                    spec.landscape -> 2.dp(spec)

                    // 3-row portrait: less empty space, so the keys themselves
                    // can be bigger
                    savedRowCount == 3 -> 2.dp(spec)

                    else -> 4.dp(spec)
                }
            }
        }

        val effectiveAvailW = when (layoutShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when {
                    spec.landscape -> {
                        if (savedRowCount == 3) {
                            availW
                        } else {
                            (availW * 1.22f).toInt()
                        }
                    }

                    savedRowCount == 3 -> (availW * 0.95f).toInt()
                    savedRowCount == 4 -> (availW * 0.94f).toInt()
                    else -> availW
                }
            }

            // portrait triangles returned above
            KeyShape.TRIANGLE -> (availW * 0.92f).toInt()
            KeyShape.CIRCLE -> {
                when {
                    spec.landscape -> availW
                    savedRowCount == 3 -> (availW * 0.98f).toInt()
                    else -> (availW * 0.92f).toInt()
                }
            }
            KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> {
                when {
                    spec.landscape -> availW

                    // 3-row portrait: bigger keys
                    savedRowCount == 3 -> (availW * 0.98f).toInt()

                    else -> (availW * 0.92f).toInt()
                }
            }
        }

        val targetColumns = when {
            layoutShape == KeyShape.HEX && spec.landscape && savedRowCount == 3 -> 11f
            layoutShape == KeyShape.HEX && spec.landscape -> 7f
            savedRowCount == 3 -> 8.8f
            layoutShape == KeyShape.HEX && savedRowCount == 4 -> 8.9f
            layoutShape == KeyShape.HEX -> 7f
            else -> max(1, count).toFloat()
        }

        val minKeyWidth = when {
            spec.landscape && savedRowCount == 3 -> 24.dp(spec)
            spec.landscape -> 40.dp(spec)
            savedRowCount == 3 -> 20.dp(spec)
            savedRowCount == 4 -> 24.dp(spec)
            else -> 36.dp(spec)
        }

        val baseKeyW = ((effectiveAvailW - (targetColumns - 1) * gap) / targetColumns)
            .toInt()
            .coerceAtLeast(minKeyWidth)

        val keyW = when {
            // Portrait CIRCLE, CUBE and TRIANGLE: a row with 6 keys uses the same key
            // size as a row with 7 keys
            !spec.landscape &&
                    count == 6 &&
                    (
                            layoutShape == KeyShape.CIRCLE ||
                                    layoutShape == KeyShape.CUBE
                            ) -> {
                ((effectiveAvailW - 6 * gap) / 7f)
                    .toInt()
                    .coerceAtLeast(36.dp(spec))
            }

            layoutShape == KeyShape.HEX &&
                    spec.landscape &&
                    savedRowCount == 3 -> baseKeyW

            layoutShape == KeyShape.HEX &&
                    spec.landscape &&
                    count <= 6 -> {
                (baseKeyW * 1.06f).toInt()
            }

            layoutShape == KeyShape.HEX &&
                    spec.landscape -> baseKeyW

            savedRowCount == 3 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(24.dp(spec))
            }

            savedRowCount == 4 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(22.dp(spec))
            }

            count == 7 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(36.dp(spec))
            }

            count == 6 -> baseKeyW

            else -> {
                ((effectiveAvailW - (count - 1) * gap) / max(1, count).toFloat())
                    .toInt()
                    .coerceAtLeast(36.dp(spec))
            }
        }

        val used = count * keyW + (count - 1) * gap

        val outer = when {
            spec.landscape && layoutShape == KeyShape.HEX -> {
                ((availW - used) / 2).coerceAtLeast(4.dp(spec))
            }

            spec.landscape -> {
                ((availW - used) / 2).coerceAtLeast(6.dp(spec))
            }

            savedRowCount == 3 -> {
                ((availW - used) / 2).coerceAtLeast(0)
            }

            savedRowCount == 4 -> {
                ((availW - used) / 2).coerceAtLeast(0)
            }

            else -> {
                ((availW - used) / 2).coerceAtLeast(0)
            }
        }

        val rawKeyH = keyHeight()

        val keyH = when {
            // 3-row portrait CUBE: the width stays, only the height grows
            !spec.landscape &&
                    savedRowCount == 3 &&
                    layoutShape == KeyShape.CUBE -> {
                // Height is 1.3 × the key width, not derived from the row's
                // computed height.
                (keyW * 1.3f).toInt()
            }

            // 3-row portrait HEX
            !spec.landscape &&
                    savedRowCount == 3 &&
                    (
                            layoutShape == KeyShape.HEX ||
                                    layoutShape == KeyShape.HEX_HALF_LEFT ||
                                    layoutShape == KeyShape.HEX_HALF_RIGHT
                            ) -> {
                (keyW * 1.90f).toInt().coerceAtLeast(42.dp(spec))
            }

            else -> rawKeyH
        }

        val overlap = when (spec.shape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                val ratio = when {
                    spec.landscape -> OVERLAP_RATIO * 0.6f
                    savedRowCount == 3 -> OVERLAP_RATIO * 1.58f
                    savedRowCount == 4 -> OVERLAP_RATIO * 1.00f
                    else -> OVERLAP_RATIO
                }
                (keyH * ratio).toInt()
            }

            else -> 0
        }

        return RowSizing(
            keyW = keyW,
            keyH = keyH,
            gapPx = gap,
            outerPadPx = outer,
            overlapPx = overlap,
            triOverlapX = 0,
            triOverlapY = 0
        )
    }
}
