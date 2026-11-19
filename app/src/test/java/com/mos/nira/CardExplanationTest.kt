package com.mos.nira

import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.BriefAnswers
import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.core.advice.PlannedScreen
import com.mos.nira.core.advice.PrintDecision
import com.mos.nira.core.advice.TechniqueAdvisor
import com.mos.nira.core.ai.CardExplanation
import com.mos.nira.core.ai.ReportExplanation
import com.mos.nira.core.image.GarmentPreview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardExplanationTest {

    private val techniques = TechniqueAdvisor.parseRules(
        listOf(File("src/main/assets/print_techniques.json"), File("app/src/main/assets/print_techniques.json")).first { it.exists() }.readText()
    )
    private val brief = PrintDecision.parseBriefRules(TestRules.root)!!
    private val quantity = PrintDecision.parseQuantityRules(TestRules.root)!!
    private val screens = listOf(
        PlannedScreen("Ink Blue", "#1F2A5C", false, "137–156 tpi", "sourced", 0.5, 0.3, 0.8),
        PlannedScreen("Deep Red", "#C0392B", false, "137–156 tpi", "sourced", null, null, null)
    )

    private fun card(tone: GarmentTone, hex: String) = PrintDecision.decide(
        BriefAnswers(hex, Fibre.COTTON, "single_jersey", 500, Feel.SOFT), tone,
        TechniqueAdvisor.advise(techniques, Fibre.COTTON, ArtworkKind.FLAT, tone), brief, quantity, "Flat Spot Screens (Solid)", screens
    )

    @Test
    fun factsCarryTheCardAndItsHonestyLabels() {
        val facts = CardExplanation.facts(card(GarmentTone.LIGHT, "#FFFFFF"), emptyList(), quantity, "cotton")
        val text = facts.joinToString("\n")
        assertTrue(text.contains("Brief: garment colour #FFFFFF; fibre cotton; fabric single jersey; quantity 500 pieces; feel wanted soft."))
        assertTrue(text.contains("Method: Water-based pigment print"))
        assertTrue(text.contains("not floor-verified"))
        assertTrue(text.contains("Quantity: 500 pieces, the buyer's order. Quantity does not change the method"))
        assertTrue(text.contains("Screen Ink Blue: mesh 137–156 tpi (sourced), ink 0.5 g per garment."))
        assertTrue(text.contains("Screen Deep Red: mesh 137–156 tpi (sourced), ink figure not available in the rules file."))
        assertTrue(text.contains("strike-off"))
        // Ink total for the order (0.5 g x 500 pieces).
        assertTrue(text.contains("Ink for 500 pieces: 250 g"))
    }

    @Test
    fun navyCardSaysVerifiedAndCarriesTheContrastWarning() {
        val warn = listOf(GarmentPreview.ContrastWarning("#1F2A5C", 7))
        val facts = CardExplanation.facts(card(GarmentTone.DARK_OR_COLOURED, "#001F3F"), warn, quantity, "cotton")
        val text = facts.joinToString("\n")
        assertTrue(text.contains("Contrast warning: ink #1F2A5C"))
        assertTrue(text.contains("Discharge + digital print (possible, see notes; verified by Ravi on the floor"))
        assertTrue(text.contains("Screens: 1 (one white discharge screen in the shape of the artwork)."))
        assertTrue(text.contains("How it is printed: Print white discharge"))
        assertTrue(text.contains("White base: no, discharge removes the garment dye."))
        assertTrue(text.contains("Risk: Reactive-dyed cotton only; discharge test first"))
    }

    @Test
    fun theNumberCheckCatchesInventedNumbers() {
        val facts = CardExplanation.facts(card(GarmentTone.LIGHT, "#FFFFFF"), emptyList(), quantity, "cotton")
        assertTrue(ReportExplanation.unverifiedNumbers("Print 500 pieces with 0.5 g of blue ink on 137 to 156 tpi mesh.", facts).isEmpty())
        assertEquals(listOf(200.0), ReportExplanation.unverifiedNumbers("Use a 200 tpi mesh.", facts))
    }

    @Test
    fun exportHoldsTheExactPrompt() {
        val facts = listOf("Method: X.")
        val json = JSONObject(CardExplanation.exportJson(facts, "PROMPT", "2026-11-19 10:00"))
        assertEquals("PROMPT", json.getString("prompt"))
        assertEquals(1, json.getJSONArray("facts").length())
        val template = listOf(File("src/main/assets/prompts/card_explain_v1.txt"), File("app/src/main/assets/prompts/card_explain_v1.txt")).first { it.exists() }.readText()
        assertTrue(CardExplanation.build(template, facts).contains("- Method: X."))
    }
}
