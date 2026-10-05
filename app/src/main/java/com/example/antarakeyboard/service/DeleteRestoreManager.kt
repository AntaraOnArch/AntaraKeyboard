package com.example.antarakeyboard.service

import android.content.Context
import android.icu.text.BreakIterator
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.ViewConfiguration
import com.example.antarakeyboard.extensions.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages delete/restore swipe gestures and backspace hold functionality.
 * Extracted from MyKeyboardService for better separation of concerns.
 */
class DeleteRestoreManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val inputConnectionProvider: () -> InputConnection?
) {
    private companion object {
        // Long enough for the longest emoji ZWJ sequences
        const val GRAPHEME_LOOKBEHIND = 32
    }

    private var deleteRepeatJob: Job? = null
    private var restoreRepeatJob: Job? = null
    private var backspaceHoldJob: Job? = null
    private var backspaceStartHoldJob: Job? = null

    private var deleteRepeatMs: Long = 120L
    private var restoreRepeatMs: Long = 120L
    private var backspaceHoldMs: Long = 90L

    private var lastDeletedText: String = ""
    private val currentDeleteBatch = StringBuilder()
    private var restoreProgressIndex: Int = 0

    private var isDeleteGestureActive = false
    private var isRestoreGestureActive = false
    private var isBackspaceHoldActive = false

    /* ───────── DELETE BATCH ───────── */

    private fun beginDeleteBatch() {
        currentDeleteBatch.clear()
    }

    private fun appendDeletedChar(ch: String) {
        currentDeleteBatch.insert(0, ch)
    }

    private fun finishDeleteBatch() {
        val result = currentDeleteBatch.toString()
        if (result.isNotEmpty()) {
            lastDeletedText = result
            restoreProgressIndex = 0
        }
    }

    fun clearRestoreBuffer() {
        lastDeletedText = ""
        restoreProgressIndex = 0
    }

    /* ───────── SWIPE REPEAT DELAYS ───────── */

    private fun swipeRepeatDelay(absDx: Float): Long {
        return when {
            absDx > 170.dp(context) -> 25L
            absDx > 140.dp(context) -> 40L
            absDx > 110.dp(context) -> 55L
            absDx > 80.dp(context) -> 75L
            absDx > 60.dp(context) -> 95L
            else -> 120L
        }
    }

    private fun restoreRepeatDelay(absDx: Float): Long {
        return when {
            absDx > 170.dp(context) -> 14L
            absDx > 140.dp(context) -> 24L
            absDx > 110.dp(context) -> 36L
            absDx > 80.dp(context) -> 50L
            absDx > 60.dp(context) -> 68L
            else -> 90L
        }
    }

    /* ───────── DELETE/RESTORE ONE CHAR ───────── */

    /**
     * Deletes the selection if there is one, otherwise one user-perceived character
     * (grapheme cluster: emoji, flags, letters with combining marks stay whole).
     * Returns false when there was nothing known to delete.
     */
    private fun deleteOneForSwipe(): Boolean {
        val ic = inputConnectionProvider() ?: return false

        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            appendDeletedChar(selected.toString())
            ic.commitText("", 1)
            return true
        }

        val before = ic.getTextBeforeCursor(GRAPHEME_LOOKBEHIND, 0)?.toString().orEmpty()
        if (before.isEmpty()) return false

        val last = lastGrapheme(before)
        appendDeletedChar(last)
        ic.deleteSurroundingText(last.length, 0)
        return true
    }

    private fun restoreOneForSwipe() {
        val ic = inputConnectionProvider() ?: return
        if (lastDeletedText.isEmpty()) return
        if (restoreProgressIndex >= lastDeletedText.length) return

        val end = nextGraphemeEnd(lastDeletedText, restoreProgressIndex)
        ic.commitText(lastDeletedText.substring(restoreProgressIndex, end), 1)
        restoreProgressIndex = end

        if (restoreProgressIndex >= lastDeletedText.length) {
            lastDeletedText = ""
            restoreProgressIndex = 0
        }
    }

    /* ───────── SWIPE DELETE ───────── */

    fun startSwipeDelete(absDx: Float) {
        stopSwipeRestore()
        deleteRepeatMs = swipeRepeatDelay(absDx)

        if (!isDeleteGestureActive) {
            isDeleteGestureActive = true
            beginDeleteBatch()
        }

        if (deleteRepeatJob != null) return

        deleteOneForSwipe()
        deleteRepeatJob = scope.launch {
            while (isActive) {
                delay(deleteRepeatMs)
                deleteOneForSwipe()
            }
        }
    }

    fun updateSwipeDelete(absDx: Float) {
        deleteRepeatMs = swipeRepeatDelay(absDx)
    }

    fun stopSwipeDelete() {
        deleteRepeatJob?.cancel()
        deleteRepeatJob = null

        if (isDeleteGestureActive) {
            finishDeleteBatch()
            isDeleteGestureActive = false
        }
    }

    /* ───────── SWIPE RESTORE ───────── */

    fun startSwipeRestore(absDx: Float) {
        stopSwipeDelete()
        restoreRepeatMs = restoreRepeatDelay(absDx)

        if (!isRestoreGestureActive) {
            isRestoreGestureActive = true
        }

        if (restoreRepeatJob != null) return

        restoreOneForSwipe()
        restoreRepeatJob = scope.launch {
            while (isActive) {
                delay(restoreRepeatMs)
                restoreOneForSwipe()
            }
        }
    }

    fun updateSwipeRestore(absDx: Float) {
        restoreRepeatMs = restoreRepeatDelay(absDx)
    }

    fun stopSwipeRestore() {
        restoreRepeatJob?.cancel()
        restoreRepeatJob = null
        isRestoreGestureActive = false
    }

    /* ───────── BACKSPACE HOLD ───────── */

    fun startBackspaceHold() {
        cancelPendingBackspaceHold()

        if (!isBackspaceHoldActive) {
            isBackspaceHoldActive = true
            beginDeleteBatch()
        }

        if (backspaceHoldJob != null) return

        // First delete immediately when hold starts
        deleteOneForSwipe()
        backspaceHoldJob = scope.launch {
            while (isActive) {
                delay(backspaceHoldMs)
                deleteOneForSwipe()
            }
        }
    }

    fun isBackspaceHoldRunning(): Boolean {
        return isBackspaceHoldActive
    }

    fun stopBackspaceHold() {
        cancelPendingBackspaceHold()

        backspaceHoldJob?.cancel()
        backspaceHoldJob = null

        if (isBackspaceHoldActive) {
            finishDeleteBatch()
            isBackspaceHoldActive = false
        }
    }

    fun scheduleBackspaceHold() {
        cancelPendingBackspaceHold()

        backspaceStartHoldJob = scope.launch {
            delay(ViewConfiguration.getLongPressTimeout().toLong())
            startBackspaceHold()
        }
    }

    fun cancelPendingBackspaceHold() {
        backspaceStartHoldJob?.cancel()
        backspaceStartHoldJob = null
    }

    fun backspaceOnce() {
        beginDeleteBatch()
        if (!deleteOneForSwipe()) {
            // Editors that don't expose their text (e.g. terminals) still get a DEL key
            inputConnectionProvider()?.let { ic ->
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
        finishDeleteBatch()
    }

    /* ───────── GRAPHEMES ───────── */

    private fun lastGrapheme(text: String): String {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val start = it.preceding(text.length).takeIf { pos -> pos != BreakIterator.DONE } ?: 0
        return text.substring(start)
    }

    private fun nextGraphemeEnd(text: String, from: Int): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val end = it.following(from)
        return if (end == BreakIterator.DONE) text.length else end
    }

    /* ───────── RESET ───────── */

    fun resetState() {
        stopSwipeDelete()
        stopSwipeRestore()
        stopBackspaceHold()
        currentDeleteBatch.clear()
        isDeleteGestureActive = false
        isRestoreGestureActive = false
        isBackspaceHoldActive = false
        restoreProgressIndex = 0
    }
}
