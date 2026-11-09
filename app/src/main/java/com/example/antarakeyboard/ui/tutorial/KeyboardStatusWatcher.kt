package com.example.antarakeyboard.ui.tutorial

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * Watches whether this keyboard is enabled (system settings list) and active (the selected input
 * method). [onChange] gets every new state – once right after [start], then on each change.
 * The system settings are observed, so a change made in Android settings or the input method
 * picker arrives while the screen is still open; [refresh] re-checks by hand (e.g. when the
 * window gets the focus back from the picker).
 */
class KeyboardStatusWatcher(
    private val context: Context,
    private val onChange: (Status) -> Unit
) {

    data class Status(val enabled: Boolean, val active: Boolean)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var last: Status? = null
    private var watching = false

    fun start() {
        if (!watching) {
            watching = true
            val resolver = context.contentResolver
            resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, observer)
            resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer)
        }
        last = null
        refresh()
    }

    fun stop() {
        if (!watching) return
        watching = false
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        if (!watching) return
        val status = status(context)
        if (status != last) {
            last = status
            onChange(status)
        }
    }

    companion object {
        fun status(context: Context) = Status(isEnabled(context), isActive(context))

        fun isEnabled(context: Context): Boolean {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            return imm.enabledInputMethodList.any { it.packageName == context.packageName }
        }

        fun isActive(context: Context): Boolean {
            val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            return id != null && ComponentName.unflattenFromString(id)?.packageName == context.packageName
        }
    }
}
