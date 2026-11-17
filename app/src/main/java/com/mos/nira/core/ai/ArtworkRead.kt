package com.mos.nira.core.ai

import org.json.JSONArray
import org.json.JSONObject

enum class ArtworkType { FLAT, TONAL, UNKNOWN }

enum class PrintRiskType { THIN_LINES, SMALL_TEXT, GRADIENT, LARGE_SOLID, FINE_DETAIL, OTHER }

data class PrintRisk(val type: PrintRiskType, val note: String)

/** The AI's reading of the artwork. An interpretation, never a measurement. */
data class ArtworkRead(
    val artworkType: ArtworkType,
    val motifs: List<String>,
    val effects: List<String>,
    val risks: List<PrintRisk>,
    val evidence: String
)

/**
 * Builds the artwork-read prompt and reads the AI's answer.
 * Prompt text lives in assets/prompts/artwork_read_v1.txt, not in Kotlin.
 */
object ArtworkReadPrompt {

    const val ASSET_PATH = "prompts/artwork_read_v1.txt"

    private const val MAX_MOTIFS = 4
    private const val MAX_EFFECTS = 3
    private const val MAX_RISKS = 4

    /** Fills the template with the engine's measured facts. */
    fun build(template: String, printColourHexes: List<String>, garmentHex: String?): String =
        template
            .replace("{print_colour_count}", printColourHexes.size.toString())
            .replace("{print_colours}", printColourHexes.joinToString(", ").ifEmpty { "none measured" })
            .replace("{garment_colour}", garmentHex ?: "not measured")
            .trim()

    /**
     * Reads the JSON answer. Tolerates text or code fences around the JSON object.
     * Returns null when no usable JSON object is found; the caller then shows the raw answer
     * labelled as unreadable, never invented fields.
     */
    fun parse(answer: String?): ArtworkRead? {
        if (answer.isNullOrBlank()) return null
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = try {
            JSONObject(answer.substring(start, end + 1))
        } catch (e: Exception) {
            return null
        }
        if (!json.has("artwork_type") && !json.has("motifs") && !json.has("risks")) return null

        val type = when (json.optString("artwork_type").trim().uppercase()) {
            "FLAT" -> ArtworkType.FLAT
            "TONAL" -> ArtworkType.TONAL
            else -> ArtworkType.UNKNOWN
        }
        val risks = json.optJSONArray("risks")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val note = item.optString("note").trim()
                val riskType = PrintRiskType.values().firstOrNull {
                    it.name == item.optString("type").trim().uppercase()
                } ?: PrintRiskType.OTHER
                if (note.isEmpty() && riskType == PrintRiskType.OTHER) null else PrintRisk(riskType, note)
            }
        }.orEmpty().take(MAX_RISKS)

        return ArtworkRead(
            artworkType = type,
            motifs = strings(json.optJSONArray("motifs")).take(MAX_MOTIFS),
            effects = strings(json.optJSONArray("effects")).take(MAX_EFFECTS),
            risks = risks,
            evidence = json.optString("evidence").trim()
        )
    }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList()
        else (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
}
