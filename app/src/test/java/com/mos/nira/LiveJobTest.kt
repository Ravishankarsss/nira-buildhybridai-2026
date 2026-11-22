package com.mos.nira

import com.mos.nira.core.live.LiveJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class LiveJobTest {

    @Test
    fun codesUseOnlyEasyLettersAndMatchTheRulesPattern() {
        val r = Random(7)
        repeat(500) {
            val c = LiveJob.newCode(r)
            assertEquals(8, c.length)
            assertTrue(c, LiveJob.isCode(c))
            assertFalse(c.any { it in "IO01" })
        }
    }

    @Test
    fun typedCodesAreCleanedUp() {
        assertEquals("K7QP3MZD", LiveJob.normalise("k7qp-3mzd"))
        assertEquals("K7QP3MZD", LiveJob.normalise(" K7QP 3MZD "))
        assertNull(LiveJob.normalise("K7QP-3MZ"))
        assertNull(LiveJob.normalise("K7QP-3MZ0"))
        assertEquals("K7QP-3MZD", LiveJob.display("K7QP3MZD"))
    }

    @Test
    fun fieldsStayInsideTheRulesLimits() {
        val f = LiveJob.fields("t".repeat(300), "m".repeat(300), "2026-11-22 14:00", List(80) { "x".repeat(900) }, "p".repeat(30_000))
        assertEquals(LiveJob.MAX_TITLE_CHARS, (f["title"] as String).length)
        @Suppress("UNCHECKED_CAST") val facts = f["facts"] as List<String>
        assertEquals(LiveJob.MAX_FACTS, facts.size)
        assertTrue(facts.all { it.length == LiveJob.MAX_FACT_CHARS })
        assertEquals(LiveJob.MAX_PROMPT_CHARS, (f["prompt"] as String).length)
        // No artwork, no colour codes field, only the keys firestore.rules allows (plus uid and the two times).
        assertEquals(setOf("v", "title", "method_name", "saved_at", "generated_by", "facts", "prompt"), f.keys)
    }

    @Test
    fun rulesFileUsesTheSameCodePatternAndLimits() {
        val rules = listOf(File("../firestore.rules"), File("firestore.rules")).first { it.exists() }.readText()
        assertTrue(rules.contains("'^[A-HJ-NP-Z2-9]{8}\$'"))
        assertTrue(rules.contains("d.facts.size() <= ${LiveJob.MAX_FACTS}"))
        assertTrue(rules.contains("d.prompt.size() <= ${LiveJob.MAX_PROMPT_CHARS.toString()}"))
        assertTrue(rules.contains("d.title.size() <= ${LiveJob.MAX_TITLE_CHARS}"))
        assertTrue(rules.contains("allow list: if false;"))
        // The alphabet is exactly A-H, J-N, P-Z, 2-9.
        val expected = (('A'..'H') + ('J'..'N') + ('P'..'Z') + ('2'..'9')).joinToString("")
        assertEquals(expected, LiveJob.ALPHABET)
    }
}
