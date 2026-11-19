package com.mos.nira.core.ai

import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import kotlin.math.abs
import kotlin.math.round

/**
 * The AI explanation of the Print Report recommendation. The model gets only text: the engine
 * result and the matching rules from print_rules.json. Its answer is shown only if every number in
 * it is found in those facts; otherwise only the facts (the data card) are shown.
 */
object ReportExplanation {

    const val ASSET_PATH = "prompts/report_explain_v1.txt"

    fun build(template: String, facts: List<String>): String =
        template.replace("{facts}", facts.joinToString("\n") { "- $it" }).trim()

    /** Engine facts for the recommendation, one per line. Every number comes from the results passed in. */
    fun engineFacts(partA: PartAResult?, partB: PartBResult?): List<String> = buildList {
        partA?.let { a ->
            a.garmentProfile?.let { add("Garment colour ${com.mos.nira.core.image.ColourName.english(it.estimatedHex)} (L* ${f1(it.medianLab.l)}).") }
            add("Print colours measured: ${a.measuredPalette.size}.")
            add("Inked area: ${f1(a.printMaskCoveragePercent)}% of the print box, ${f1(a.printWidthCm)} x ${f1(a.printHeightCm)} cm.")
        }
        partB?.let { b ->
            add("White base needed: ${if (b.isDarkGarment) "yes" else "no"}.")
            b.strategies.firstOrNull { it.isRecommended }?.let { r ->
                add("Recommended: ${r.name}, ${r.screenCount} screens, mean dE00 ${f2(r.meanDeltaE00)}, 95% dE00 ${f2(r.percentile95DeltaE00)}.")
                if (r.recommendationReason.isNotBlank()) add("Reason: ${r.recommendationReason}")
            }
            b.strategies.filter { !it.isRecommended }.forEach { s ->
                add("Other option: ${s.name}, ${s.screenCount} screens, mean dE00 ${f2(s.meanDeltaE00)}.")
            }
        }
    }

    /** Numbers in a text, ignoring hex colours (#1D5CAB) and digits that are part of words (dE00, L2). */
    fun numbers(text: String): List<Double> {
        val cleaned = text.replace(Regex("#[0-9A-Fa-f]{6}"), " ")
        return Regex("(?<![A-Za-z0-9.])\\d+(?:[.,]\\d+)?").findAll(cleaned)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .toList()
    }

    /**
     * Numbers in [answer] that are not in [facts]. A number counts as found when it equals a fact
     * number, or that number rounded to a whole number or to one decimal place.
     */
    fun unverifiedNumbers(answer: String, facts: List<String>): List<Double> {
        val known = numbers(facts.joinToString("\n"))
        return numbers(answer).filter { n ->
            known.none { k -> same(n, k) || same(n, round(k)) || same(n, round(k * 10) / 10) }
        }.distinct()
    }

    private fun same(a: Double, b: Double) = abs(a - b) < 1e-9

    private fun f1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
    private fun f2(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
}
