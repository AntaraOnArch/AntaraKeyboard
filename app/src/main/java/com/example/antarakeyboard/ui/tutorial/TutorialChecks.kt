package com.example.antarakeyboard.ui.tutorial

/**
 * Tutorial practice steps and how each one is recognized from the practice field's text (the app
 * only sees text, not gestures). Pure, unit-tested in TutorialChecksTest.
 */
enum class TutorialStep { CAPITALS, CAPS_LOCK, PUNCTUATION, RESTORE, LONG_PRESS, EMOJI }

object TutorialChecks {

    /** Capitals typed with a swipe up (Shift gives them too – both count). */
    const val CAPITALS_NEEDED = 2

    /** Punctuation step: this many commas / exclamation marks in all. */
    const val PUNCTUATION_NEEDED = 2

    /** Restore step: type something, delete it, bring it back – this many times. */
    const val RESTORE_ROUNDS = 3

    /** Shift step: this many capitals in all, however typed (Shift or swipe up), in a row or not (the number is in tutorial_caps_text). */
    const val CAPS_NEEDED = 5

    /**
     * True when [text] completes [step]. [restoreRounds] the delete → restore rounds of the Restore step
     * ([RestoreCounter]), [longPressLetters] the letters only a long press types.
     */
    fun isDone(
        step: TutorialStep,
        text: String,
        restoreRounds: Int = 0,
        longPressLetters: Set<String> = emptySet()
    ): Boolean = when (step) {
        TutorialStep.CAPITALS -> text.count { it.isUpperCase() } >= CAPITALS_NEEDED
        TutorialStep.CAPS_LOCK -> text.count { it.isUpperCase() } >= CAPS_NEEDED
        // Two good swipe-ups (on . → comma, on ? → !), any mix; missed keys in between don't matter
        TutorialStep.PUNCTUATION -> text.count { it == ',' || it == '!' } >= PUNCTUATION_NEEDED
        TutorialStep.RESTORE -> restoreRounds >= RESTORE_ROUNDS
        TutorialStep.LONG_PRESS -> codePoints(text).any { it in longPressLetters }
        TutorialStep.EMOJI -> text.codePoints().anyMatch { isEmoji(it) }
    }

    /** Long-press entries that are single letters the keys don't type themselves. */
    fun longPressLetters(bindings: Collection<String>, keyOutputs: Collection<String>): Set<String> {
        val plain = keyOutputs.flatMap { listOf(it.lowercase(), it.uppercase()) }.toSet()
        return bindings
            .filter { codePoints(it).size == 1 && Character.isLetter(it.codePointAt(0)) }
            .flatMap { listOf(it, it.uppercase()) }
            .filter { it !in plain }
            .toSet()
    }

    fun isEmoji(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF

    private fun codePoints(text: String): List<String> =
        text.codePoints().toArray().map { String(Character.toChars(it)) }
}

/**
 * Counts the Restore step's rounds from the field's text changes: text that got shorter (deleted)
 * and then is exactly what it was before the deletion again (restored) is one round. Typing
 * something else in between starts over. (Typing the same letters again by hand also counts – the
 * app only sees text.)
 */
class RestoreCounter {

    var rounds = 0
        private set
    private var last = ""
    /** The text right before the current deletion, null while nothing is deleted. */
    private var beforeDelete: String? = null

    fun reset(text: String, rounds: Int = 0) {
        this.rounds = rounds
        last = text
        beforeDelete = null
    }

    fun onText(text: String) {
        val before = beforeDelete
        when {
            before != null && text == before -> {
                rounds++
                beforeDelete = null
            }
            text.length < last.length -> if (before == null) beforeDelete = last
            before != null && !before.startsWith(text) -> beforeDelete = null
        }
        last = text
    }
}
