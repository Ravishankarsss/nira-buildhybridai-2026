package com.mos.nira.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleHelperTest {

    @Test
    fun testSupportedLanguagesList() {
        val languages = LocaleHelper.supportedLanguages
        assertTrue("Must support at least 9 languages", languages.size >= 9)

        // Verify Tamil is present
        val tamil = languages.find { it.code == "ta" }
        assertNotNull("Tamil must be supported", tamil)
        assertEquals("தமிழ் (Tamil)", tamil?.nativeName)

        // Verify major international textile languages are present
        val expectedCodes = listOf("en", "ta", "hi", "es", "fr", "de", "vi", "bn", "zh")
        for (code in expectedCodes) {
            val lang = languages.find { it.code == code }
            assertNotNull("Language code $code must be supported", lang)
        }
    }
}
