package com.mos.nira.core.ai

import com.mos.nira.core.advice.AdviceLevel
import com.mos.nira.core.advice.BaseSource
import com.mos.nira.core.advice.DecisionCard
import com.mos.nira.core.advice.MethodChoice
import com.mos.nira.core.advice.QuantityBand
import com.mos.nira.core.advice.QuantityRules
import com.mos.nira.core.image.ColourName
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

    const val ASSET_PATH = "prompts/card_explain_v2.txt"
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
            a.garmentHex?.let { "garment colour ${ColourName.english(it)}" },
            fibreName?.let { "fibre $it" },
            a.fabric?.let { "fabric ${it.replace('_', ' ')}" },
            a.quantity?.let { "quantity $it pieces" },
            a.feel?.let { "feel wanted ${it.name.lowercase()}" }
        )
        if (brief.isNotEmpty()) add("Brief: ${brief.joinToString("; ")}.")
        val garmentName = a.garmentHex?.let { ColourName.english(it) } ?: "this"
        contrast.forEach {
            add("Contrast warning: the ${ColourName.english(it.inkHex)} ink is hard to see on the $garmentName garment (lightness difference ${it.deltaL}).")
        }
        card.best?.let { add("Method: ${method(it)}") }
        card.second?.let { add("Second choice: ${method(it)}") }
        card.smallRun?.let { add("For small quantities: ${method(it)}") }
        add(
            when (card.quantityBand) {
                QuantityBand.NOT_ANSWERED -> "Quantity: not answered."
                QuantityBand.NOT_SET -> "Quantity: the quantity limits are not in the rules file yet, so quantity did not change the method."
                QuantityBand.BUYER_ORDER -> "Quantity: ${a.quantity} pieces, the buyer's order. Quantity does not change the method; it is used for the ink total of the order."
                QuantityBand.DIGITAL -> "Quantity: below ${quantityRules?.digitalBelowPieces} pieces, digital comes first."
                QuantityBand.SCREEN -> "Quantity: from ${quantityRules?.screenFromPieces} pieces, screen printing comes first."
                QuantityBand.BETWEEN -> "Quantity: between ${quantityRules?.digitalBelowPieces} and ${quantityRules?.screenFromPieces} pieces, both digital and screen are offered."
            }
        )
        val best = card.best ?: return@buildList
        when (card.baseSource) {
            BaseSource.DIGITAL -> {
                val n = best.advice.screens
                if (n != null && n > 0) {
                    add("Screens: $n (${best.advice.screensDetail ?: "from the rules file"}).")
                } else {
                    add("Screens: none, this is a digital print.")
                }
                if (best.advice.processSteps.isNotEmpty()) add("How it is printed: ${best.advice.processSteps.joinToString(" ")}")
                if ("discharge" in best.advice.id) add("White base: no, discharge removes the garment dye.")
            }
            else -> {
                card.separationName?.let { add("Separation measured by the engine: $it.") }
                add("Screens: ${card.screens.size} (${card.screens.joinToString(", ") { label(it.name) }}).")
                add(
                    when {
                        card.baseSource == BaseSource.DISCHARGE -> "White base: no, discharge removes the garment dye."
                        card.baseNeeded == true -> "White base: yes, under the print only."
                        else -> "White base: no."
                    }
                )
                card.screens.forEach { s ->
                    val ink = s.gramsPerGarment?.let { "ink ${f1(it)} g per garment" } ?: "ink figure not available in the rules file"
                    add("Screen ${label(s.name)}: mesh ${s.mesh}${s.meshConfidence?.let { " ($it)" } ?: ""}, $ink.")
                }
                card.gramsPerGarment?.let {
                    add("Ink per garment: ${f1(it)} g (min ${f1(card.gramsMin ?: it)}, max ${f1(card.gramsMax ?: it)}).")
                    a.quantity?.let { q ->
                        add("Ink for $q pieces: ${f0(it * q)} g (min ${f0((card.gramsMin ?: it) * q)}, max ${f0((card.gramsMax ?: it) * q)}).")
                    }
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

    private val HEX = Regex("#[0-9A-Fa-f]{6}")

    /** A screen label for the model: a hex label becomes its colour name (no codes in the facts). */
    private fun label(name: String) = if (HEX.matches(name)) ColourName.english(name) else name

    /** Words of the method name the answer must contain ("Discharge + digital print" -> discharge, digital). */
    fun methodWords(methodName: String): List<String> =
        Regex("[A-Za-z]+").findAll(methodName.substringBefore("(")).map { it.value.lowercase() }
            .filter { it.length >= 4 && it != "print" }.distinct().toList()

    /** Method words missing from the answer; empty when the answer names the card's method. */
    fun missingMethodWords(answer: String, methodName: String?): List<String> {
        if (methodName == null) return emptyList()
        val text = answer.lowercase()
        return methodWords(methodName).filter { it !in text }
    }

    /** True when the answer has a colour code (the facts have none, so any code was made up). */
    fun hasColourCode(answer: String): Boolean = HEX.containsMatchIn(answer)

    /** The export the web page loads: the facts and the exact prompt the app sent. */
    fun exportJson(facts: List<String>, prompt: String, savedAt: String, methodName: String? = null): String = JSONObject()
        .put("generated_by", "NIRA Android app (engine and rules files; no AI used to make these facts)")
        .put("method_name", methodName ?: JSONObject.NULL)
        .put("saved_at", savedAt)
        .put("prompt_asset", ASSET_PATH)
        .put("facts", JSONArray(facts))
        .put("prompt", prompt)
        .toString(2)

    private fun f1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
    private fun f0(v: Double) = String.format(java.util.Locale.US, "%.0f", v)
}
