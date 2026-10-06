package com.example.antarakeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Settings export / import as a JSON file.
 *
 * The file holds whole SharedPreferences files (typed key/value pairs) under a format id and
 * version. It contains no package or class names, so a backup made by any build of the app
 * (including before the applicationId changes from com.example…) can be imported by any other.
 */
object SettingsBackup {

    const val FORMAT = "antara-keyboard-settings"

    /** Bump when a stored key or value changes meaning, and migrate older files in [decode]. */
    const val VERSION = 1

    const val MAX_FILE_BYTES = 2 * 1024 * 1024

    /** SharedPreferences files in a backup → keys that are never exported or overwritten. */
    val FILES: Map<String, Set<String>> = mapOf(
        KeyboardPrefs.PREFS_NAME to emptySet(),
        PrefsManager.PREFS_THEME to emptySet(),
        PrefsManager.PREFS_CUSTOM_THEME to emptySet(),
        EdgeSlotsStorage.SP_NAME to emptySet(),
        GlobalLongPressStorage.PREFS_NAME to emptySet(),
        // Recent emojis reveal what the user types – privacy, not a setting
        EmojiPickerStorage.PREFS_NAME to setOf(EmojiPickerStorage.KEY_RECENT_EMOJIS),
        SavedLayoutStorage.PREFS_NAME to emptySet()
    )

    sealed class DecodeResult {
        data class Ok(val data: Map<String, Map<String, Any>>) : DecodeResult()
        /** Not JSON, or not an Antara Keyboard settings file. */
        data object NotABackup : DecodeResult()
        /** Made by a newer app version whose format this version doesn't know. */
        data object NewerVersion : DecodeResult()
    }

    /* ───────── PURE JSON (unit-tested) ───────── */

    fun encode(data: Map<String, Map<String, *>>, exportedAt: String): String {
        val files = JsonObject()
        FILES.forEach { (name, excluded) ->
            val values = data[name] ?: return@forEach
            val file = JsonObject()
            values.keys.filter { it !in excluded }.sorted().forEach { key ->
                typedValue(values[key])?.let { file.add(key, it) }
            }
            files.add(name, file)
        }

        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            addProperty("exportedAt", exportedAt)
            add("data", files)
        }
        return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root)
    }

    fun decode(json: String): DecodeResult {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return DecodeResult.NotABackup

        val format = runCatching { root.get("format").asString }.getOrNull()
        val version = runCatching { root.get("version").asInt }.getOrNull()
        val files = runCatching { root.getAsJsonObject("data") }.getOrNull()
        if (format != FORMAT || version == null || version < 1 || files == null) {
            return DecodeResult.NotABackup
        }
        if (version > VERSION) return DecodeResult.NewerVersion

        val out = mutableMapOf<String, Map<String, Any>>()
        FILES.forEach { (name, excluded) ->
            val file = runCatching { files.getAsJsonObject(name) }.getOrNull() ?: return@forEach
            val values = mutableMapOf<String, Any>()
            file.entrySet().forEach { (key, element) ->
                if (key in excluded) return@forEach
                val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
                readValue(obj)?.let { values[key] = it }
            }
            out[name] = values
        }
        return DecodeResult.Ok(out)
    }

    private fun typedValue(value: Any?): JsonObject? {
        val (type, json) = when (value) {
            is Boolean -> "boolean" to JsonPrimitive(value)
            is Int -> "int" to JsonPrimitive(value)
            is Long -> "long" to JsonPrimitive(value)
            is Float -> "float" to JsonPrimitive(value)
            is String -> "string" to JsonPrimitive(value)
            is Set<*> -> "string_set" to JsonArray().apply {
                value.filterIsInstance<String>().sorted().forEach { add(it) }
            }
            else -> return null
        }
        return JsonObject().apply {
            addProperty("type", type)
            add("value", json)
        }
    }

    private fun readValue(obj: JsonObject): Any? = runCatching {
        val value = obj.get("value")
        when (obj.get("type").asString) {
            "boolean" -> value.asBoolean
            "int" -> value.asInt
            "long" -> value.asLong
            "float" -> value.asFloat
            "string" -> value.asString
            "string_set" -> value.asJsonArray.map { it.asString }.toSet()
            else -> null
        }
    }.getOrNull()

    /* ───────── ANDROID ───────── */

    fun export(context: Context): String {
        val data = FILES.keys.associateWith { name -> prefs(context, name).all }
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return encode(data, utc.format(Date()))
    }

    /**
     * Replaces each backed-up file with the imported values (excluded keys stay as they are).
     * Files missing from the backup are left untouched.
     */
    fun apply(context: Context, data: Map<String, Map<String, Any>>) {
        FILES.forEach { (name, excluded) ->
            val values = data[name] ?: return@forEach
            val prefs = prefs(context, name)
            val editor = prefs.edit()
            prefs.all.keys.filter { it !in excluded }.forEach { editor.remove(it) }
            values.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value as Set<String>)
                }
            }
            editor.commit()
        }
    }

    private fun prefs(context: Context, name: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}
