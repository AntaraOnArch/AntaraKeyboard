package com.example.antarakeyboard.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenSourceLicensesTest {

    @Test
    fun everyLicenseTextIsBundled() {
        OpenSourceLicenses.ENTRIES.forEach { entry ->
            val file = File("src/main/assets/licenses/${entry.licenseAsset}")
            assertTrue("${entry.name}: missing ${file.path}", file.isFile && file.length() > 200)
        }
    }

    @Test
    fun mitTextsKeepTheirCopyrightNotices() {
        assertTrue(File("src/main/assets/licenses/mit-symspellkt.txt").readText().contains("Adam Brown"))
        assertTrue(File("src/main/assets/licenses/mit-murmurhash.txt").readText().contains("Gonçalo Silva"))
    }
}
