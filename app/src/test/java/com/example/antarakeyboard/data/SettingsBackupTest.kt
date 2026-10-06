package com.example.antarakeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val sample: Map<String, Map<String, Any>> = mapOf(
        KeyboardPrefs.PREFS_NAME to mapOf(
            "row_count" to 4,
            "key_shape" to "TRIANGLE",
            "vibration_enabled" to false,
            "rgb_rainbow_brightness" to 0.45f,
            "some_timestamp" to 1_759_660_000_000L,
            "some_set" to setOf("b", "a")
        ),
        PrefsManager.PREFS_THEME to mapOf("dark_mode" to true, "app_language" to "sr-Latn"),
        EmojiPickerStorage.PREFS_NAME to mapOf(
            "tabs_position" to "TOP",
            EmojiPickerStorage.KEY_RECENT_EMOJIS to "[\"😀\"]"
        )
    )

    private fun ok(json: String) = SettingsBackup.decode(json) as SettingsBackup.DecodeResult.Ok

    @Test
    fun roundTripKeepsValuesAndTypes() {
        val decoded = ok(SettingsBackup.encode(sample, "2026-10-05T12:00:00Z")).data

        assertEquals(sample[KeyboardPrefs.PREFS_NAME], decoded[KeyboardPrefs.PREFS_NAME])
        assertEquals(sample[PrefsManager.PREFS_THEME], decoded[PrefsManager.PREFS_THEME])
        val kb = decoded.getValue(KeyboardPrefs.PREFS_NAME)
        assertTrue(kb["row_count"] is Int)
        assertTrue(kb["some_timestamp"] is Long)
        assertTrue(kb["rgb_rainbow_brightness"] is Float)
    }

    @Test
    fun recentEmojisAreNeverExported() {
        val json = SettingsBackup.encode(sample, "now")
        assertFalse(json.contains(EmojiPickerStorage.KEY_RECENT_EMOJIS))
        assertEquals(mapOf("tabs_position" to "TOP"), ok(json).data[EmojiPickerStorage.PREFS_NAME])
    }

    @Test
    fun fileHasNoPackageOrClassNames() {
        val json = SettingsBackup.encode(sample, "now")
        assertFalse(json.contains("com.example"))
        assertFalse(json.contains("antarakeyboard."))
        assertTrue(json.contains("\"format\": \"${SettingsBackup.FORMAT}\""))
    }

    @Test
    fun rejectsOtherFiles() {
        listOf(
            "",
            "not json",
            "[]",
            """{"format":"something-else","version":1,"data":{}}""",
            """{"format":"${SettingsBackup.FORMAT}","data":{}}""",
            """{"format":"${SettingsBackup.FORMAT}","version":1}"""
        ).forEach { json ->
            assertEquals(json, SettingsBackup.DecodeResult.NotABackup, SettingsBackup.decode(json))
        }
    }

    @Test
    fun rejectsNewerVersion() {
        val json = """{"format":"${SettingsBackup.FORMAT}","version":${SettingsBackup.VERSION + 1},"data":{}}"""
        assertEquals(SettingsBackup.DecodeResult.NewerVersion, SettingsBackup.decode(json))
    }

    @Test
    fun skipsUnknownFilesTypesAndBrokenEntries() {
        val json = """
            {"format":"${SettingsBackup.FORMAT}","version":1,"data":{
              "unknown_prefs":{"x":{"type":"int","value":1}},
              "${KeyboardPrefs.PREFS_NAME}":{
                "row_count":{"type":"int","value":5},
                "weird":{"type":"color","value":"#fff"},
                "broken":{"type":"int","value":"abc"},
                "not_an_object":3
              }
            }}
        """.trimIndent()
        val data = ok(json).data
        assertFalse(data.containsKey("unknown_prefs"))
        assertEquals(mapOf("row_count" to 5), data[KeyboardPrefs.PREFS_NAME])
    }
}
