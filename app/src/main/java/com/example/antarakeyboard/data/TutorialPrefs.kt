package com.example.antarakeyboard.data

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the first-run tutorial was finished or skipped (`tutorial_prefs`). Device state, not a
 * setting, so it is left out of settings export / import: an older backup can never bring the
 * tutorial back on its own.
 */
object TutorialPrefs {

    private const val PREFS_NAME = "tutorial_prefs"
    private const val KEY_DONE = "tutorial_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun setDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_DONE, true) }
    }
}
