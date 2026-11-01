package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GlobalLongPressStorage {

    const val PREFS_NAME = "global_lp_prefs"
    private const val KEY_ALPHABET_BINDS = "global_alphabet_binds"
    private const val KEY_NUMERIC_BINDS = "global_numeric_binds"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ───────── ALPHABET BINDS ───────── */

    fun saveAlphabetBinds(context: Context, binds: Map<String, List<String>>) {
        val json = gson.toJson(binds)
        prefs(context).edit { putString(KEY_ALPHABET_BINDS, json) }
    }

    fun loadAlphabetBinds(context: Context): Map<String, List<String>> {
        val json = prefs(context).getString(KEY_ALPHABET_BINDS, null)
        return if (!json.isNullOrBlank()) {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            runCatching { gson.fromJson<Map<String, List<String>>>(json, type) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
    }
    fun getAlphabetBind(context: Context, keyLabel: String): List<String> {
        return loadAlphabetBinds(context)[keyLabel].orEmpty()
    }

    fun saveAlphabetBind(context: Context, keyLabel: String, values: List<String>) {
        val binds = loadAlphabetBinds(context).toMutableMap()
        binds[keyLabel] = values
        saveAlphabetBinds(context, binds)
    }

    fun clearAlphabetBinds(context: Context) {
        prefs(context).edit { remove(KEY_ALPHABET_BINDS) }
    }

    /* ───────── NUMERIC BINDS ───────── */

    fun saveNumericBinds(context: Context, binds: Map<String, List<String>>) {
        val json = gson.toJson(binds)
        prefs(context).edit { putString(KEY_NUMERIC_BINDS, json) }
    }

    fun loadNumericBinds(context: Context): Map<String, List<String>> {
        val json = prefs(context).getString(KEY_NUMERIC_BINDS, null)
        return if (!json.isNullOrBlank()) {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            runCatching { gson.fromJson<Map<String, List<String>>>(json, type) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
    }

    fun clearNumericBinds(context: Context) {
        prefs(context).edit { remove(KEY_NUMERIC_BINDS) }
    }

    /* ───────── HELPERS ───────── */

    /**
     * Saves the binds of every key in [layout] into the global binds. Keys that are not in this
     * layout (other row counts have other numeric keys) keep their binds; a key whose binds were
     * all removed is stored with an empty list, so it doesn't fall back to the factory binds.
     */
    fun extractAndSaveAlphabetBinds(context: Context, layout: com.example.antarakeyboard.model.KeyboardConfig) {
        saveAlphabetBinds(context, loadAlphabetBinds(context) + extractBindsFromLayout(layout))
    }

    fun extractAndSaveNumericBinds(context: Context, layout: com.example.antarakeyboard.model.KeyboardConfig) {
        saveNumericBinds(context, loadNumericBinds(context) + extractBindsFromLayout(layout))
    }

    internal fun extractBindsFromLayout(layout: com.example.antarakeyboard.model.KeyboardConfig): Map<String, List<String>> =
        (layout.rows.flatMap { it.keys } + layout.specialLeft + layout.specialRight)
            .filter { it.label.isNotBlank() }
            .associate { it.label to it.longPressBindings.toList() }

    /**
     * Applies the global binds to [layout]; [factory] is the default layout of the same row count.
     */
    fun applyAlphabetBindsToLayout(
        context: Context,
        layout: com.example.antarakeyboard.model.KeyboardConfig,
        factory: com.example.antarakeyboard.model.KeyboardConfig
    ): com.example.antarakeyboard.model.KeyboardConfig {
        if (!prefs(context).contains(KEY_ALPHABET_BINDS)) return layout
        return applyBindsToLayout(layout, loadAlphabetBinds(context), factory)
    }

    fun applyNumericBindsToLayout(
        context: Context,
        layout: com.example.antarakeyboard.model.KeyboardConfig,
        factory: com.example.antarakeyboard.model.KeyboardConfig
    ): com.example.antarakeyboard.model.KeyboardConfig {
        if (!prefs(context).contains(KEY_NUMERIC_BINDS)) return layout
        return applyBindsToLayout(layout, loadNumericBinds(context), factory)
    }

    /**
     * The global binds are the only truth. A key missing from them gets the factory binds of
     * [factory] (e.g. `" , : ;` on `.`), never the snapshot stored inside [layout], which can
     * still hold letters of a previously used language.
     */
    internal fun applyBindsToLayout(
        layout: com.example.antarakeyboard.model.KeyboardConfig,
        binds: Map<String, List<String>>,
        factory: com.example.antarakeyboard.model.KeyboardConfig
    ): com.example.antarakeyboard.model.KeyboardConfig {
        val factoryBinds = extractBindsFromLayout(factory)
        fun withBinds(key: com.example.antarakeyboard.model.KeyConfig) =
            if (key.label.isBlank()) key
            else key.copy(
                longPressBindings = (binds[key.label] ?: factoryBinds[key.label]).orEmpty().toMutableList()
            )

        return layout.copy(
            rows = layout.rows.map { row ->
                row.copy(keys = row.keys.map(::withBinds).toMutableList())
            }.toMutableList(),
            specialLeft = layout.specialLeft.map(::withBinds).toMutableList(),
            specialRight = layout.specialRight.map(::withBinds).toMutableList()
        )
    }
}
