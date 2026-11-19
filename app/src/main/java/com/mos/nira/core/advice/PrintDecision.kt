package com.mos.nira.core.advice

import org.json.JSONObject

/** "Feel wanted", the fourth brief question. */
enum class Feel(val tag: String?) { SOFT("soft"), RAISED("raised"), ANY(null) }

/** Where the order quantity falls against print_rules.json quantity_thresholds. */
enum class QuantityBand { DIGITAL, BETWEEN, SCREEN, NOT_SET, NOT_ANSWERED, BUYER_ORDER }

/** Where the white-base line on the card comes from. */
enum class BaseSource { ENGINE, DISCHARGE, DIGITAL }

/** print_rules.json "print_brief". No defaults: null when the section is missing. */
data class BriefRules(
    val fabrics: List<String>,
    val feelOfTechnique: Map<String, String>,
    val confidence: String,
    val verifiedByRavi: Boolean,
    /** Confidence labels of the rules the card also uses (recommendation, white_base). */
    val separationConfidence: String?,
    val baseConfidence: String?
)

/** print_rules.json "quantity_thresholds". A null limit means Ravi has not given it yet. */
data class QuantityRules(
    /** False (Ravi, 19 Nov 2026): quantity is the buyer's order and does not change the method. */
    val changesMethod: Boolean,
    val digitalBelowPieces: Int?,
    val screenFromPieces: Int?,
    val confidence: String,
    val verifiedByRavi: Boolean
)

/** The four tap answers (garment colour, fibre and fabric, quantity, feel). Null = not answered. */
data class BriefAnswers(
    val garmentHex: String? = null,
    val fibre: Fibre? = null,
    val fabric: String? = null,
    val quantity: Int? = null,
    val feel: Feel? = null
)

/** One screen from the engine (Parts B, C and D), as the card needs it. */
data class PlannedScreen(
    val name: String,
    val hex: String,
    val isBaseWhite: Boolean,
    /** Mesh text from screen_type_mesh_rule, or "not available". */
    val mesh: String,
    val meshConfidence: String?,
    /** Ink grams per garment from ink_consumption, or null when the rules file has no figure. */
    val gramsPerGarment: Double?,
    val gramsMin: Double?,
    val gramsMax: Double?
)

data class MethodChoice(val advice: TechniqueAdvice, val feelTag: String?, val fitsQuantity: Boolean?)

/** The one decision card. Every value comes from the engine, the rules files or the user's taps. */
data class DecisionCard(
    val answers: BriefAnswers,
    val garmentTone: GarmentTone?,
    val quantityBand: QuantityBand,
    val best: MethodChoice?,
    val second: MethodChoice?,
    /** A digital method for small runs, when the best method is not already one. */
    val smallRun: MethodChoice?,
    val separationName: String?,
    /** Screens to burn (empty for a digital method). */
    val screens: List<PlannedScreen>,
    val baseSource: BaseSource,
    /** True / false for screen methods; null for digital. */
    val baseNeeded: Boolean?,
    val gramsPerGarment: Double?,
    val gramsMin: Double?,
    val gramsMax: Double?,
    /** Screens with no ink figure in the rules file. */
    val inksWithoutGrams: List<String>
) {
    val isScreenMethod: Boolean get() = best?.advice?.group == SCREEN_GROUP
    val unanswered: Int get() = listOf(answers.garmentHex, answers.fibre, answers.quantity, answers.feel).count { it == null }

    companion object {
        const val SCREEN_GROUP = "screen_ink_system"
        const val DIGITAL_GROUP = "digital"
    }
}

/**
 * Builds the decision card from the brief answers, the technique advice (print_techniques.json),
 * print_rules.json print_brief and quantity_thresholds, and the engine's screens. Deterministic:
 * no AI, no invented values; anything the rules file lacks is reported as missing.
 */
object PrintDecision {

    fun parseBriefRules(root: JSONObject): BriefRules? = try {
        val r = root.getJSONObject("print_brief")
        val fabrics = r.getJSONArray("fabrics").let { a -> (0 until a.length()).map { a.getString(it) } }
        val feel = r.getJSONObject("feel_of_technique").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
        BriefRules(
            fabrics, feel, r.getString("confidence"), r.getBoolean("verified_by_ravi"),
            separationConfidence = root.optJSONObject("recommendation")?.optString("confidence")?.takeIf { it.isNotBlank() },
            baseConfidence = root.optJSONObject("white_base")?.optString("confidence")?.takeIf { it.isNotBlank() }
        )
    } catch (e: Exception) {
        null
    }

    fun parseQuantityRules(root: JSONObject): QuantityRules? = try {
        val r = root.getJSONObject("quantity_thresholds")
        fun intOrNull(key: String): Int? = if (!r.has(key) || r.isNull(key)) null else r.getInt(key)
        QuantityRules(
            r.getBoolean("quantity_changes_method"),
            intOrNull("digital_below_pieces"), intOrNull("screen_from_pieces"), r.getString("confidence"), r.getBoolean("verified_by_ravi")
        )
    } catch (e: Exception) {
        null
    }

    fun quantityBand(quantity: Int?, rules: QuantityRules?): QuantityBand {
        if (quantity == null) return QuantityBand.NOT_ANSWERED
        if (rules != null && !rules.changesMethod) return QuantityBand.BUYER_ORDER
        val below = rules?.digitalBelowPieces
        val from = rules?.screenFromPieces
        if (below == null || from == null) return QuantityBand.NOT_SET
        return when {
            quantity < below -> QuantityBand.DIGITAL
            quantity >= from -> QuantityBand.SCREEN
            else -> QuantityBand.BETWEEN
        }
    }

    /** Orders the methods by print_brief.method_order. Ruled-out methods are dropped. */
    fun rankMethods(advice: List<TechniqueAdvice>, band: QuantityBand, feel: Feel?, briefRules: BriefRules?): List<MethodChoice> {
        val choices = advice.filter { it.level != AdviceLevel.NOT_SUITABLE }.map { a ->
            val fits = when (band) {
                QuantityBand.DIGITAL -> a.group == DecisionCard.DIGITAL_GROUP
                QuantityBand.SCREEN -> a.group == DecisionCard.SCREEN_GROUP
                else -> null
            }
            MethodChoice(a, briefRules?.feelOfTechnique?.get(a.id), fits)
        }
        val fileOrder = advice.withIndex().associate { it.value.id to it.index }
        return choices.sortedWith(
            compareBy<MethodChoice>(
                { when (it.fitsQuantity) { true -> 0; null -> 1; false -> 2 } },
                { feelRank(it.feelTag, feel) },
                { fibreRank(it.advice) },
                { if (it.advice.verifiedByRavi) 0 else 1 },
                { it.advice.level.ordinal },
                { c -> c.advice.reasons.count { it is AdviceReason.FibreCaution || it is AdviceReason.GarmentCaution } },
                { fileOrder[it.advice.id] ?: Int.MAX_VALUE }
            )
        )
    }

    /** The chosen fibre listed as suitable, then no fibre data, then not listed. */
    private fun fibreRank(a: TechniqueAdvice): Int = when {
        a.reasons.any { it is AdviceReason.FibreSuitable } -> 0
        a.reasons.any { it is AdviceReason.FibreNoData } -> 1
        else -> 2
    }

    private fun feelRank(tag: String?, feel: Feel?): Int {
        val wanted = feel?.tag ?: return 0
        return when (tag) {
            wanted -> 0
            null -> 1
            else -> 2
        }
    }

    fun decide(
        answers: BriefAnswers,
        garmentTone: GarmentTone?,
        advice: List<TechniqueAdvice>,
        briefRules: BriefRules?,
        quantityRules: QuantityRules?,
        separationName: String?,
        engineScreens: List<PlannedScreen>
    ): DecisionCard {
        val band = quantityBand(answers.quantity, quantityRules)
        // Garment colour and fibre can rule a method out, so no method is named before both are answered.
        val ranked = if (garmentTone == null || answers.fibre == null) emptyList()
        else rankMethods(advice, band, answers.feel, briefRules)
        val best = ranked.getOrNull(0)
        val second = ranked.getOrNull(1)
        // A plain digital method (DTG, DTF...) for small runs, unless one is already shown.
        fun plainDigital(c: MethodChoice?) = c != null && c.advice.group == DecisionCard.DIGITAL_GROUP && "discharge" !in c.advice.id
        val smallRun = if (band == QuantityBand.SCREEN || plainDigital(best) || plainDigital(second)) null
        else ranked.drop(2).firstOrNull { plainDigital(it) }

        val isScreen = best?.advice?.group == DecisionCard.SCREEN_GROUP
        val isDischarge = best?.advice?.id?.contains("discharge") == true
        val baseSource = when {
            !isScreen -> BaseSource.DIGITAL
            isDischarge -> BaseSource.DISCHARGE
            else -> BaseSource.ENGINE
        }
        // Discharge removes the dye, so it needs no white base (print_techniques.json discharge).
        val screens = when (baseSource) {
            BaseSource.DIGITAL -> emptyList()
            BaseSource.DISCHARGE -> engineScreens.filter { !it.isBaseWhite }
            BaseSource.ENGINE -> engineScreens
        }
        val withGrams = screens.filter { it.gramsPerGarment != null }
        return DecisionCard(
            answers = answers,
            garmentTone = garmentTone,
            quantityBand = band,
            best = best,
            second = second,
            smallRun = smallRun,
            separationName = if (isScreen) separationName else null,
            screens = screens,
            baseSource = baseSource,
            baseNeeded = if (isScreen) screens.any { it.isBaseWhite } else null,
            gramsPerGarment = withGrams.takeIf { it.isNotEmpty() }?.sumOf { it.gramsPerGarment!! },
            gramsMin = withGrams.takeIf { it.isNotEmpty() }?.sumOf { it.gramsMin ?: it.gramsPerGarment!! },
            gramsMax = withGrams.takeIf { it.isNotEmpty() }?.sumOf { it.gramsMax ?: it.gramsPerGarment!! },
            inksWithoutGrams = screens.filter { it.gramsPerGarment == null }.map { it.name }
        )
    }
}
