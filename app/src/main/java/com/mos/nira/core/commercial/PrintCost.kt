package com.mos.nira.core.commercial

import com.mos.nira.core.advice.DecisionCard
import com.mos.nira.core.advice.PlannedScreen
import com.mos.nira.core.image.ColorConverter
import kotlin.math.ceil

/**
 * A factory's own rates, typed once in Settings and kept on the phone. NIRA never invents a
 * price: a missing rate stays null and every result that needs it says so.
 */
data class FactoryRates(
    val currency: String = "",
    /** Screen making and exposing, per screen. */
    val screenMakeCost: Double? = null,
    /** Machine set-up and registration, per job. */
    val jobSetupCost: Double? = null,
    /** Printing labour / run charge, per screen per piece. */
    val printRunPerScreen: Double? = null,
    /** Ink, per kg. */
    val inkCostPerKg: Double? = null,
    /** Digital (DTG / DTF) print rate, per piece. */
    val digitalRatePerPc: Double? = null,
    /** True while the rates are the "Load sample rates" values and nothing has been edited. */
    val isSample: Boolean = false
) {
    val isEmpty: Boolean
        get() = screenMakeCost == null && jobSetupCost == null && printRunPerScreen == null &&
            inkCostPerKg == null && digitalRatePerPc == null
}

/** Which rate or engine figure a result could not use. */
enum class Missing { QUANTITY, SCREEN_MAKE, JOB_SETUP, PRINT_RUN, INK_PRICE, INK_GRAMS, DIGITAL_RATE }

enum class CostMethod { SCREEN, DIGITAL, DISCHARGE_DIGITAL }

/**
 * One costed way of printing the job. A null amount means it could not be worked out; [missing]
 * says why. Partial totals are never shown as complete: [complete] is false when anything is
 * missing.
 */
data class CostResult(
    val method: CostMethod,
    val screens: Int,
    val quantity: Int?,
    val fixed: Double?,
    val variablePerPc: Double?,
    val inkCostPerPc: Double?,
    val total: Double?,
    val perPc: Double?,
    val missing: List<Missing>,
    /** Screens whose ink grams are not in the rules file (their ink is not costed). */
    val inksWithoutGrams: List<String> = emptyList()
) {
    val complete: Boolean get() = missing.isEmpty() && total != null
}

/** print_rules.json "cost_down". No defaults: null when the section is missing. */
data class CostDownRules(
    val closeMatchMaxDeltaE00: Double,
    val methodSwitchMinSavingPercent: Double,
    val confidence: String,
    val verifiedByRavi: Boolean
)

enum class CostDownKind { MERGE_COLOURS, SWITCH_METHOD, LIGHT_GARMENT }

data class CostDownOption(
    val kind: CostDownKind,
    val before: CostResult,
    val after: CostResult,
    /** Merge: the colour that goes and the colour it joins. */
    val fromHex: String? = null,
    val intoHex: String? = null,
    val deltaE00: Double? = null,
    /** Merge: within the close-match limit; otherwise the buyer must approve the shift. */
    val closeMatch: Boolean? = null,
    /** Method switch: the break-even quantity between screen and digital, when it exists. */
    val breakEvenQty: Int? = null
) {
    val savingPerPc: Double get() = (before.perPc ?: 0.0) - (after.perPc ?: 0.0)
    val savingTotal: Double get() = (before.total ?: 0.0) - (after.total ?: 0.0)
}

/**
 * Order cost and Cost-Down options, from the decision card's screens and ink (the existing Part D
 * figures) and the factory's own rates. Pure and deterministic: no AI, no invented prices.
 */
object PrintCost {

    fun methodOf(card: DecisionCard): CostMethod? {
        val a = card.best?.advice ?: return null
        return when {
            a.id == "discharge_digital" -> CostMethod.DISCHARGE_DIGITAL
            a.group == DecisionCard.DIGITAL_GROUP -> CostMethod.DIGITAL
            a.group == DecisionCard.SCREEN_GROUP -> CostMethod.SCREEN
            else -> null
        }
    }

    /**
     * Screen print: fixed = screens x screen making + job set-up; per piece = screens x run charge
     * + ink grams / 1000 x ink price; total = fixed + per piece x quantity.
     */
    fun screen(screens: List<PlannedScreen>, quantity: Int?, rates: FactoryRates): CostResult {
        val n = screens.size
        val missing = ArrayList<Missing>()
        val qty = quantity?.takeIf { it > 0 }
        if (qty == null) missing += Missing.QUANTITY
        if (rates.screenMakeCost == null) missing += Missing.SCREEN_MAKE
        if (rates.jobSetupCost == null) missing += Missing.JOB_SETUP
        if (rates.printRunPerScreen == null) missing += Missing.PRINT_RUN
        if (rates.inkCostPerKg == null) missing += Missing.INK_PRICE
        val withoutGrams = screens.filter { it.gramsPerGarment == null }.map { it.name }
        if (withoutGrams.isNotEmpty()) missing += Missing.INK_GRAMS
        val grams = screens.mapNotNull { it.gramsPerGarment }.sum()

        val fixed = if (rates.screenMakeCost != null && rates.jobSetupCost != null) n * rates.screenMakeCost + rates.jobSetupCost else null
        val ink = rates.inkCostPerKg?.let { grams / 1000.0 * it }
        val variable = if (rates.printRunPerScreen != null && ink != null) n * rates.printRunPerScreen + ink else null
        val total = if (fixed != null && variable != null && qty != null) fixed + variable * qty else null
        return CostResult(CostMethod.SCREEN, n, qty, fixed, variable, ink, total, total?.let { it / qty!! }, missing, withoutGrams)
    }

    /** Digital (DTG / DTF): total = digital rate x quantity. No screens, no set-up. */
    fun digital(quantity: Int?, rates: FactoryRates): CostResult {
        val qty = quantity?.takeIf { it > 0 }
        val missing = ArrayList<Missing>()
        if (qty == null) missing += Missing.QUANTITY
        if (rates.digitalRatePerPc == null) missing += Missing.DIGITAL_RATE
        val total = if (qty != null && rates.digitalRatePerPc != null) rates.digitalRatePerPc * qty else null
        return CostResult(CostMethod.DIGITAL, 0, qty, 0.0, rates.digitalRatePerPc, null, total, total?.let { it / qty!! }, missing)
    }

    /**
     * Discharge + digital (Ravi, 19 Nov): one white discharge screen, then a digital print. Fixed =
     * 1 screen + set-up; per piece = 1 x run charge + digital rate. The discharge paste weight is
     * not in the rules file, so its cost is reported as missing, never as zero.
     */
    fun dischargeDigital(quantity: Int?, rates: FactoryRates): CostResult {
        val qty = quantity?.takeIf { it > 0 }
        val missing = ArrayList<Missing>()
        if (qty == null) missing += Missing.QUANTITY
        if (rates.screenMakeCost == null) missing += Missing.SCREEN_MAKE
        if (rates.jobSetupCost == null) missing += Missing.JOB_SETUP
        if (rates.printRunPerScreen == null) missing += Missing.PRINT_RUN
        if (rates.digitalRatePerPc == null) missing += Missing.DIGITAL_RATE
        missing += Missing.INK_GRAMS
        val fixed = if (rates.screenMakeCost != null && rates.jobSetupCost != null) rates.screenMakeCost + rates.jobSetupCost else null
        val variable = if (rates.printRunPerScreen != null && rates.digitalRatePerPc != null) rates.printRunPerScreen + rates.digitalRatePerPc else null
        val total = if (fixed != null && variable != null && qty != null) fixed + variable * qty else null
        return CostResult(CostMethod.DISCHARGE_DIGITAL, 1, qty, fixed, variable, null, total, total?.let { it / qty!! }, missing, listOf("discharge paste"))
    }

    /** The order cost of the card's best method; null when there is no method or no rates at all. */
    fun forCard(card: DecisionCard, engineScreens: List<PlannedScreen>, rates: FactoryRates): CostResult? {
        if (rates.isEmpty) return null
        return when (methodOf(card) ?: return null) {
            CostMethod.SCREEN -> screen(card.screens.ifEmpty { engineScreens }, card.answers.quantity, rates)
            CostMethod.DIGITAL -> digital(card.answers.quantity, rates)
            CostMethod.DISCHARGE_DIGITAL -> dischargeDigital(card.answers.quantity, rates)
        }
    }

    /** Quantity above which screen printing costs less than digital; null when it never does. */
    fun breakEvenQty(screen: CostResult, rates: FactoryRates): Int? {
        val fixed = screen.fixed ?: return null
        val variable = screen.variablePerPc ?: return null
        val digital = rates.digitalRatePerPc ?: return null
        if (digital <= variable) return null
        return ceil(fixed / (digital - variable)).toInt()
    }

    /**
     * The three Cost-Down rules. An option is listed only when both costs are complete and it
     * saves money. [engineScreens] are the engine's screens even when the best method is digital.
     */
    fun costDown(card: DecisionCard, engineScreens: List<PlannedScreen>, rates: FactoryRates, rules: CostDownRules?): List<CostDownOption> {
        if (rules == null || rates.isEmpty) return emptyList()
        val qty = card.answers.quantity?.takeIf { it > 0 } ?: return emptyList()
        val method = methodOf(card) ?: return emptyList()
        val out = ArrayList<CostDownOption>()

        if (method == CostMethod.SCREEN) {
            val screens = card.screens.ifEmpty { engineScreens }
            val before = screen(screens, qty, rates)

            // Rule 1: merge the two closest print colours (never the white underbase).
            val colours = screens.filter { !it.isBaseWhite && !it.isProcess }
            if (colours.size >= 2 && before.complete) {
                var best: Triple<PlannedScreen, PlannedScreen, Double>? = null
                for (i in colours.indices) for (j in i + 1 until colours.size) {
                    val d = ColorConverter.deltaE00(lab(colours[i].hex), lab(colours[j].hex))
                    if (best == null || d < best.third) best = Triple(colours[i], colours[j], d)
                }
                val (a, b, d) = best!!
                // The smaller colour (by ink) merges into the larger one; its ink weight moves with it.
                val (from, into) = if ((a.gramsPerGarment ?: 0.0) <= (b.gramsPerGarment ?: 0.0)) a to b else b to a
                val merged = screens.filter { it !== from && it !== into } + into.copy(
                    gramsPerGarment = (into.gramsPerGarment ?: 0.0) + (from.gramsPerGarment ?: 0.0),
                    gramsMin = into.gramsMin?.let { it + (from.gramsMin ?: 0.0) },
                    gramsMax = into.gramsMax?.let { it + (from.gramsMax ?: 0.0) }
                )
                val after = screen(merged, qty, rates)
                val option = CostDownOption(CostDownKind.MERGE_COLOURS, before, after, from.hex, into.hex, d, d <= rules.closeMatchMaxDeltaE00)
                if (after.complete && option.savingPerPc > 0) out += option
            }

            // Rule 2: switch to digital for this quantity.
            val digital = digital(qty, rates)
            if (before.complete && digital.complete && cheaperBy(before, digital, rules)) {
                out += CostDownOption(CostDownKind.SWITCH_METHOD, before, digital, breakEvenQty = breakEvenQty(before, rates))
            }

            // Rule 3: a light garment needs no white underbase (a buyer decision, not the factory's).
            if (screens.any { it.isBaseWhite } && before.complete) {
                val after = screen(screens.filter { !it.isBaseWhite }, qty, rates)
                val option = CostDownOption(CostDownKind.LIGHT_GARMENT, before, after)
                if (after.complete && option.savingPerPc > 0) out += option
            }
        } else {
            // Rule 2 the other way: screen printing for this quantity, using the engine's screens.
            val before = if (method == CostMethod.DIGITAL) digital(qty, rates) else dischargeDigital(qty, rates)
            val screenCost = screen(engineScreens, qty, rates)
            if (before.complete && screenCost.complete && engineScreens.isNotEmpty() && cheaperBy(before, screenCost, rules)) {
                out += CostDownOption(CostDownKind.SWITCH_METHOD, before, screenCost, breakEvenQty = breakEvenQty(screenCost, rates))
            }
        }
        return out.sortedByDescending { it.savingPerPc }
    }

    private fun cheaperBy(before: CostResult, after: CostResult, rules: CostDownRules): Boolean {
        val b = before.total ?: return false
        val a = after.total ?: return false
        return b > 0 && (b - a) / b * 100.0 >= rules.methodSwitchMinSavingPercent
    }

    private fun lab(hex: String) = ColorConverter.hexToRgb(hex).let { ColorConverter.rgbToLab(it.r, it.g, it.b) }

    fun parseCostDownRules(root: org.json.JSONObject): CostDownRules? = try {
        val r = root.getJSONObject("cost_down")
        CostDownRules(
            r.getDouble("close_match_max_de00"),
            r.getDouble("method_switch_min_saving_percent"),
            r.getString("confidence"),
            r.getBoolean("verified_by_ravi")
        )
    } catch (e: Exception) {
        null
    }
}
