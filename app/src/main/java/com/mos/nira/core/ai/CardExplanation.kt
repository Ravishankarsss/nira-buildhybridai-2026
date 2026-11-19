package com.mos.nira.core.ai

import com.mos.nira.core.advice.AdviceLevel
import com.mos.nira.core.advice.BaseSource
import com.mos.nira.core.advice.DecisionCard
import com.mos.nira.core.advice.MethodChoice
import com.mos.nira.core.advice.QuantityBand
import com.mos.nira.core.advice.QuantityRules
import com.mos.nira.core.image.GarmentPreview
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Explain this card": the decision card turned into plain English facts for the model (text only,
 * never the image). The answer is checked with [ReportExplanation.unverifiedNumbers] against the
 * same facts. The same facts and prompt are saved as JSON so the web page can run the identical
 * request on a real NIRA job.
 */
object CardExplanation {

    const val ASSET_PATH = "prompts/card_explain_v1.txt"
    const val EXPORT_FILE = "card_last.json"

    fun build(template: String, facts: List<String>): String = ReportExplanation.build(template, facts)

    fun facts(
        card: DecisionCard,
        contrast: List<GarmentPreview.ContrastWarning>,
        quantityRules: QuantityRules?,
        fibreName: String?
    ): List<String> = buildList {
        val a = card.answers
        val brief = listOfNotNull(
            a.garmentHex?.let { "garment colour $it" },
            fibreName?.let { "fibre $it" },
            a.fabric?.let { "fabric ${it.replace('_', ' ')}" },
            a.quantity?.let { "quantity $it pieces" },
            a.feel?.let { "feel wanted ${it.name.lowercase()}" }
        )
        if (brief.isNotEmpty()) add("Brief: ${brief.joinToString("; ")}.")
        contrast.forEach { add("Contrast warning: ink ${it.inkHex} is hard to see on this garment (lightness difference ${it.deltaL}).") }
        card.best?.let { add("Method: ${method(it)}") }
        card.second?.let { add("Second choice: ${method(it)}") }
        card.smallRun?.let { add("For small quantities: ${method(it)}") }
        add(
            when (card.quantityBand) {
                QuantityBand.NOT_ANSWERED -> "Quantity: not answered."
                QuantityBand.NOT_SET -> "Quantity: the quantity limits are not in the rules file yet, so quantity did not change the method."
                QuantityBand.DIGITAL -> "Quantity: below ${quantityRules?.digitalBelowPieces} pieces, digital comes first."
                QuantityBand.SCREEN -> "Quantity: from ${quantityRules?.screenFromPieces} pieces, screen printing comes first."
                QuantityBand.BETWEEN -> "Quantity: between ${quantityRules?.digitalBelowPieces} and ${quantityRules?.screenFromPieces} pieces, both digital and screen are offered."
            }
        )
        val best = card.best ?: return@buildList
        when (card.baseSource) {
            BaseSource.DIGITAL -> add(
                if (best.advice.id == "discharge_digital") "Screens: how the discharge is applied is not in the rules file yet."
                else "Screens: none, this is a digital print."
            )
            else -> {
                card.separationName?.let { add("Separation measured by the engine: $it.") }
                add("Screens: ${card.screens.size} (${card.screens.joinToString(", ") { it.name }}).")
                add(
                    when {
                        card.baseSource == BaseSource.DISCHARGE -> "White base: no, discharge removes the garment dye."
                        card.baseNeeded == true -> "White base: yes, under the print only."
                        else -> "White base: no."
                    }
                )
                card.screens.forEach { s ->
                    val ink = s.gramsPerGarment?.let { "ink ${f1(it)} g per garment" } ?: "ink figure not available in the rules file"
                    add("Screen ${s.name}: mesh ${s.mesh}${s.meshConfidence?.let { " ($it)" } ?: ""}, $ink.")
                }
                card.gramsPerGarment?.let {
                    add("Ink per garment: ${f1(it)} g (min ${f1(card.gramsMin ?: it)}, max ${f1(card.gramsMax ?: it)}).")
                }
            }
        }
        best.advice.risks.forEach { add("Risk: $it") }
        add("All values are a model result for planning; a strike-off is needed before bulk production.")
    }

    private fun method(c: MethodChoice): String {
        val a = c.advice
        val level = when (a.level) {
            AdviceLevel.RECOMMENDED -> "recommended"
            AdviceLevel.POSSIBLE -> "possible, see notes"
            AdviceLevel.NOT_SUITABLE -> "not suitable"
        }
        val check = if (a.verifiedByRavi) "verified by Ravi on the floor" else "not floor-verified"
        val feel = c.feelTag?.let { "; feel $it" } ?: ""
        return "${a.name} ($level; $check; confidence ${a.confidence ?: "not given in the rules file"}$feel)."
    }

    /** The export the web page loads: the facts and the exact prompt the app sent. */
    fun exportJson(facts: List<String>, prompt: String, savedAt: String): String = JSONObject()
        .put("generated_by", "NIRA Android app (engine and rules files; no AI used to make these facts)")
        .put("saved_at", savedAt)
        .put("prompt_asset", ASSET_PATH)
        .put("facts", JSONArray(facts))
        .put("prompt", prompt)
        .toString(2)

    private fun f1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}
