package com.mos.nira.core.advice

import org.json.JSONArray
import org.json.JSONObject

/**
 * Technique Library: every entry of assets/print_techniques.json shown as written, for reading
 * and searching. No AI and no values of its own: what is not in the file is not shown.
 */
object TechniqueLibrary {

    /** One parameter as the file gives it, e.g. cure_temp_c = 160 (sourced_multi). */
    data class Param(
        val key: String,
        /** "160", "110–160", or the text the file gives. */
        val value: String,
        val published: String?,
        val note: String?,
        val confidence: String?
    )

    data class Entry(
        val rule: TechniqueRule,
        val params: List<Param>,
        val source: String?,
        val processSource: String?
    )

    data class Library(
        val entries: List<Entry>,
        /** Group ids in file order. */
        val groups: List<String>,
        val confidenceKey: Map<String, String>,
        val artworkFitKey: Map<String, String>
    )

    fun parse(json: String): Library {
        val root = JSONObject(json)
        val rules = TechniqueAdvisor.parseRules(json).associateBy { it.id }
        val arr = root.optJSONArray("techniques") ?: JSONArray()
        val entries = (0 until arr.length()).mapNotNull { i ->
            val t = arr.getJSONObject(i)
            val rule = rules[t.getString("id")] ?: return@mapNotNull null
            Entry(rule, params(t.optJSONObject("parameters")), text(t, "source"), text(t, "process_source"))
        }
        return Library(
            entries = entries,
            groups = entries.map { it.rule.group }.distinct(),
            confidenceKey = map(root.optJSONObject("confidence_key")),
            artworkFitKey = map(root.optJSONObject("artwork_fit_key"))
        )
    }

    /** Entries matching the search words (name, what it is, fibres, risks) and the group, in file order. */
    fun filter(library: Library, query: String, group: String?): List<Entry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return library.entries.filter { e ->
            (group == null || e.rule.group == group) && words.all { w -> haystack(e).contains(w) }
        }
    }

    private fun haystack(e: Entry): String = with(e.rule) {
        (listOf(name, id, whatItIs, garmentColour.orEmpty()) + suitableFibres + notSuitableFibres + cautions + risks)
            .joinToString(" ").lowercase()
    }

    private fun params(obj: JSONObject?): List<Param> {
        if (obj == null) return emptyList()
        return obj.keys().asSequence().mapNotNull { key ->
            val p = obj.optJSONObject(key) ?: return@mapNotNull obj.opt(key)?.let { Param(key, it.toString(), null, null, null) }
            val value = when {
                p.has("value") -> num(p.opt("value"))
                p.has("min") || p.has("max") -> listOfNotNull(p.opt("min")?.let(::num), p.opt("max")?.let(::num)).joinToString("–")
                else -> null
            }
            Param(key, value ?: "—", text(p, "published"), text(p, "note"), text(p, "confidence"))
        }.toList()
    }

    private fun num(v: Any?): String = when (v) {
        is Number -> if (v.toDouble() % 1.0 == 0.0) v.toLong().toString() else v.toString()
        null, JSONObject.NULL -> "—"
        else -> v.toString()
    }

    private fun text(o: JSONObject, key: String): String? = o.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun map(o: JSONObject?): Map<String, String> =
        o?.keys()?.asSequence()?.associateWith { o.optString(it) } ?: emptyMap()
}
