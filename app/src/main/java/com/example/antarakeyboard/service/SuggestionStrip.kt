package com.example.antarakeyboard.service

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.service.suggest.SuggestionController
import com.example.antarakeyboard.service.suggest.SuggestionDictionaries
import com.example.antarakeyboard.service.suggest.WordSuggester
import kotlinx.coroutines.CoroutineScope

/**
 * Word suggestions: the strip of up to [SUGGESTION_COUNT] words above the keys. Tapping a word
 * replaces the word being typed and adds a space. The dictionary survives input view recreation.
 */
class SuggestionStrip(
    private val context: Context,
    scope: CoroutineScope,
    private val host: Host
) {
    interface Host {
        val themedContext: Context
        val inputConnection: InputConnection?
        /** Text color of the strip (the keys' text color). */
        fun textColor(): Int
        fun performHapticFeedback(view: View)
        /** A suggestion was committed (other typed text clears the swipe-restore buffer too). */
        fun onSuggestionApplied()
    }

    private val suggestionController by lazy {
        SuggestionController(context, scope) { refresh() }
    }
    private var suggestionStrip: LinearLayout? = null
    private var suggestionsOn = false
    private var suggestionsAllowedInField = false

    val heightPx: Int get() = 38.dp(context)

    val isVisible: Boolean get() = suggestionStrip?.isVisible == true

    /** Dictionary for the current keyboard script and app/device language. */
    private fun currentSuggestionDictionary(): String? =
        SuggestionDictionaries.forKeyboard(
            KeyboardPrefs.getSelectedLongPressPreset(context),
            KeyboardPrefs.languageTag(context)
        )

    /** New strip view for a new input view (the old one is dropped). */
    fun createView(): LinearLayout = LinearLayout(host.themedContext).apply {
        val themedCtx = host.themedContext
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        tag = "suggestion_strip"

        repeat(SUGGESTION_COUNT) { index ->
            if (index > 0) {
                addView(View(themedCtx), LinearLayout.LayoutParams(1.dp(context), 18.dp(context)))
            }
            addView(
                TextView(themedCtx).apply {
                    gravity = Gravity.CENTER
                    textSize = 16f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(4.dp(context), 0, 4.dp(context), 0)
                    setOnClickListener { view ->
                        val suggestion = (view as TextView).text?.toString().orEmpty()
                        if (suggestion.isNotEmpty()) {
                            host.performHapticFeedback(view)
                            applySuggestion(suggestion)
                        }
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
    }.also { suggestionStrip = it }

    /** Main app switch and field type; nothing in passwords, numbers, e-mails or URLs. */
    fun startInput(info: EditorInfo?) {
        suggestionsOn = KeyboardPrefs.isSuggestionsEnabled(context)
        suggestionsAllowedInField = InputTypes.allowsSuggestions(info)
        if (suggestionsOn) suggestionController.use(currentSuggestionDictionary())
        else suggestionController.release()
        update()
    }

    /** Another script types another language: switch the dictionary too. */
    fun onScriptChanged() {
        if (suggestionsOn) suggestionController.use(currentSuggestionDictionary())
    }

    /**
     * Shows the strip only when suggestions are on and the field allows them; fields that ask
     * for none (passwords, numbers, e-mail, URL, TYPE_TEXT_FLAG_NO_SUGGESTIONS) get no strip.
     * Recolors it to match the keys.
     */
    fun update() {
        val suggestionStrip = suggestionStrip ?: return
        suggestionStrip.visibility =
            if (suggestionsOn && suggestionsAllowedInField) View.VISIBLE else View.GONE
        val text = host.textColor()
        for (i in 0 until suggestionStrip.childCount) {
            when (val child = suggestionStrip.getChildAt(i)) {
                is TextView -> child.setTextColor(text)
                else -> child.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(text, 0x40))
            }
        }
        refresh()
    }

    fun refresh() {
        if (suggestionStrip == null || !suggestionsOn) return
        val word = if (suggestionsAllowedInField) currentTypedWord() else ""
        showSuggestions(if (word.isEmpty()) emptyList() else suggestionController.suggest(word))
    }

    fun clear() = showSuggestions(emptyList())

    private fun showSuggestions(words: List<String>) {
        val suggestionStrip = suggestionStrip ?: return
        var slot = 0
        for (i in 0 until suggestionStrip.childCount) {
            val view = suggestionStrip.getChildAt(i) as? TextView ?: continue
            view.text = words.getOrNull(slot).orEmpty()
            slot++
        }
    }

    /** The word right before the cursor (empty with a selection or inside a word). */
    private fun currentTypedWord(): String {
        val ic = host.inputConnection ?: return ""
        if (!ic.getSelectedText(0).isNullOrEmpty()) return ""
        val before = ic.getTextBeforeCursor(SUGGESTION_LOOKBEHIND, 0) ?: return ""
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        return WordSuggester.currentWord(before, after)
    }

    /** Replaces the word being typed with [suggestion] and adds a space. */
    private fun applySuggestion(suggestion: String) {
        val ic = host.inputConnection ?: return
        val typed = currentTypedWord()
        ic.beginBatchEdit()
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$suggestion ", 1)
        ic.endBatchEdit()
        host.onSuggestionApplied()
    }

    private companion object {
        const val SUGGESTION_COUNT = 3
        const val SUGGESTION_LOOKBEHIND = 48
    }
}
