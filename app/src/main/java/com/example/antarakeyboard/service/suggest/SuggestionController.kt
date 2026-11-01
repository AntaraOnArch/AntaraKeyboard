package com.example.antarakeyboard.service.suggest

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads one bundled dictionary at a time (in the background) and answers suggestion queries.
 * Switching dictionary drops the previous one, so only one is ever held in memory.
 */
class SuggestionController(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Called on the main thread when a dictionary finished loading. */
    private val onReady: () -> Unit
) {
    private var dictionaryName: String? = null
    private var suggester: WordSuggester? = null
    private var loadJob: Job? = null

    /** Makes [name] the active dictionary (null = no suggestions). Loads it if needed. */
    fun use(name: String?) {
        if (name == dictionaryName) return
        dictionaryName = name
        suggester = null
        loadJob?.cancel()
        if (name == null) return

        loadJob = scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                runCatching {
                    context.assets.open("dictionaries/$name.txt").bufferedReader().useLines { lines ->
                        WordSuggester(WordSuggester.parse(lines))
                    }
                }.getOrNull()
            }
            if (dictionaryName == name) {
                suggester = loaded
                onReady()
            }
        }
    }

    /** Frees the dictionary (suggestions turned off). */
    fun release() = use(null)

    fun suggest(word: String): List<String> = suggester?.suggest(word) ?: emptyList()
}
