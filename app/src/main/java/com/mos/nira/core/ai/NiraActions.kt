package com.mos.nira.core.ai

import com.mos.nira.core.image.ColourName
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ask NIRA can do things in the app when the user asks ("make navy green", "1000 pieces"). The AI
 * only proposes actions from this fixed list; code checks every one before the app runs it, and
 * the user can undo it. The AI never sets a price or an ink figure: after an action the engine
 * recalculates the card.
 */
object NiraActions {

    sealed interface Action {
        /** Show the job's [fromHex] ink as [toKey]'s colour in the garment preview (preview only). */
        /** [allHexes]: every measured shade with this colour name, so the whole colour changes in the preview. */
        data class PreviewInk(val fromHex: String, val fromName: String, val toKey: String, val toHex: String, val allHexes: List<String> = listOf(fromHex)) : Action
        data class SetGarment(val key: String, val hex: String) : Action
        data class SetQuantity(val pieces: Int) : Action
        data class SetFibre(val fibre: String) : Action
        data class SetFabric(val fabric: String) : Action
        data class SetFeel(val feel: String) : Action
        data class OpenSheet(val language: String) : Action
        // Home actions (Ask NIRA on Home).
        data class OpenJob(val id: String, val name: String) : Action
        data class OpenLibrary(val query: String) : Action
        data class NewJob(val photo: Boolean) : Action
        data object OpenAiActivity : Action
        data object OpenSettings : Action
    }

    /** Why a proposed action was not run. */
    enum class Reject { UNKNOWN_TYPE, UNKNOWN_INK, UNKNOWN_COLOUR, QUANTITY_NOT_SAID, BAD_VALUE }

    data class Proposed(val raw: JSONObject, val action: Action?, val reject: Reject?)

    /** What the AI may choose from for this job. */
    data class Context(
        /** The job's print inks, as shown in the preview (hex codes; named by ColourName). */
        val inkHexes: List<String>,
        /** Named garment colours: key -> hex (print_rules.json garment_preview.named_colours). */
        val colours: List<Pair<String, String>>,
        val fabrics: List<String>,
        val languages: List<String>,
        /** Home: the saved jobs the user can open, id -> name as the facts write it. */
        val jobs: List<Pair<String, String>> = emptyList()
    ) {
        /** "navy" -> its hex, from the job's own inks. */
        val inkNames: List<Pair<String, String>> get() = inkHexes.map { ColourName.english(it).lowercase() to it }.distinctBy { it.first }
    }

    val FIBRES = listOf("cotton", "polyester", "cotton_poly_blend", "linen", "nylon")
    val FEELS = listOf("soft", "raised", "any")

    /** The lists the prompt gives the AI, as plain text. */
    fun promptLists(c: Context): Map<String, String> = mapOf(
        "{inks}" to c.inkNames.joinToString(", ") { it.first }.ifBlank { "(none)" },
        // Plain words for the AI ("royal blue", "single jersey"); validate() turns spaces back into the file keys.
        "{colours}" to c.colours.joinToString(", ") { it.first.replace('_', ' ') },
        "{fibres}" to FIBRES.joinToString(", ") { it.replace('_', ' ') },
        "{fabrics}" to c.fabrics.joinToString(", ") { it.replace('_', ' ') },
        "{languages}" to c.languages.joinToString(", "),
        "{jobs}" to c.jobs.joinToString("; ") { it.second }.ifBlank { "(none)" }
    )

    fun validate(arr: JSONArray?, c: Context, question: String): List<Proposed> {
        if (arr == null) return emptyList()
        val said = ReportExplanation.numbers(question)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            fun s(k: String) = o.optString(k).trim().lowercase().replace(' ', '_')
            fun colour(key: String) = c.colours.firstOrNull { it.first == key }?.second
            val result: Pair<Action?, Reject?> = when (s("type")) {
                "preview_ink" -> {
                    val from = s("from").replace('_', ' ')
                    val ink = c.inkNames.firstOrNull { it.first == from }
                    val to = colour(s("to"))
                    when {
                        ink == null -> null to Reject.UNKNOWN_INK
                        to == null -> null to Reject.UNKNOWN_COLOUR
                        else -> Action.PreviewInk(ink.second, ink.first, s("to"), to, c.inkHexes.filter { ColourName.english(it).lowercase() == from }.distinct()) to null
                    }
                }
                "set_garment" -> colour(s("colour"))?.let { Action.SetGarment(s("colour"), it) to null } ?: (null to Reject.UNKNOWN_COLOUR)
                "set_quantity" -> {
                    val n = o.opt("pieces")?.toString()?.replace(",", "")?.toDoubleOrNull()
                    when {
                        n == null || n < 1 || n % 1.0 != 0.0 || n > 10_000_000 -> null to Reject.BAD_VALUE
                        said.none { it == n } -> null to Reject.QUANTITY_NOT_SAID
                        else -> Action.SetQuantity(n.toInt()) to null
                    }
                }
                "set_fibre" -> if (s("fibre") in FIBRES) Action.SetFibre(s("fibre")) to null else null to Reject.BAD_VALUE
                "set_fabric" -> if (s("fabric") in c.fabrics) Action.SetFabric(s("fabric")) to null else null to Reject.BAD_VALUE
                "set_feel" -> if (s("feel") in FEELS) Action.SetFeel(s("feel")) to null else null to Reject.BAD_VALUE
                "open_sheet" -> if (s("language") in c.languages) Action.OpenSheet(s("language")) to null else null to Reject.BAD_VALUE
                "open_job" -> {
                    val want = o.optString("job").trim().lowercase()
                    c.jobs.firstOrNull { it.second.lowercase() == want }?.let { Action.OpenJob(it.first, it.second) to null } ?: (null to Reject.BAD_VALUE)
                }
                "open_library" -> {
                    val q = o.optString("search").trim()
                    if (q.isNotEmpty() && q.length <= 40) Action.OpenLibrary(q) to null else null to Reject.BAD_VALUE
                }
                "new_job" -> when (s("from")) {
                    "artwork" -> Action.NewJob(photo = false) to null
                    "photo" -> Action.NewJob(photo = true) to null
                    else -> null to Reject.BAD_VALUE
                }
                "open_ai_activity" -> Action.OpenAiActivity to null
                "open_settings" -> Action.OpenSettings to null
                else -> null to Reject.UNKNOWN_TYPE
            }
            Proposed(o, result.first, result.second)
        }
    }

    /** One reply from the model: the (written-down) question, the answer and the proposed actions. */
    data class Reply(val question: String?, val answer: String, val actions: JSONArray?)

    /**
     * Reads the model's JSON reply. A reply that is not JSON (an on-device model sometimes answers
     * in plain text) is used as the answer, with no actions.
     */
    fun parseReply(reply: String): Reply? {
        val json = BriefReader.extractJson(reply)
        if (json == null) return reply.trim().takeIf { it.isNotEmpty() }?.let { Reply(null, it, null) }
        val q = json.optString("question").trim().takeIf { it.isNotEmpty() && it != "null" }
        val a = json.optString("answer").trim().takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return Reply(q, a, json.optJSONArray("actions"))
    }
}
