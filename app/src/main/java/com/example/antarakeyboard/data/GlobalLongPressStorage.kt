package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GlobalLongPressStorage {

    private const val PREFS_NAME = "global_lp_prefs"
    private const val KEY_ALPHABET_BINDS = "global_alphabet_binds"
    private const val KEY_NUMERIC_BINDS = "global_numeric_binds"

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /* ───────── ALPHABET BINDS ───────── */

    fun saveAlphabetBinds(context: Context, binds: Map<String, List<String>>) {
        val json = gson.toJson(binds)
        prefs(context).edit().putString(KEY_ALPHABET_BINDS, json).apply()
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
        prefs(context).edit().remove(KEY_ALPHABET_BINDS).apply()
    }

    /* ───────── NUMERIC BINDS ───────── */

    fun saveNumericBinds(context: Context, binds: Map<String, List<String>>) {
        val json = gson.toJson(binds)
        prefs(context).edit().putString(KEY_NUMERIC_BINDS, json).apply()
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
        prefs(context).edit().remove(KEY_NUMERIC_BINDS).apply()
    }

    /* ───────── HELPERS ───────── */

    /**
     * Izvuče bindove iz layouta i spremi ih globalno
     */
    fun extractAndSaveAlphabetBinds(context: Context, layout: com.example.antarakeyboard.model.KeyboardConfig) {
        val binds = extractBindsFromLayout(layout)
        saveAlphabetBinds(context, binds)
    }

    fun extractAndSaveNumericBinds(context: Context, layout: com.example.antarakeyboard.model.KeyboardConfig) {
        val binds = extractBindsFromLayout(layout)
        saveNumericBinds(context, binds)
    }

    private fun extractBindsFromLayout(layout: com.example.antarakeyboard.model.KeyboardConfig): Map<String, List<String>> {
        val binds = mutableMapOf<String, List<String>>()

        layout.rows.forEach { row ->
            row.keys.forEach { key ->
                if (key.longPressBindings.isNotEmpty() && key.label.isNotBlank()) {
                    binds[key.label] = key.longPressBindings.toList()
                }
            }
        }
        layout.specialLeft.forEach { key ->
            if (key.longPressBindings.isNotEmpty() && key.label.isNotBlank()) {
                binds[key.label] = key.longPressBindings.toList()
            }
        }
        layout.specialRight.forEach { key ->
            if (key.longPressBindings.isNotEmpty() && key.label.isNotBlank()) {
                binds[key.label] = key.longPressBindings.toList()
            }
        }

        return binds
    }

    /**
     * Apliciraj globalne bindove na layout
     */
    fun applyAlphabetBindsToLayout(
        context: Context,
        layout: com.example.antarakeyboard.model.KeyboardConfig
    ): com.example.antarakeyboard.model.KeyboardConfig {
        val globalBinds = loadAlphabetBinds(context)
        return applyBindsToLayout(layout, globalBinds)
    }

    fun applyNumericBindsToLayout(
        context: Context,
        layout: com.example.antarakeyboard.model.KeyboardConfig
    ): com.example.antarakeyboard.model.KeyboardConfig {
        val globalBinds = loadNumericBinds(context)
        return applyBindsToLayout(layout, globalBinds)
    }

    private fun applyBindsToLayout(
        layout: com.example.antarakeyboard.model.KeyboardConfig,
        binds: Map<String, List<String>>
    ): com.example.antarakeyboard.model.KeyboardConfig {
        if (binds.isEmpty()) return layout

        return layout.copy(
            rows = layout.rows.map { row ->
                row.copy(keys = row.keys.map { key ->
                    val globalBind = binds[key.label]
                    if (globalBind != null && key.label.isNotBlank()) {
                        key.copy(longPressBindings = globalBind.toMutableList())
                    } else {
                        key
                    }
                }.toMutableList())
            }.toMutableList(),
            specialLeft = layout.specialLeft.map { key ->
                val globalBind = binds[key.label]
                if (globalBind != null && key.label.isNotBlank()) {
                    key.copy(longPressBindings = globalBind.toMutableList())
                } else {
                    key
                }
            }.toMutableList(),
            specialRight = layout.specialRight.map { key ->
                val globalBind = binds[key.label]
                if (globalBind != null && key.label.isNotBlank()) {
                    key.copy(longPressBindings = globalBind.toMutableList())
                } else {
                    key
                }
            }.toMutableList()
        )
    }
}