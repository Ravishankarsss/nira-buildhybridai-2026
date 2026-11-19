package com.mos.nira

import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.BriefAnswers
import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.core.advice.PlannedScreen
import com.mos.nira.core.advice.PrintDecision
import com.mos.nira.core.advice.TechniqueAdvisor
import com.mos.nira.core.ai.ReportExplanation
import com.mos.nira.core.commercial.CostDownKind
import com.mos.nira.core.commercial.CostMethod
import com.mos.nira.core.commercial.FactoryRates
import com.mos.nira.core.commercial.Missing
import com.mos.nira.core.commercial.PrintCost
import com.mos.nira.features.commercial.tradeOffFacts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Order cost and Cost-Down: deterministic arithmetic from the factory's own rates; nothing invented. */
class CommercialCostTest {

    private val rates = FactoryRates("₹", 450.0, 500.0, 3.5, 850.0, 45.0)
    private val costDownRules = PrintCost.parseCostDownRules(TestRules.root)!!
    private val techniques = TechniqueAdvisor.parseRules(
        listOf(File("src/main/assets/print_techniques.json"), File("app/src/main/assets/print_techniques.json")).first { it.exists() }.readText()
    )
    private val brief = PrintDecision.parseBriefRules(TestRules.root)!!
    private val quantity = PrintDecision.parseQuantityRules(TestRules.root)!!

    private fun scr(name: String, hex: String, g: Double?, base: Boolean = false, process: Boolean = false) =
        PlannedScreen(name, hex, base, "137–156 tpi", "sourced", g, g?.let { it * 0.8 }, g?.let { it * 1.2 }, process)

    /** A white-cotton card (water-based, a screen method) with the given screens and quantity. */
    private fun screenCard(screens: List<PlannedScreen>, qty: Int?) = PrintDecision.decide(
        BriefAnswers("#FFFFFF", Fibre.COTTON, null, qty, Feel.SOFT), GarmentTone.LIGHT,
        TechniqueAdvisor.advise(techniques, Fibre.COTTON, ArtworkKind.FLAT, GarmentTone.LIGHT), brief, quantity, "Flat Spot", screens
    )

    @Test
    fun costing_empty_rates_returns_null() {
        val card = screenCard(listOf(scr("Navy", "#1F2A5C", 1.0)), 500)
        assertNull(PrintCost.forCard(card, card.screens, FactoryRates()))
        assertTrue(PrintCost.costDown(card, card.screens, FactoryRates(), costDownRules).isEmpty())
        // One missing rate: no total, and the gap is named (never counted as zero).
        val r = PrintCost.screen(card.screens, 500, rates.copy(inkCostPerKg = null))
        assertNull(r.total)
        assertTrue(Missing.INK_PRICE in r.missing)
    }

    @Test
    fun costing_screen_vs_digital_breakeven() {
        // 2 screens, 2 g of ink per piece.
        val screens = listOf(scr("Navy", "#1F2A5C", 1.5), scr("Red", "#C0392B", 0.5))
        val s = PrintCost.screen(screens, 500, rates)
        assertEquals(2 * 450.0 + 500.0, s.fixed!!, 1e-9)                    // 1400
        assertEquals(2 * 3.5 + 2.0 / 1000 * 850.0, s.variablePerPc!!, 1e-9) // 8.7
        assertEquals(1400.0 + 8.7 * 500, s.total!!, 1e-9)                   // 5750
        assertEquals(5750.0 / 500, s.perPc!!, 1e-9)                         // 11.5
        val d = PrintCost.digital(500, rates)
        assertEquals(45.0 * 500, d.total!!, 1e-9)
        // Break-even = ceil(1400 / (45 - 8.7)) = ceil(38.56...) = 39 pieces.
        assertEquals(39, PrintCost.breakEvenQty(s, rates))
        // Digital cheaper than screen per piece: no break-even.
        assertNull(PrintCost.breakEvenQty(s, rates.copy(digitalRatePerPc = 8.0)))
    }

    @Test
    fun costdown_merge_closest_pair_reduces_screen_count() {
        // Navy and a near-navy blue are the closest pair; red is far from both.
        val screens = listOf(scr("Navy", "#1F2A5C", 1.5), scr("Blue", "#24306A", 0.2), scr("Red", "#C0392B", 0.5))
        val card = screenCard(screens, 500)
        val merge = PrintCost.costDown(card, screens, rates, costDownRules).first { it.kind == CostDownKind.MERGE_COLOURS }
        assertEquals(3, merge.before.screens)
        assertEquals(2, merge.after.screens)
        assertEquals("#24306A", merge.fromHex)  // the smaller colour goes
        assertEquals("#1F2A5C", merge.intoHex)
        assertTrue(merge.deltaE00!! <= costDownRules.closeMatchMaxDeltaE00)
        assertEquals(true, merge.closeMatch)
        // Ink weight is unchanged, so the saving is exactly one screen and its run charge.
        assertEquals((450.0 + 3.5 * 500) / 500, merge.savingPerPc, 1e-9)
        // Red and navy only: the merge is a visible shift that needs the buyer's approval.
        val two = listOf(scr("Navy", "#1F2A5C", 1.5), scr("Red", "#C0392B", 0.5))
        val visible = PrintCost.costDown(screenCard(two, 500), two, rates, costDownRules).first { it.kind == CostDownKind.MERGE_COLOURS }
        assertEquals(false, visible.closeMatch)
        // CMYK process screens are never merged.
        val cmyk = listOf(scr("Cyan", "#00AEEF", 0.3, process = true), scr("Magenta", "#EC008C", 0.3, process = true))
        assertTrue(PrintCost.costDown(screenCard(cmyk, 500), cmyk, rates, costDownRules).none { it.kind == CostDownKind.MERGE_COLOURS })
    }

    @Test
    fun costdown_ai_guardrail_checks_numbers() {
        val screens = listOf(scr("Navy", "#1F2A5C", 1.5), scr("Blue", "#24306A", 0.2), scr("Red", "#C0392B", 0.5))
        val card = screenCard(screens, 500)
        val cost = PrintCost.forCard(card, screens, rates)!!
        val options = PrintCost.costDown(card, screens, rates, costDownRules)
        val facts = tradeOffFacts(cost, options, rates)
        val merge = options.first { it.kind == CostDownKind.MERGE_COLOURS }
        val newPerPc = String.format(java.util.Locale.US, "%.2f", merge.after.perPc)
        val savingPerPc = String.format(java.util.Locale.US, "%.2f", merge.savingPerPc)
        // An answer quoting the exact engine numbers passes...
        assertTrue(ReportExplanation.unverifiedNumbers("Merging saves ₹$savingPerPc per piece, bringing it to ₹$newPerPc per piece.", facts).isEmpty())
        // ...and a made-up saving is caught.
        assertEquals(listOf(9.99), ReportExplanation.unverifiedNumbers("It saves ₹9.99 per piece.", facts))
        // Grouped amounts (Indian or Western) read as one number.
        assertEquals(listOf(192000.0), ReportExplanation.numbers("Total ₹1,92,000."))
        assertEquals(listOf(192000.0), ReportExplanation.numbers("Total 192,000."))
        assertTrue(facts.none { Regex("#[0-9A-Fa-f]{6}").containsMatchIn(it) })
    }

    @Test
    fun zeroQuantityAndMissingInkAreReportedNotHidden() {
        val screens = listOf(scr("Navy", "#1F2A5C", 1.0), scr("Red", "#C0392B", null))
        val zero = PrintCost.screen(screens, 0, rates)
        assertNull(zero.total)
        assertTrue(Missing.QUANTITY in zero.missing)
        val partial = PrintCost.screen(screens, 100, rates)
        assertNotNull(partial.total)       // shown, but...
        assertFalse(partial.complete)      // ...marked incomplete
        assertEquals(listOf("Red"), partial.inksWithoutGrams)
    }

    @Test
    fun dischargeDigitalIsOneScreenPlusDigitalAndNeverComplete() {
        val r = PrintCost.dischargeDigital(500, rates)
        assertEquals(CostMethod.DISCHARGE_DIGITAL, r.method)
        assertEquals(1, r.screens)
        assertEquals(450.0 + 500.0, r.fixed!!, 1e-9)
        assertEquals(3.5 + 45.0, r.variablePerPc!!, 1e-9)
        assertTrue(Missing.INK_GRAMS in r.missing) // discharge paste weight is not in the rules file
        assertFalse(r.complete)
    }

    @Test
    fun lightGarmentDropsTheUnderbaseAndIsABuyerDecision() {
        val screens = listOf(scr("Base White", "#FFFFFF", 2.0, base = true), scr("Navy", "#1F2A5C", 1.0), scr("Red", "#C0392B", 0.5))
        val card = screenCard(screens, 500)
        val light = PrintCost.costDown(card, screens, rates, costDownRules).first { it.kind == CostDownKind.LIGHT_GARMENT }
        assertEquals(2, light.after.screens)
        val expected = (450.0 + 3.5 * 500 + 2.0 / 1000 * 850.0 * 500) / 500
        assertEquals(expected, light.savingPerPc, 1e-9)
    }

    @Test
    fun costDownRulesComeFromTheRulesFile() {
        assertEquals(12.0, costDownRules.closeMatchMaxDeltaE00, 0.0)
        assertEquals(5.0, costDownRules.methodSwitchMinSavingPercent, 0.0)
        assertNull(PrintCost.parseCostDownRules(JSONObject("{}")))
    }

    @Test
    fun sampleRatesFileMatchesTheBrief() {
        val j = JSONObject(listOf(File("src/main/assets/sample_factory_rates.json"), File("app/src/main/assets/sample_factory_rates.json")).first { it.exists() }.readText())
        assertEquals("₹", j.getString("currency"))
        assertEquals(450.0, j.getDouble("screen_make_cost"), 0.0)
        assertEquals(45.0, j.getDouble("digital_rate_per_pc"), 0.0)
    }
}
