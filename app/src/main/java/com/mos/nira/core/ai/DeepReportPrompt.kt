package com.mos.nira.core.ai

import com.mos.nira.core.advice.AdviceLevel
import com.mos.nira.core.advice.AdviceReason
import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.core.advice.TechniqueAdvice
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PartDResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import java.util.Locale

enum class ReportLanguage(val promptName: String) { ENGLISH("English"), TAMIL("Tamil") }

/** What the user chose for the print method advice, plus the advice itself. */
data class MethodAdviceFacts(
    val fibre: Fibre?,
    val artwork: ArtworkKind?,
    val garment: GarmentTone?,
    val advice: List<TechniqueAdvice>
)

/**
 * Builds the deep report request for the cloud model. Only text and numbers go into it:
 * the engine's measurements, the AI read text and the rule-based method advice.
 * The artwork image is never part of this request.
 */
object DeepReportPrompt {

    const val ASSET_PATH = "prompts/deep_report_v1.txt"

    private const val MAX_COLOURS = 12
    private const val MAX_METHODS_PER_LEVEL = 4

    fun build(template: String, facts: String, language: ReportLanguage): String =
        template.replace("{language}", language.promptName).replace("{facts}", facts.trim()).trim()

    /** Plain-text facts. Every number comes from the engine results passed in. */
    fun facts(
        partA: PartAResult?,
        partB: PartBResult?,
        partC: PartCResult?,
        partD: PartDResult?,
        inputs: PrintJobInputs,
        aiRead: ArtworkRead?,
        aiReadSource: String?,
        methods: MethodAdviceFacts?
    ): String = buildString {
        appendLine("Measured by the NIRA engine:")
        if (partA == null) {
            appendLine("- Print area and colours: not available (report not calculated).")
        } else {
            appendLine("- Print box: ${f1(partA.printWidthCm)} cm wide x ${f1(partA.printHeightCm)} cm high (${f1(partA.totalBoxAreaCm2)} cm2).")
            appendLine("- Inked area inside the box: ${f1(partA.printMaskCoveragePercent)}%.")
            partA.garmentProfile?.let {
                appendLine("- Garment colour measured from the image border: ${it.estimatedHex} (L* ${f1(it.medianLab.l)}).")
            }
            if (partA.pixelsPerCm > 0) appendLine("- Resolution on the print box: ${f1(partA.pixelsPerCm)} px/cm.")
            partA.lowResolutionWarning?.let { appendLine("- Warning: $it") }
            appendLine("- Print colours (${partA.measuredPalette.size}):")
            partA.measuredPalette.take(MAX_COLOURS).forEach { c ->
                val shade = c.nearestShade?.let { " nearest library shade ${it.code} ${it.name}" + (c.nearestShadeDeltaE00?.let { d -> " dE00 ${f1(d)}" } ?: "") } ?: ""
                appendLine("  - ${c.hex}, ${f1(c.coveragePercent)}% of the print$shade")
            }
        }

        if (partB != null && partB.strategies.isNotEmpty()) {
            appendLine("- White base needed (garment rule): ${if (partB.isDarkGarment) "yes" else "no"}.")
            appendLine("- Separation options:")
            partB.strategies.forEach { s ->
                val tag = if (s.isRecommended) " RECOMMENDED" else ""
                appendLine("  - ${s.name}: ${s.screenCount} screens, mean dE00 ${f2(s.meanDeltaE00)}, 95% dE00 ${f2(s.percentile95DeltaE00)}$tag")
                if (s.isRecommended && s.recommendationReason.isNotBlank()) appendLine("    Reason: ${s.recommendationReason}")
                s.transitionNote?.let { appendLine("    Note: $it") }
            }
        }

        if (partC != null && partC.channels.isNotEmpty()) {
            appendLine("- Chosen separation: ${partC.strategyName}. Screens:")
            partC.channels.forEach { ch ->
                appendLine("  - ${ch.inkName} ${ch.inkHex}, mesh ${ch.recommendedMesh}, ${f1(ch.boxCoveragePercent)}% of the print box")
            }
        }

        if (partD != null) {
            appendLine("- Ink estimate: ${f2(partD.totalGramsPerGarment)} g per garment (range ${f2(partD.totalGramsPerGarmentMin)} to ${f2(partD.totalGramsPerGarmentMax)} g), ${f1(partD.totalGramsPer100Pieces)} g per 100 pieces. Strokes ${inputs.strokes}, wastage ${f1(inputs.wastagePercent)}%. ${partD.note}")
        }

        appendLine()
        if (aiRead == null) {
            appendLine("AI read of the artwork: not done.")
        } else {
            appendLine("AI read of the artwork (source ${aiReadSource ?: "not reported"}):")
            appendLine("- Type: ${aiRead.artworkType.name}")
            if (aiRead.motifs.isNotEmpty()) appendLine("- Motifs: ${aiRead.motifs.joinToString(", ")}")
            if (aiRead.effects.isNotEmpty()) appendLine("- Effects: ${aiRead.effects.joinToString(", ")}")
            aiRead.risks.forEach { appendLine("- Risk ${it.type.name}: ${it.note}") }
            if (aiRead.evidence.isNotBlank()) appendLine("- Evidence: ${aiRead.evidence}")
        }

        appendLine()
        if (methods == null || methods.fibre == null) {
            appendLine("Print method advice: fabric not chosen.")
        } else {
            appendLine("Print method advice from the rules file (fabric ${methods.fibre.name.lowercase()}, artwork ${methods.artwork?.name?.lowercase() ?: "not chosen"}, garment ${methods.garment?.name?.lowercase() ?: "not chosen"}):")
            listOf(AdviceLevel.RECOMMENDED, AdviceLevel.POSSIBLE, AdviceLevel.NOT_SUITABLE).forEach { level ->
                val items = methods.advice.filter { it.level == level }.take(MAX_METHODS_PER_LEVEL)
                if (items.isNotEmpty()) {
                    appendLine("- ${level.name.replace('_', ' ')}:")
                    items.forEach { a ->
                        val notes = a.reasons.mapNotNull { reasonText(it) }.joinToString("; ")
                        val verified = if (a.verifiedByRavi) "" else " (not floor-verified)"
                        appendLine("  - ${a.name}$verified: $notes")
                    }
                }
            }
        }
    }.trim()

    /** Short English text of a reason for the cloud model. Nothing is added beyond the rule. */
    private fun reasonText(r: AdviceReason): String? = when (r) {
        is AdviceReason.FibreSuitable -> "fabric suitable (${r.entry})"
        is AdviceReason.FibreNotSuitable -> "fabric not suitable (${r.entry})"
        is AdviceReason.FibreCaution -> "fabric caution: ${r.note}"
        is AdviceReason.FibreNotListed -> "fabric not in its suitable list"
        AdviceReason.FibreNoData -> "no fibre data"
        AdviceReason.FibreNotChosen -> null
        AdviceReason.ArtworkSolidOnly -> "needs solid shapes"
        AdviceReason.ArtworkSolidPreferred -> "best on solid shapes"
        AdviceReason.ArtworkFits -> "suits the artwork"
        AdviceReason.ArtworkNotChosen -> null
        AdviceReason.GarmentLightOnly -> "white or light garments only"
        AdviceReason.GarmentDarkOnly -> "needs a dark or medium dyed garment"
        is AdviceReason.GarmentCaution -> "garment caution: ${r.note}"
        is AdviceReason.GarmentNote -> "garment note: ${r.note}"
        AdviceReason.GarmentNotChosen -> null
    }

    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
}
