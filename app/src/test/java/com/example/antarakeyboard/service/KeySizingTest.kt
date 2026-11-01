package com.example.antarakeyboard.service

import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeySizingTest {

    // A common phone: 1080 px wide at 2.75 density, a little padding on each side
    private val density = 2.75f
    private val availW = 1060
    private val usableH = 700

    private val layouts = mapOf(
        3 to defaultThreeRowKeyboardLayoutQwertz,
        4 to defaultFourRowKeyboardLayout,
        5 to defaultKeyboardLayout
    )
    private val shapes = listOf(KeyShape.HEX, KeyShape.TRIANGLE, KeyShape.CIRCLE, KeyShape.CUBE)

    private fun spec(rows: Int, shape: KeyShape) = KeySizing.Spec(rows, shape, landscape = false, density = density)

    private fun sizing(rows: Int, shape: KeyShape, count: Int) =
        KeySizing.baseRowSizing(spec(rows, shape), count, availW) {
            KeySizing.keyHeight(spec(rows, shape), rows, usableH)
        }

    @Test
    fun everyDefaultPortraitRowFitsTheWidth() {
        layouts.forEach { (rows, layout) ->
            shapes.forEach { shape ->
                layout.rows.forEach { row ->
                    val count = row.keys.size
                    val s = sizing(rows, shape, count)
                    val used = count * s.keyW + (count - 1) * (s.gapPx - s.triOverlapX)
                    assertTrue("$rows rows $shape: keyW ${s.keyW}", s.keyW > 0 && s.keyH > 0)
                    assertTrue("$rows rows $shape, $count keys: $used > $availW", used <= availW)
                }
            }
        }
    }

    @Test
    fun onlyHexagonRowsOverlapVertically() {
        listOf(3, 4, 5).forEach { rows ->
            shapes.forEach { shape ->
                val overlap = sizing(rows, shape, 7).overlapPx
                if (shape == KeyShape.HEX) assertTrue("$rows rows", overlap > 0)
                else assertEquals("$rows rows $shape", 0, overlap)
            }
        }
    }

    @Test
    fun portraitTrianglesNeverAskForTheKeyHeight() {
        listOf(3, 4, 5).forEach { rows ->
            val s = KeySizing.baseRowSizing(spec(rows, KeyShape.TRIANGLE), 7, availW) {
                error("key height not needed")
            }
            assertEquals((s.keyW * 0.92f).toInt(), s.keyH)
        }
    }

    @Test
    fun keyHeightShrinksWithMoreRows() {
        shapes.forEach { shape ->
            val five = KeySizing.keyHeight(spec(5, shape), 5, usableH)
            val six = KeySizing.keyHeight(spec(5, shape), 6, usableH)
            assertTrue("$shape: $six >= $five", six < five)
        }
    }
}
