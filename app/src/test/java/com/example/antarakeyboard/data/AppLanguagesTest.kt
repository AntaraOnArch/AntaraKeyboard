package com.example.antarakeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class AppLanguagesTest {

    /** Unit tests run from the module folder, so the resources are at src/main/res. */
    private fun translatedResourceTags(): Set<String> =
        File("src/main/res").listFiles().orEmpty()
            .filter { File(it, "strings.xml").exists() }
            .map { it.name }
            .map { dir ->
                when {
                    dir == "values" -> "en"
                    dir.startsWith("values-b+") -> dir.removePrefix("values-b+").replace('+', '-')
                    else -> dir.removePrefix("values-")
                }
            }
            .toSet()

    @Test
    fun languageListMatchesTranslations() {
        assertEquals(translatedResourceTags(), AppLanguages.TAGS.toSet())
        assertEquals(AppLanguages.TAGS.size, AppLanguages.TAGS.toSet().size)
    }

    @Test
    fun matchHandlesRegionsAndScripts() {
        assertEquals("de", AppLanguages.match("de-DE"))
        assertEquals("sr-Latn", AppLanguages.match("sr-Latn-RS"))
        assertEquals("sr", AppLanguages.match("sr-RS"))
        assertEquals("nb", AppLanguages.match("nb-NO"))
        assertNull(AppLanguages.match("ja"))
        assertNull(AppLanguages.match(""))
        assertNull(AppLanguages.match(null))
    }
}
