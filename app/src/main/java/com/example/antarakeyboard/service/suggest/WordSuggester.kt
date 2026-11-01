package com.example.antarakeyboard.service.suggest

import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity
import com.darkrockstudios.symspellkt.impl.SymSpell

/**
 * Word suggestions for the word being typed: completions ("prij" → "prijatelj") from a
 * frequency list, plus spelling corrections ("prijatlj" → "prijatelj") via SymSpell.
 * Pure Kotlin (no Android), built once per language. Nothing the user types is stored.
 *
 * @param entries words with their frequency, lowercase
 */
class WordSuggester(entries: Map<String, Long>) {

    private val frequency: Map<String, Long> = entries
    private val sorted: Array<String> = entries.keys.sorted().toTypedArray()

    // maxEditDistance 2 with a 5-letter prefix: catches two typos at a third of the memory
    // of the default settings (measured: ~18 MB for 50k words)
    private val symSpell = SymSpell(
        SpellCheckSettings(maxEditDistance = 2.0, prefixLength = 5, topK = 5)
    ).also { spell ->
        entries.forEach { (word, count) -> spell.createDictionaryEntry(word, count.toDouble()) }
    }

    /** Up to [max] suggestions for [typed], in the typed word's capitalization. */
    fun suggest(typed: String, max: Int = 3): List<String> {
        if (typed.isEmpty()) return emptyList()
        val lower = typed.lowercase()
        val known = lower in frequency

        val corrections = if (known || lower.length < 3) {
            emptyList()
        } else {
            val distance = if (lower.length <= 4) 1.0 else 2.0
            runCatching { symSpell.lookup(lower, Verbosity.Closest, distance) }
                .getOrDefault(emptyList())
                .map { it.term }
                .filter { it != lower }
        }

        val result = LinkedHashSet<String>()
        corrections.take(2).forEach { result.add(it) }
        completions(lower, max).forEach { if (result.size < max) result.add(it) }
        corrections.drop(2).forEach { if (result.size < max) result.add(it) }
        return result.take(max).map { matchCase(typed, it) }
    }

    /** Most frequent words that start with [prefix] (longer than it). */
    private fun completions(prefix: String, max: Int): List<String> {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
        }

        val best = ArrayList<String>(max + 1)
        var i = lo
        var scanned = 0
        while (i < sorted.size && scanned < MAX_PREFIX_SCAN) {
            val word = sorted[i]
            if (!word.startsWith(prefix)) break
            if (word.length > prefix.length) {
                best.add(word)
                best.sortByDescending { frequency[it] ?: 0L }
                if (best.size > max) best.removeAt(best.lastIndex)
            }
            i++
            scanned++
        }
        return best
    }

    companion object {
        // Very short prefixes match thousands of words; the list is long enough to stop early
        private const val MAX_PREFIX_SCAN = 20_000

        /** "Prij" → "Prijatelj", "PRIJ" → "PRIJATELJ", otherwise unchanged. */
        fun matchCase(typed: String, word: String): String = when {
            typed.length > 1 && typed == typed.uppercase() && typed != typed.lowercase() ->
                word.uppercase()
            typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }

        /** Parses "<word> <count>" lines (most frequent first). */
        fun parse(lines: Sequence<String>): Map<String, Long> {
            val out = LinkedHashMap<String, Long>()
            lines.forEach { line ->
                val space = line.indexOf(' ')
                if (space > 0) {
                    val count = line.substring(space + 1).trim().toLongOrNull()
                    if (count != null) out[line.substring(0, space)] = count
                }
            }
            return out
        }

        /**
         * The word being typed: letters (and an inner apostrophe) right before the cursor.
         * [after] is the text right after the cursor; inside a word there is nothing to suggest.
         */
        fun currentWord(before: CharSequence, after: CharSequence): String {
            if (after.isNotEmpty() && after[0].isLetter()) return ""
            var start = before.length
            while (start > 0) {
                val ch = before[start - 1]
                val inWord = ch.isLetter() ||
                    (ch == '\'' && start - 2 >= 0 && before[start - 2].isLetter() && start < before.length)
                if (!inWord) break
                start--
            }
            return before.substring(start)
        }
    }
}
