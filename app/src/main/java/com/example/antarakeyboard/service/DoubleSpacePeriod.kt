package com.example.antarakeyboard.service

/**
 * Double space → period: a space typed right after "word " turns that space into ". ".
 * Pure, unit-tested in DoubleSpacePeriodTest.
 */
object DoubleSpacePeriod {

    /** Closing marks a sentence may end with before the period, e.g. (word) or "word". */
    private const val CLOSERS = ")]}\"'”’»"

    /** True when a space typed after [before] (the two characters before the cursor) becomes ". ". */
    fun shouldReplace(before: CharSequence?): Boolean {
        if (before == null || before.length < 2) return false
        val last = before[before.length - 1]
        val prev = before[before.length - 2]
        return last == ' ' && (prev.isLetterOrDigit() || prev in CLOSERS)
    }
}
