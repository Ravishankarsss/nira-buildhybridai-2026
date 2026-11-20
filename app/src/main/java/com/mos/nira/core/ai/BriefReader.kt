package com.mos.nira.core.ai

import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import org.json.JSONObject

/**
 * Buyer Brief Reader: the AI reads a buyer's tech-pack page or order email and proposes answers
 * for NIRA's four brief questions. The AI only proposes; code checks every answer and the user
 * confirms each one before it fills the brief.
 *
 * Checks (in code, not trusted to the AI):
 *  - each value must be one of the allowed values (garment colours from print_rules.json
 *    garment_preview.named_colours, fabrics from print_brief.fabrics, NIRA's fibres and feels);
 *  - quantity must be a positive whole number;
 *  - for pasted text, the evidence must really be in the text, and the quantity's digits must be
 *    in it too. A page photo cannot be checked this way, so the user confirms against the page.
 */
object BriefReader {

    const val ASSET_PATH = "prompts/brief_read_v1.txt"

    /** Longest pasted brief sent to the model, in characters. */
    const val MAX_TEXT_CHARS = 4000

    enum class Kind { GARMENT_COLOUR, FIBRE, FABRIC, QUANTITY, FEEL }

    enum class Drop { NOT_ALLOWED, NOT_IN_TEXT, BAD_NUMBER }

    data class Field(
        val kind: Kind,
        /** What fills the brief: a colour key, Fibre name, fabric id, quantity or Feel name. */
        val value: String,
        val evidence: String?,
        /** Why the field is not offered; null when it passed the checks. */
        val dropped: Drop? = null
    )

    data class Reading(val fields: List<Field>) {
        val usable: List<Field> get() = fields.filter { it.dropped == null }
    }

    private val FIBRES = mapOf(
        "cotton" to Fibre.COTTON, "polyester" to Fibre.POLYESTER, "cotton_poly_blend" to Fibre.COTTON_POLY_BLEND,
        "linen" to Fibre.LINEN, "nylon" to Fibre.NYLON
    )
    private val FEELS = mapOf("soft" to Feel.SOFT, "raised" to Feel.RAISED, "any" to Feel.ANY)

    fun fibre(value: String): Fibre? = FIBRES[value]
    fun feel(value: String): Feel? = FEELS[value]

    fun build(template: String, brief: String, colourKeys: List<String>, fabrics: List<String>): String = template
        .replace("{colours}", colourKeys.joinToString(", "))
        .replace("{fibres}", FIBRES.keys.joinToString(", "))
        .replace("{fabrics}", fabrics.joinToString(", "))
        .replace("{brief}", brief.trim().take(MAX_TEXT_CHARS).ifBlank { "(the brief is the attached page image)" })
        .trim()

    /**
     * Parses the model's JSON and checks it. [sourceText] is the pasted brief, or null for a page
     * photo. Returns null when the reply is not JSON.
     */
    fun parse(reply: String, colourKeys: List<String>, fabrics: List<String>, sourceText: String?): Reading? {
        val json = extractJson(reply) ?: return null
        val evidence = json.optJSONObject("evidence")
        val source = sourceText?.let { normalise(it) }

        fun ev(key: String): String? = evidence?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }?.trim()

        fun field(kind: Kind, key: String, allowed: Collection<String>): Field? {
            val raw = json.opt(key)
            if (raw == null || raw == JSONObject.NULL) return null
            val v = raw.toString().trim().lowercase().replace(' ', '_').replace('/', '_')
            if (v.isEmpty() || v == "null") return null
            val e = ev(key)
            val drop = when {
                v !in allowed -> Drop.NOT_ALLOWED
                source != null && (e == null || normalise(e) !in source) -> Drop.NOT_IN_TEXT
                else -> null
            }
            return Field(kind, v, e, drop)
        }

        val fields = mutableListOf<Field>()
        field(Kind.GARMENT_COLOUR, "garment_colour", colourKeys)?.let(fields::add)
        field(Kind.FIBRE, "fibre", FIBRES.keys)?.let(fields::add)
        field(Kind.FABRIC, "fabric", fabrics)?.let(fields::add)
        quantity(json, ev("quantity"), source)?.let(fields::add)
        field(Kind.FEEL, "feel", FEELS.keys)?.let(fields::add)
        return Reading(fields)
    }

    private fun quantity(json: JSONObject, evidence: String?, source: String?): Field? {
        val raw = json.opt("quantity")
        if (raw == null || raw == JSONObject.NULL) return null
        val text = raw.toString().trim()
        if (text.isEmpty() || text == "null") return null
        val n = text.replace(",", "").toDoubleOrNull()
        if (n == null || n < 1 || n % 1.0 != 0.0 || n > 10_000_000) return Field(Kind.QUANTITY, text, evidence, Drop.BAD_NUMBER)
        val q = n.toLong().toString()
        // The digits must be in the pasted text ("2,500" and "2500" both count).
        val drop = if (source != null && q !in source.replace(Regex("(?<=\\d)[,. ](?=\\d)"), "")) Drop.NOT_IN_TEXT else null
        return Field(Kind.QUANTITY, q, evidence, drop)
    }

    private fun normalise(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()

    /** The first {...} block in the reply (models sometimes wrap JSON in ``` fences). */
    fun extractJson(reply: String): JSONObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(reply.substring(start, end + 1)) }.getOrNull()
    }
}
