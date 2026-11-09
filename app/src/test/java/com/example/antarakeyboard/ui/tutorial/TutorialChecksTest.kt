package com.example.antarakeyboard.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialChecksTest {

    private fun done(step: TutorialStep, text: String, rounds: Int = 0, letters: Set<String> = emptySet()) =
        TutorialChecks.isDone(step, text, rounds, letters)

    @Test
    fun capitalsNeedTwo() {
        assertFalse(done(TutorialStep.CAPITALS, "Ab"))
        assertTrue(done(TutorialStep.CAPITALS, "AB"))
        assertTrue(done(TutorialStep.CAPITALS, "Ana Ivo"))
    }

    @Test
    fun capsLockNeedsFiveCapitalsAnywhere() {
        assertFalse(done(TutorialStep.CAPS_LOCK, "OK"))
        assertFalse(done(TutorialStep.CAPS_LOCK, "ABcD"))
        assertTrue(done(TutorialStep.CAPS_LOCK, "AbCdEfGH"))
        assertTrue(done(TutorialStep.CAPS_LOCK, "OK OKA"))
    }

    @Test
    fun punctuation() {
        assertFalse(done(TutorialStep.PUNCTUATION, "a."))
        assertFalse(done(TutorialStep.PUNCTUATION, "a,"))
        assertFalse(done(TutorialStep.PUNCTUATION, "!?."))
        assertTrue(done(TutorialStep.PUNCTUATION, ",!"))
        assertTrue(done(TutorialStep.PUNCTUATION, ",,"))
        assertTrue(done(TutorialStep.PUNCTUATION, "!x?!"))
        // Missed keys in between don't matter
        assertTrue(done(TutorialStep.PUNCTUATION, "a,.?k,"))
    }

    @Test
    fun restoreNeedsThreeRounds() {
        assertFalse(done(TutorialStep.RESTORE, "a", rounds = 2))
        assertTrue(done(TutorialStep.RESTORE, "a", rounds = 3))
    }

    private fun RestoreCounter.type(vararg texts: String) = texts.forEach { onText(it) }

    @Test
    fun restoreCounterCountsDeleteThenRestore() {
        val counter = RestoreCounter().apply { reset("") }
        counter.type("a", "", "a")
        assertEquals(1, counter.rounds)
        // Typed and restored again, a longer text deleted bit by bit and restored at once
        counter.type("ab", "a", "ab")
        counter.type("ab!", "ab", "a", "", "ab!")
        assertEquals(3, counter.rounds)
    }

    @Test
    fun restoreCounterIgnoresOtherTyping() {
        val counter = RestoreCounter().apply { reset("") }
        counter.type("a", "", "b")
        assertEquals(0, counter.rounds)
        // Just typing, or a partial restore, is no round
        counter.type("bc", "bcd", "bc", "bcx")
        assertEquals(0, counter.rounds)
        counter.type("bc", "bcx")
        assertEquals(1, counter.rounds)
    }

    @Test
    fun longPressLettersSkipPlainKeysAndText() {
        val letters = TutorialChecks.longPressLetters(
            bindings = listOf("č", "ć", "c", "@", "hello@mail.com", "😀"),
            keyOutputs = listOf("c", "v")
        )
        assertEquals(setOf("č", "Č", "ć", "Ć"), letters)
        assertTrue(done(TutorialStep.LONG_PRESS, "Ćao", letters = letters))
        assertFalse(done(TutorialStep.LONG_PRESS, "cao", letters = letters))
    }

    @Test
    fun emoji() {
        assertTrue(done(TutorialStep.EMOJI, "hi 😀"))
        assertTrue(done(TutorialStep.EMOJI, "❤"))
        assertFalse(done(TutorialStep.EMOJI, "hi :)"))
    }
}
