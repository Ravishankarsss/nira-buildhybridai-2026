package com.mos.nira.core.advice

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

/** Fabric fibre chosen by the user. AI does not guess the fibre from a photo. */
enum class Fibre(val rulesNames: Set<String>) {
    COTTON(setOf("cotton")),
    POLYESTER(setOf("polyester")),
    COTTON_POLY_BLEND(setOf("cotton_polyester_blend", "polyester/cotton blends", "polyester/cotton blend", "blends")),
    LINEN(setOf("linen")),
    NYLON(setOf("nylon"))
}

/** Garment colour class, from the white_base rule in print_rules.json. */
enum class GarmentTone { LIGHT, DARK_OR_COLOURED }

/** Artwork class used by the rules file (artwork_fit). */
enum class ArtworkKind { FLAT, TONAL }

enum class AdviceLevel { RECOMMENDED, POSSIBLE, NOT_SUITABLE }

enum class Severity { OK, CAUTION, UNKNOWN, BLOCK }

/** One reason behind a level. The UI turns each into a sentence from string resources. */
sealed class AdviceReason(val severity: Severity) {
    data class FibreSuitable(val entry: String) : AdviceReason(Severity.OK)
    data class FibreNotSuitable(val entry: String) : AdviceReason(Severity.BLOCK)
    data class FibreCaution(val note: String) : AdviceReason(Severity.CAUTION)
    data class FibreNotListed(val fibre: Fibre) : AdviceReason(Severity.CAUTION)
    data object FibreNoData : AdviceReason(Severity.UNKNOWN)
    data object FibreNotChosen : AdviceReason(Severity.UNKNOWN)
    data object ArtworkSolidOnly : AdviceReason(Severity.BLOCK)
    data object ArtworkSolidPreferred : AdviceReason(Severity.CAUTION)
    data object ArtworkFits : AdviceReason(Severity.OK)
    data object ArtworkNotChosen : AdviceReason(Severity.UNKNOWN)
    data object GarmentLightOnly : AdviceReason(Severity.BLOCK)
    data object GarmentDarkOnly : AdviceReason(Severity.BLOCK)
    data class GarmentCaution(val note: String) : AdviceReason(Severity.CAUTION)
    data class GarmentNote(val note: String) : AdviceReason(Severity.CAUTION)
    data object GarmentNotChosen : AdviceReason(Severity.UNKNOWN)
}

data class TechniqueAdvice(
    val id: String,
    val name: String,
    val group: String,
    val whatItIs: String,
    val level: AdviceLevel,
    val reasons: List<AdviceReason>,
    val risks: List<String>,
    /** Confidence label from the rules file, or null if the file gives none. */
    val confidence: String?,
    val verifiedByRavi: Boolean
)

/** One technique as read from print_techniques.json. */
data class TechniqueRule(
    val id: String,
    val name: String,
    val group: String,
    val whatItIs: String,
    val artworkFit: String,
    val suitableFibres: List<String>,
    val notSuitableFibres: List<String>,
    val cautions: List<String>,
    val garmentColour: String?,
    val risks: List<String>,
    val confidence: String?,
    val verifiedByRavi: Boolean
)

/**
 * Suggests print methods from assets/print_techniques.json only. Deterministic: no AI, no
 * invented values. A technique is RECOMMENDED only when every check the rules file supports
 * passes; missing data makes it POSSIBLE, never RECOMMENDED; any hard rule makes it NOT_SUITABLE.
 */
object TechniqueAdvisor {

    /**
     * Groups offered as a base print method. Special effects, separation routes and fabric
     * printing are left out: effects are add-ons, routes are in the Print Report.
     */
    val METHOD_GROUPS = setOf("screen_ink_system", "digital")

    fun parseRules(json: String): List<TechniqueRule> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("techniques") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            TechniqueRule(
                id = t.getString("id"),
                name = t.optString("name", t.getString("id")),
                group = t.optString("group"),
                whatItIs = t.optString("what_it_is"),
                artworkFit = t.optString("artwork_fit"),
                suitableFibres = strings(t.optJSONArray("suitable_fibres")),
                notSuitableFibres = strings(t.optJSONArray("not_suitable_fibres")),
                cautions = strings(t.optJSONArray("cautions_fibres")),
                garmentColour = t.optString("garment_colour").takeIf { it.isNotBlank() },
                risks = strings(t.optJSONArray("risks")),
                confidence = t.optString("confidence").takeIf { it.isNotBlank() },
                verifiedByRavi = t.optBoolean("verified_by_ravi", false)
            )
        }
    }

    /**
     * Garment class from the white_base rule: dark or coloured when lightness is below the
     * limit or chroma is above the limit (both limits come from print_rules.json).
     */
    fun garmentTone(l: Double, a: Double, b: Double, lightnessLimit: Double, chromaLimit: Double): GarmentTone =
        if (l < lightnessLimit || hypot(a, b) > chromaLimit) GarmentTone.DARK_OR_COLOURED else GarmentTone.LIGHT

    fun advise(
        rules: List<TechniqueRule>,
        fibre: Fibre?,
        artwork: ArtworkKind?,
        garment: GarmentTone?
    ): List<TechniqueAdvice> =
        rules.filter { it.group in METHOD_GROUPS }
            .map { rule -> adviseOne(rule, fibre, artwork, garment) }
            .sortedBy { it.level.ordinal } // stable: keeps the rules-file order inside each level

    private fun adviseOne(rule: TechniqueRule, fibre: Fibre?, artwork: ArtworkKind?, garment: GarmentTone?): TechniqueAdvice {
        val reasons = ArrayList<AdviceReason>()
        reasons += fibreReasons(rule, fibre)
        reasons += artworkReason(rule, artwork)
        reasons += garmentReasons(rule, garment)

        val level = when {
            reasons.any { it.severity == Severity.BLOCK } -> AdviceLevel.NOT_SUITABLE
            reasons.any { it.severity == Severity.CAUTION || it.severity == Severity.UNKNOWN } -> AdviceLevel.POSSIBLE
            else -> AdviceLevel.RECOMMENDED
        }
        return TechniqueAdvice(
            id = rule.id,
            name = rule.name,
            group = rule.group,
            whatItIs = rule.whatItIs,
            level = level,
            reasons = reasons,
            risks = rule.risks,
            confidence = rule.confidence,
            verifiedByRavi = rule.verifiedByRavi
        )
    }

    /** "cotton (100%, reactive-dyed)" -> "cotton"; "polyester: use low-cure ink" -> "polyester". */
    private fun base(entry: String): String =
        entry.lowercase().substringBefore(" (").substringBefore(":").trim()

    private fun matches(entry: String, fibre: Fibre) = base(entry) in fibre.rulesNames

    private fun isFibreName(entry: String) = Fibre.values().any { matches(entry, it) }

    private fun noteOf(entry: String) = entry.substringAfter(":", entry).trim()

    private fun fibreReasons(rule: TechniqueRule, fibre: Fibre?): List<AdviceReason> {
        if (fibre == null) return listOf(AdviceReason.FibreNotChosen)
        val out = ArrayList<AdviceReason>()
        val notSuitable = rule.notSuitableFibres.firstOrNull { matches(it, fibre) }
        val suitable = rule.suitableFibres.firstOrNull { matches(it, fibre) }
        when {
            notSuitable != null -> out += AdviceReason.FibreNotSuitable(notSuitable)
            suitable != null -> out += AdviceReason.FibreSuitable(suitable)
            rule.suitableFibres.isEmpty() && rule.notSuitableFibres.isEmpty() -> out += AdviceReason.FibreNoData
            else -> out += AdviceReason.FibreNotListed(fibre)
        }
        rule.cautions.filter { matches(it, fibre) }.forEach { out += AdviceReason.FibreCaution(noteOf(it)) }
        return out
    }

    private fun artworkReason(rule: TechniqueRule, artwork: ArtworkKind?): AdviceReason = when {
        artwork == null -> AdviceReason.ArtworkNotChosen
        artwork == ArtworkKind.FLAT -> AdviceReason.ArtworkFits
        rule.artworkFit == "SOLID_ONLY" -> AdviceReason.ArtworkSolidOnly
        rule.artworkFit == "SOLID_PREFERRED" -> AdviceReason.ArtworkSolidPreferred
        else -> AdviceReason.ArtworkFits
    }

    private fun garmentReasons(rule: TechniqueRule, garment: GarmentTone?): List<AdviceReason> {
        val out = ArrayList<AdviceReason>()
        // Cautions that are about the garment colour, not a fibre (for example "dark garments: ...").
        val garmentCautions = rule.cautions.filter { !isFibreName(it) && base(it).contains("dark garment") }
        val text = rule.garmentColour?.lowercase()

        if (garment == null) {
            if (text != null || garmentCautions.isNotEmpty()) out += AdviceReason.GarmentNotChosen
            return out
        }
        when {
            text == null -> Unit
            text.contains("white or light only") ->
                if (garment == GarmentTone.DARK_OR_COLOURED) out += AdviceReason.GarmentLightOnly
            text.startsWith("dark or medium") -> {
                if (garment == GarmentTone.LIGHT) out += AdviceReason.GarmentDarkOnly
                val extra = rule.garmentColour!!.substringAfter(",", "").trim()
                if (extra.isNotEmpty()) out += AdviceReason.GarmentNote(extra)
            }
            text.startsWith("dark (also works on light)") -> Unit
            else -> out += AdviceReason.GarmentNote(rule.garmentColour!!)
        }
        if (garment == GarmentTone.DARK_OR_COLOURED) {
            garmentCautions.forEach { out += AdviceReason.GarmentCaution(noteOf(it)) }
        }
        return out
    }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
}
