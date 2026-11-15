package com.mos.nira.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    @Test
    fun testAllTranslationFilesHaveAllKeysMatchingBase() {
        // Parse keys from base values/strings.xml
        val baseFile = File("src/main/res/values/strings.xml")
        assertTrue("Base strings.xml must exist", baseFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val baseDoc = builder.parse(baseFile)

        val baseKeys = mutableSetOf<String>()
        val stringNodes = baseDoc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as org.w3c.dom.Element
            baseKeys.add(element.getAttribute("name"))
        }

        val pluralNodes = baseDoc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val element = pluralNodes.item(i) as org.w3c.dom.Element
            baseKeys.add(element.getAttribute("name"))
        }

        assertTrue("Base strings should contain keys", baseKeys.isNotEmpty())

        val langFolders = listOf(
            "values-ta", "values-hi", "values-es", "values-fr",
            "values-de", "values-vi", "values-bn", "values-zh"
        )

        for (folder in langFolders) {
            val transFile = File("src/main/res/$folder/strings.xml")
            assertTrue("Translation file for $folder must exist", transFile.exists())

            val transDoc = builder.parse(transFile)
            val transKeys = mutableSetOf<String>()

            val tStringNodes = transDoc.getElementsByTagName("string")
            for (i in 0 until tStringNodes.length) {
                val element = tStringNodes.item(i) as org.w3c.dom.Element
                transKeys.add(element.getAttribute("name"))
            }

            val tPluralNodes = transDoc.getElementsByTagName("plurals")
            for (i in 0 until tPluralNodes.length) {
                val element = tPluralNodes.item(i) as org.w3c.dom.Element
                transKeys.add(element.getAttribute("name"))
            }

            // Check for missing keys
            val missingKeys = baseKeys - transKeys
            assertTrue("Locale $folder is missing string keys: $missingKeys", missingKeys.isEmpty())
        }
    }
}
