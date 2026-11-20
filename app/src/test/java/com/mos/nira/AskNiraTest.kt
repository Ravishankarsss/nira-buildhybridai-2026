package com.mos.nira

import com.mos.nira.core.ai.AskNira
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ask NIRA: the prompt carries the facts, recent turns and question; answers are checked in code. */
class AskNiraTest {

    private val facts = listOf(
        "Brief: garment colour navy; fibre cotton; quantity 500 pieces.",
        "Method: Discharge + digital print (possible, see notes; verified by Ravi on the floor).",
        "Screens: 1 (one white discharge screen in the shape of the artwork).",
        "Order cost now: ₹10.25 per piece, ₹5127 for 500 pieces."
    )

    private val template = listOf(File("src/main/assets/${AskNira.ASSET_PATH}"), File("app/src/main/assets/${AskNira.ASSET_PATH}"))
        .first { it.exists() }.readText()

    @Test
    fun promptHasFactsHistoryAndQuestion() {
        val history = (1..5).map { AskNira.Turn("q$it", "a$it") }
        val p = AskNira.build(template, facts, history, "  Why discharge?  ")
        assertTrue(p.contains("- Method: Discharge + digital print"))
        assertTrue(p.endsWith("Question: Why discharge?"))
        // Only the last turns go back to the model.
        assertTrue(p.contains("Q: q5") && p.contains("Q: q3"))
        assertFalse(p.contains("Q: q2"))
        assertFalse(p.contains("{facts}") || p.contains("{conversation}") || p.contains("{question}"))
        assertTrue(AskNira.build(template, facts, emptyList(), "x").contains("(none)"))
    }

    @Test
    fun numbersMustComeFromFactsOrTheQuestion() {
        assertNull(AskNira.check("It costs ₹10.25 per piece and uses 1 screen.", facts, "What does it cost?"))
        // A number the user asked about may be repeated...
        assertNull(AskNira.check("NIRA has 1 screen for this job, not 3.", facts, "Can I do it in 3 screens?"))
        // ...but a new calculation is held back.
        val hold = AskNira.check("For 1000 pieces it costs ₹9.10 each.", facts, "What about 1000 pieces?")
        assertEquals(AskNira.Hold.Numbers(listOf(9.1)), hold)
    }

    @Test
    fun colourCodesAreHeldUnlessTheUserTypedThem() {
        assertEquals(AskNira.Hold.ColourCode, AskNira.check("Use navy #1F2A5C ink.", facts, "Which ink?"))
        assertNull(AskNira.check("The code #1F2A5C is navy.", facts, "What is #1F2A5C?"))
    }

    @Test
    fun promptKeepsTheGuardrails() {
        assertTrue(template.contains("Do not calculate new numbers"))
        assertTrue(template.contains("never colour codes"))
        assertTrue(template.contains("same language as the question"))
    }
}
