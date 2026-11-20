package com.mos.nira

import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.ai.BriefReader
import com.mos.nira.core.ai.BriefReader.Drop
import com.mos.nira.core.ai.BriefReader.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Buyer Brief Reader: the AI proposes, code checks against allowed values and the pasted text. */
class BriefReaderTest {

    // A made-up order email (no real buyer or brand).
    private val email = """
        Hi team, please quote the summer tee.
        Body: navy blue, 100% combed cotton single jersey 180 gsm.
        Print: front chest logo, soft hand feel please.
        Order qty: 2,500 pcs across S-XXL.
    """.trimIndent()

    private val colours = listOf("white", "black", "navy", "red", "royal_blue")
    private val fabrics = listOf("single_jersey", "pique", "fleece", "interlock", "other")

    private val template = listOf(File("src/main/assets/${BriefReader.ASSET_PATH}"), File("app/src/main/assets/${BriefReader.ASSET_PATH}"))
        .first { it.exists() }.readText()

    private fun f(r: BriefReader.Reading, k: Kind) = r.fields.first { it.kind == k }

    @Test
    fun goodReplyFillsAllFiveAnswers() {
        val reply = """```json
            {"garment_colour": "navy", "fibre": "cotton", "fabric": "single_jersey", "quantity": 2500, "feel": "soft",
             "evidence": {"garment_colour": "navy blue", "fibre": "100% combed cotton", "fabric": "single jersey",
                          "quantity": "2,500 pcs", "feel": "soft hand feel"}}
            ```"""
        val r = BriefReader.parse(reply, colours, fabrics, email)!!
        assertEquals(5, r.usable.size)
        assertEquals("2500", f(r, Kind.QUANTITY).value)
        assertEquals(Fibre.COTTON, BriefReader.fibre(f(r, Kind.FIBRE).value))
        assertEquals(Feel.SOFT, BriefReader.feel(f(r, Kind.FEEL).value))
    }

    @Test
    fun madeUpAnswersAreDropped() {
        // A colour outside the list, evidence not in the email, and a quantity the email never says.
        val reply = """{"garment_colour": "teal", "fibre": "polyester", "fabric": "pique", "quantity": 3000, "feel": null,
            "evidence": {"garment_colour": "teal", "fibre": "polyester", "fabric": "pique knit", "quantity": "3000 pcs"}}"""
        val r = BriefReader.parse(reply, colours, fabrics, email)!!
        assertEquals(Drop.NOT_ALLOWED, f(r, Kind.GARMENT_COLOUR).dropped)
        assertEquals(Drop.NOT_IN_TEXT, f(r, Kind.FIBRE).dropped)
        assertEquals(Drop.NOT_IN_TEXT, f(r, Kind.FABRIC).dropped)
        assertEquals(Drop.NOT_IN_TEXT, f(r, Kind.QUANTITY).dropped)
        assertTrue(r.fields.none { it.kind == Kind.FEEL })
        assertTrue(r.usable.isEmpty())
    }

    @Test
    fun badQuantityAndNonJsonReplies() {
        val r = BriefReader.parse("""{"quantity": 12.5}""", colours, fabrics, null)!!
        assertEquals(Drop.BAD_NUMBER, f(r, Kind.QUANTITY).dropped)
        assertNull(BriefReader.parse("Sorry, I cannot read this page.", colours, fabrics, null))
        // A page photo has no text to check against: allowed values still apply, evidence is shown.
        val photo = BriefReader.parse("""{"garment_colour": "black", "quantity": 1200, "evidence": {"quantity": "1200"}}""", colours, fabrics, null)!!
        assertEquals(2, photo.usable.size)
    }

    @Test
    fun promptListsTheAllowedValues() {
        val p = BriefReader.build(template, email, colours, fabrics)
        assertTrue(p.contains("navy, red, royal_blue"))
        assertTrue(p.contains("single_jersey, pique"))
        assertTrue(p.contains("cotton_poly_blend"))
        assertTrue(p.contains("Order qty: 2,500 pcs"))
        assertFalse(p.contains("{brief}") || p.contains("{colours}"))
        assertTrue(BriefReader.build(template, "", colours, fabrics).contains("attached page image"))
    }
}
