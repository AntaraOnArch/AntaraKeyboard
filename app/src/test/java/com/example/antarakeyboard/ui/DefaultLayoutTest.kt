package com.example.antarakeyboard.ui

import com.example.antarakeyboard.model.KeyboardConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec: the numeric layout must always contain ABC, 0–9 and Enter (protected keys). */
class DefaultLayoutTest {

    private fun labels(cfg: KeyboardConfig) =
        (cfg.specialLeft + cfg.rows.flatMap { it.keys } + cfg.specialRight).map { it.label }

    private val protectedNumericKeys = (0..9).map { it.toString() } + "↵"

    @Test
    fun numericLayoutsContainProtectedKeys() {
        listOf(
            "3 rows" to defaultThreeRowNumericLayout,
            "4 rows" to defaultFourRowNumericLayout,
            "5 rows" to defaultNumericLayout
        ).forEach { (name, layout) ->
            val present = labels(layout)
            protectedNumericKeys.forEach { key ->
                assertTrue("$name numeric layout is missing '$key'", key in present)
            }
            assertTrue(
                "$name numeric layout is missing the back-to-letters key",
                present.any { it.equals("abc", ignoreCase = true) }
            )
        }
    }

    @Test
    fun alphabetLayoutsContainEnterAnd123() {
        listOf(
            "3 rows" to defaultThreeRowKeyboardLayoutQwertz,
            "4 rows" to defaultFourRowKeyboardLayout,
            "5 rows" to defaultKeyboardLayout
        ).forEach { (name, layout) ->
            val present = labels(layout)
            assertTrue("$name alphabet layout is missing Enter", "↵" in present)
            assertTrue("$name alphabet layout is missing 123", "123" in present)
        }
    }
}
