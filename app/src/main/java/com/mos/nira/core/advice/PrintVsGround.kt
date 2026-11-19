package com.mos.nira.core.advice

import com.mos.nira.core.image.ColorConverter

/** print_rules.json "print_vs_ground". No defaults: null when the section is missing. */
data class PrintVsGroundRules(val lighterThresholdL: Double, val confidence: String, val verifiedByRavi: Boolean)

enum class GroundVerdict { NEEDS_DISCHARGE_OR_BASE, PRINTS_ON_TOP, CLOSE_TO_GROUND }

data class GroundCheck(val inkHex: String, val deltaL: Double, val verdict: GroundVerdict)

/**
 * Ravi's master rule for a dyed ground (19 Nov 2026): a print colour lighter than the fabric must
 * remove the dye (discharge) or sit on a white base; a darker colour prints on top. Lightness is
 * CIELAB L*; colours within the threshold of the ground are reported as close to it.
 */
object PrintVsGround {

    fun check(inkHexes: List<String>, groundHex: String, rules: PrintVsGroundRules): List<GroundCheck> {
        val g = lab(groundHex)
        return inkHexes.distinct().map { hex ->
            val d = lab(hex).l - g.l
            val v = when {
                d > rules.lighterThresholdL -> GroundVerdict.NEEDS_DISCHARGE_OR_BASE
                d < -rules.lighterThresholdL -> GroundVerdict.PRINTS_ON_TOP
                else -> GroundVerdict.CLOSE_TO_GROUND
            }
            GroundCheck(hex, d, v)
        }
    }

    fun parse(root: org.json.JSONObject): PrintVsGroundRules? = try {
        val r = root.getJSONObject("print_vs_ground")
        PrintVsGroundRules(r.getDouble("lighter_threshold_L"), r.getString("confidence"), r.getBoolean("verified_by_ravi"))
    } catch (e: Exception) {
        null
    }

    private fun lab(hex: String) = ColorConverter.hexToRgb(hex).let { ColorConverter.rgbToLab(it.r, it.g, it.b) }
}
