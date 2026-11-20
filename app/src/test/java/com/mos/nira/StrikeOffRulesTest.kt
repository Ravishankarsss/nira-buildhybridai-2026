package com.mos.nira

import com.mos.nira.core.qc.StrikeOff
import com.mos.nira.core.qc.StrikeOffVerdict
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Strike-off rules come from print_rules.json (Ravi, 20 Nov 2026); no defaults in code. */
class StrikeOffRulesTest {

    private val rules = StrikeOff.parse(TestRules.root)!!

    @Test
    fun ravisLimitsAreRead() {
        assertEquals(2.0, rules.passMax, 0.0)
        assertEquals(4.0, rules.checkMax, 0.0)
        assertEquals(4.0, rules.failAbove, 0.0)
        assertEquals(listOf("pure_white", "pure_black"), rules.visualCheckColours)
        assertTrue(rules.whiteReference)
        assertEquals("D65_light_box", rules.preferredLighting)
        assertEquals("approved_artwork_file", rules.firstStrikeOffStandard)
        assertEquals("approved_golden_sample_photo", rules.bulkStandard)
        assertTrue(rules.source!!.startsWith("S Ravi Shankar"))
    }

    @Test
    fun verdictFollowsTheLimits() {
        assertEquals(StrikeOffVerdict.PASS, StrikeOff.verdict(2.0, rules))
        assertEquals(StrikeOffVerdict.CHECK, StrikeOff.verdict(2.01, rules))
        assertEquals(StrikeOffVerdict.CHECK, StrikeOff.verdict(4.0, rules))
        assertEquals(StrikeOffVerdict.FAIL, StrikeOff.verdict(4.01, rules))
        assertEquals(StrikeOffVerdict.FAIL, StrikeOff.overall(listOf(StrikeOffVerdict.PASS, StrikeOffVerdict.FAIL)))
        assertEquals(StrikeOffVerdict.CHECK, StrikeOff.overall(listOf(StrikeOffVerdict.PASS, StrikeOffVerdict.CHECK)))
        assertNull(StrikeOff.overall(emptyList()))
    }

    @Test
    fun missingSectionMeansNoRules() {
        assertNull(StrikeOff.parse(JSONObject("{}")))
        assertNull(StrikeOff.parse(JSONObject("""{"strike_off_check": {"v1_active_rules": {"color_limits": {"thresholds_delta_e": {"pass_max": 2}}}}}""")))
    }
}
