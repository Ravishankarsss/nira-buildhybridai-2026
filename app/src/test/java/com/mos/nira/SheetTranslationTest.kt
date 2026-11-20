package com.mos.nira

import com.mos.nira.core.ai.SheetTranslation
import com.mos.nira.core.ai.SheetTranslation.Reject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sheet translation: a line is used only if every number survives and no code is added. */
class SheetTranslationTest {

    private val texts = listOf(
        "Reactive-dyed cotton only; discharge test first",
        "Cure at 160 °C for 90 seconds.",
        "Test every dye lot: some dyes do not discharge fully"
    )

    @Test
    fun goodTranslationsAreKept() {
        val reply = """["Solo algodón teñido reactivo; prueba de discharge primero", "Curar a 160 °C durante 90 segundos.", "Probar cada lote de tinte"]"""
        val r = SheetTranslation.parse(reply, texts)!!
        assertEquals(3, r.translated.size)
        assertTrue(r.rejected.isEmpty())
    }

    @Test
    fun changedNumbersAndCodesAreRejected() {
        val reply = """```json
            ["Solo algodón #1F2A5C", "Curar a 150 °C durante 90 segundos.", ""]
            ```"""
        val r = SheetTranslation.parse(reply, texts)!!
        assertTrue(r.translated.isEmpty())
        assertEquals(Reject.COLOUR_CODE, r.rejected[texts[0]])
        assertEquals(Reject.NUMBERS_CHANGED, r.rejected[texts[1]])
        assertEquals(Reject.EMPTY, r.rejected[texts[2]])
        // A dropped number is caught too.
        assertEquals(Reject.NUMBERS_CHANGED, SheetTranslation.check("Cure at 160 °C", "Curar a alta temperatura"))
    }

    @Test
    fun wrongShapeRepliesAreRefused() {
        assertNull(SheetTranslation.parse("""["only one"]""", texts))
        assertNull(SheetTranslation.parse("Sorry, I cannot.", texts))
    }

    @Test
    fun promptCarriesLanguageAndTexts() {
        val template = listOf(File("src/main/assets/${SheetTranslation.ASSET_PATH}"), File("app/src/main/assets/${SheetTranslation.ASSET_PATH}"))
            .first { it.exists() }.readText()
        val p = SheetTranslation.build(template, texts, "Spanish")
        assertTrue(p.contains("into Spanish"))
        assertTrue(p.contains("\"Cure at 160 °C for 90 seconds.\""))
        assertTrue(p.contains("Keep every number exactly as written"))
    }
}
