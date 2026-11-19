package com.mos.nira

import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.BaseSource
import com.mos.nira.core.advice.BriefAnswers
import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.core.advice.PlannedScreen
import com.mos.nira.core.advice.PrintDecision
import com.mos.nira.core.advice.QuantityBand
import com.mos.nira.core.advice.QuantityRules
import com.mos.nira.core.advice.TechniqueAdvisor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrintDecisionTest {

    private val techniques = TechniqueAdvisor.parseRules(
        listOf(File("src/main/assets/print_techniques.json"), File("app/src/main/assets/print_techniques.json")).first { it.exists() }.readText()
    )
    private val brief = PrintDecision.parseBriefRules(TestRules.root)!!
    private val quantity = PrintDecision.parseQuantityRules(TestRules.root)!!

    private val screens = listOf(
        PlannedScreen("Base White (Underbase)", "#FFFFFF", true, "110–125 tpi", "sourced", 2.0, 1.5, 2.5),
        PlannedScreen("Navy", "#1F2A5C", false, "137–156 tpi", "sourced", 1.0, 0.8, 1.2),
        PlannedScreen("Red", "#C0392B", false, "137–156 tpi", "sourced", null, null, null)
    )

    private fun card(tone: GarmentTone, answers: BriefAnswers, q: QuantityRules = quantity) = PrintDecision.decide(
        answers = answers,
        garmentTone = tone,
        advice = TechniqueAdvisor.advise(techniques, answers.fibre, ArtworkKind.TONAL, tone),
        briefRules = brief,
        quantityRules = q,
        separationName = "Flat Spot",
        engineScreens = screens
    )

    @Test
    fun rulesLoadAndQuantityLimitsAreNotInvented() {
        assertTrue(brief.fabrics.contains("single_jersey"))
        assertEquals("soft", brief.feelOfTechnique["discharge_digital"])
        // Ravi, 19 Nov: quantity is the buyer's order and does not change the method.
        assertEquals(false, quantity.changesMethod)
        assertNull(quantity.digitalBelowPieces)
        assertNull(quantity.screenFromPieces)
        assertEquals(QuantityBand.BUYER_ORDER, PrintDecision.quantityBand(50, quantity))
        assertEquals(QuantityBand.NOT_SET, PrintDecision.quantityBand(50, quantity.copy(changesMethod = true)))
        assertEquals(QuantityBand.NOT_ANSWERED, PrintDecision.quantityBand(null, quantity))
        assertNull(PrintDecision.parseBriefRules(JSONObject("{}")))
    }

    @Test
    fun noMethodBeforeGarmentAndFibreAreAnswered() {
        val noFibre = card(GarmentTone.DARK_OR_COLOURED, BriefAnswers("#001F3F", null, null, 500, Feel.SOFT))
        assertNull(noFibre.best)
        val noGarment = PrintDecision.decide(
            BriefAnswers(null, Fibre.COTTON, null, null, null), null,
            TechniqueAdvisor.advise(techniques, Fibre.COTTON, ArtworkKind.TONAL, null), brief, quantity, null, screens
        )
        assertNull(noGarment.best)
    }

    @Test
    fun dischargeDigitalIsInTheTechniquesFile() {
        val t = techniques.first { it.id == "discharge_digital" }
        assertTrue(t.verifiedByRavi)
        assertEquals("digital", t.group)
        assertTrue(t.risks.any { it.contains("discharge test first") })
        // Ravi, 19 Nov: one white discharge screen in the shape of the artwork, then digital.
        assertEquals(1, t.screens)
        assertEquals(3, t.processSteps.size)
    }

    @Test
    fun navyCottonGivesDischargeDigitalThenDischargeScreen() {
        val c = card(GarmentTone.DARK_OR_COLOURED, BriefAnswers("#001F3F", Fibre.COTTON, "single_jersey", 500, Feel.SOFT))
        assertEquals("discharge_digital", c.best!!.advice.id)
        assertEquals("discharge", c.second!!.advice.id)
        assertNotNull(c.smallRun)
        assertEquals("digital", c.smallRun!!.advice.group)
        // Digital: no screens on the card.
        assertEquals(BaseSource.DIGITAL, c.baseSource)
        assertTrue(c.screens.isEmpty())
    }

    @Test
    fun whiteCottonSoftGivesWaterBasedAndNoDischarge() {
        val c = card(GarmentTone.LIGHT, BriefAnswers("#FFFFFF", Fibre.COTTON, "single_jersey", 500, Feel.SOFT))
        assertEquals("water_based_pigment", c.best!!.advice.id)
        assertFalse(listOfNotNull(c.best, c.second, c.smallRun).any { "discharge" in it.advice.id })
        assertEquals(BaseSource.ENGINE, c.baseSource)
        assertEquals(3, c.screens.size)
        assertEquals(true, c.baseNeeded)
        // Ink total counts only screens with a figure in the rules file; the rest are named.
        assertEquals(3.0, c.gramsPerGarment!!, 1e-9)
        assertEquals(listOf("Red"), c.inksWithoutGrams)
    }

    @Test
    fun aFibreNotListedDoesNotLeadEvenWhenVerified() {
        val c = card(GarmentTone.DARK_OR_COLOURED, BriefAnswers("#001F3F", Fibre.LINEN, null, 500, Feel.SOFT))
        assertEquals("water_based_pigment", c.best!!.advice.id)
    }

    @Test
    fun polyesterNeverGetsDischarge() {
        val c = card(GarmentTone.DARK_OR_COLOURED, BriefAnswers("#000000", Fibre.POLYESTER, null, null, Feel.ANY))
        assertFalse(listOfNotNull(c.best, c.second, c.smallRun).any { "discharge" in it.advice.id })
    }

    @Test
    fun dischargeScreenDropsTheWhiteBase() {
        // Without a soft-feel tag for discharge_digital the screen discharge can lead; check the base rule directly.
        val ranked = PrintDecision.rankMethods(
            TechniqueAdvisor.advise(techniques.filter { it.id != "discharge_digital" }, Fibre.COTTON, ArtworkKind.TONAL, GarmentTone.DARK_OR_COLOURED),
            QuantityBand.NOT_SET, Feel.SOFT, brief
        )
        assertEquals("discharge", ranked.first().advice.id)
        val c = PrintDecision.decide(
            BriefAnswers("#001F3F", Fibre.COTTON, null, 1000, Feel.SOFT), GarmentTone.DARK_OR_COLOURED,
            TechniqueAdvisor.advise(techniques.filter { it.id != "discharge_digital" }, Fibre.COTTON, ArtworkKind.TONAL, GarmentTone.DARK_OR_COLOURED),
            brief, quantity, "Flat Spot", screens
        )
        assertEquals(BaseSource.DISCHARGE, c.baseSource)
        assertEquals(false, c.baseNeeded)
        assertTrue(c.screens.none { it.isBaseWhite })
    }

    @Test
    fun quantityBandsMoveTheMethodOnceRaviGivesTheLimits() {
        val q = quantity.copy(changesMethod = true, digitalBelowPieces = 100, screenFromPieces = 300)
        assertEquals(QuantityBand.DIGITAL, PrintDecision.quantityBand(50, q))
        assertEquals(QuantityBand.BETWEEN, PrintDecision.quantityBand(150, q))
        assertEquals(QuantityBand.SCREEN, PrintDecision.quantityBand(300, q))
        val small = card(GarmentTone.LIGHT, BriefAnswers("#FFFFFF", Fibre.COTTON, null, 20, Feel.ANY), q)
        assertEquals("digital", small.best!!.advice.group)
        val bulk = card(GarmentTone.LIGHT, BriefAnswers("#FFFFFF", Fibre.COTTON, null, 5000, Feel.ANY), q)
        assertEquals("screen_ink_system", bulk.best!!.advice.group)
        assertNull(bulk.smallRun)
    }
}
