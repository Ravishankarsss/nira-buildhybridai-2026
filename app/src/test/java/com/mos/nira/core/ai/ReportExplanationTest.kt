package com.mos.nira.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReportExplanationTest {

    private val facts = listOf(
        "Garment colour #1E1E1E (L* 11.3).",
        "Print colours measured: 2.",
        "Inked area: 13.4% of the print box, 30.0 x 22.5 cm.",
        "White base needed: yes.",
        "Recommended: White + blue halftone, 2 screens, mean dE00 2.41, 95% dE00 6.80.",
        "Rule: the white base is choked by 0.5 mm."
    )

    @Test
    fun numbers_ignoreHexColoursAndDigitsInsideWords() {
        assertEquals(listOf(2.0, 13.4, 0.5), ReportExplanation.numbers("2 screens on #1E1E1E, dE00 13.4% and L2 choke 0.5 mm"))
    }

    @Test
    fun anAnswerUsingOnlyEngineNumbers_isVerified() {
        val answer = "Two screens are enough: a white base choked by 0.5 mm, then blue as a halftone, " +
            "with 2 screens in total covering about 13% of the box."
        assertTrue(ReportExplanation.unverifiedNumbers(answer, facts).isEmpty())
    }

    @Test
    fun anInventedNumber_isCaught() {
        val answer = "Use 4 screens and a 1.5 mm choke for this 2-colour logo."
        assertEquals(listOf(4.0, 1.5), ReportExplanation.unverifiedNumbers(answer, facts))
    }

    @Test
    fun promptHasTheFacts_andTheTemplateAsksForNoNewNumbers() {
        val template = listOf(File("src/main/assets/${ReportExplanation.ASSET_PATH}"), File("app/src/main/assets/${ReportExplanation.ASSET_PATH}"))
            .first { it.exists() }.readText()
        assertTrue(template.contains("Do not add any number"))
        val prompt = ReportExplanation.build(template, facts)
        facts.forEach { assertTrue(prompt.contains(it)) }
        assertTrue(!prompt.contains("{facts}"))
    }
}
