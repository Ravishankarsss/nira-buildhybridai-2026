package com.mos.nira.core.ai

import com.mos.nira.core.advice.DecisionCard
import org.json.JSONArray

/**
 * Decision Sheet in the buyer's language. The sheet's labels come from the app's own string
 * translations (no AI). Only the English texts from the rules files (what the method is, the
 * screens detail, process steps, risks) can be translated by AI, and each translation is kept
 * only if it passes the checks here; otherwise that line stays in English.
 */
object SheetTranslation {

    const val ASSET_PATH = "prompts/sheet_translate_v1.txt"

    /** The rules-file texts the sheet shows for this card, in sheet order, without duplicates. */
    fun texts(card: DecisionCard): List<String> {
        val a = card.best?.advice ?: return emptyList()
        return (listOfNotNull(a.whatItIs.takeIf { it.isNotBlank() }, a.screensDetail) + a.processSteps + a.risks)
            .filter { it.isNotBlank() }.distinct()
    }

    fun build(template: String, texts: List<String>, languageName: String): String =
        template.replace("{language}", languageName).replace("{texts}", JSONArray(texts).toString()).trim()

    /** Why one translation was not used. */
    enum class Reject { NUMBERS_CHANGED, COLOUR_CODE, EMPTY }

    data class Result(
        /** English text -> translation, only for translations that passed. */
        val translated: Map<String, String>,
        val rejected: Map<String, Reject>
    )

    /** Parses the model's JSON array; null when it is not an array of the same length. */
    fun parse(reply: String, texts: List<String>): Result? {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val arr = runCatching { JSONArray(reply.substring(start, end + 1)) }.getOrNull() ?: return null
        if (arr.length() != texts.size) return null
        val ok = mutableMapOf<String, String>()
        val bad = mutableMapOf<String, Reject>()
        texts.forEachIndexed { i, src ->
            val t = arr.optString(i).trim()
            when (val r = check(src, t)) {
                null -> ok[src] = t
                else -> bad[src] = r
            }
        }
        return Result(ok, bad)
    }

    /** A translation must keep exactly the same numbers and add no colour code. */
    fun check(source: String, translation: String): Reject? = when {
        translation.isBlank() || translation == "null" -> Reject.EMPTY
        CardExplanation.hasColourCode(translation) && !CardExplanation.hasColourCode(source) -> Reject.COLOUR_CODE
        ReportExplanation.numbers(translation).sorted() != ReportExplanation.numbers(source).sorted() -> Reject.NUMBERS_CHANGED
        else -> null
    }
}
