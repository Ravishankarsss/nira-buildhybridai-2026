package com.mos.nira

import com.mos.nira.core.advice.GroundVerdict
import com.mos.nira.core.advice.PrintVsGround
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ravi's rule: lighter than the fabric needs discharge or a white base; darker prints on top. */
class PrintVsGroundTest {

    private val rules = PrintVsGround.parse(TestRules.root)!!

    @Test
    fun rulesLoadFromTheRealFile() {
        assertTrue(rules.lighterThresholdL > 0)
        assertEquals("assumption", rules.confidence)
        assertNull(PrintVsGround.parse(JSONObject("{}")))
    }

    /** The pink bunny all-over print (colours measured from Ravi's photo, 18 Nov 2026). */
    @Test
    fun bunnyOnPinkGround() {
        val pinkGround = "#D0859C"
        val white = "#E4E1DE"
        val lightPink = "#DCB8C8"
        val red = "#9B3C3C"
        val blackBrown = "#3D353A"
        val v = PrintVsGround.check(listOf(white, lightPink, red, blackBrown), pinkGround, rules).associate { it.inkHex to it.verdict }
        assertEquals(GroundVerdict.NEEDS_DISCHARGE_OR_BASE, v[white])
        assertEquals(GroundVerdict.NEEDS_DISCHARGE_OR_BASE, v[lightPink])
        assertEquals(GroundVerdict.PRINTS_ON_TOP, v[red])
        assertEquals(GroundVerdict.PRINTS_ON_TOP, v[blackBrown])
    }

    @Test
    fun navyGarment() {
        val v = PrintVsGround.check(listOf("#1F2A5C", "#C0392B", "#03213F"), "#001F3F", rules).associate { it.inkHex to it.verdict }
        // The logo's navy ink is about 7 L* lighter than the navy garment: above the threshold, so
        // by Ravi's rule it needs discharge or a white base (the contrast check also flags it).
        assertEquals(GroundVerdict.NEEDS_DISCHARGE_OR_BASE, v["#1F2A5C"])
        assertEquals(GroundVerdict.NEEDS_DISCHARGE_OR_BASE, v["#C0392B"])
        // An ink within the threshold of the garment is reported as close to it.
        assertEquals(GroundVerdict.CLOSE_TO_GROUND, v["#03213F"])
    }
}
