package com.mos.nira.core.qc

import org.json.JSONObject

/**
 * Strike-off check rules: print_rules.json "strike_off_check" (S Ravi Shankar, 20 Nov 2026).
 * Only v1_active_rules is used; v2_placeholders stay off until the file enables them.
 * No defaults: a missing value makes the whole rule set null, never a guess.
 */
data class StrikeOffRules(
    val passMax: Double,
    val checkMax: Double,
    val failAbove: Double,
    /** Colours that get a "check by eye" badge (glare and opacity), e.g. pure_white, pure_black. */
    val visualCheckColours: List<String>,
    val whiteReference: Boolean,
    val preferredLighting: String?,
    val fallbackLighting: String?,
    val avoidLighting: List<String>,
    val firstStrikeOffStandard: String?,
    val bulkStandard: String?,
    val source: String?
)

enum class StrikeOffVerdict { PASS, CHECK, FAIL }

object StrikeOff {

    fun parse(root: JSONObject): StrikeOffRules? {
        val so = root.optJSONObject("strike_off_check") ?: return null
        val v1 = so.optJSONObject("v1_active_rules") ?: return null
        val limits = v1.optJSONObject("color_limits")?.optJSONObject("thresholds_delta_e") ?: return null
        fun num(k: String) = if (limits.has(k)) limits.optDouble(k).takeIf { !it.isNaN() } else null
        val pass = num("pass_max") ?: return null
        val check = num("check_max") ?: return null
        val fail = num("fail_above") ?: return null
        val flags = v1.optJSONObject("color_limits")?.optJSONObject("visual_check_flags")?.optJSONArray("colors")
        val capture = v1.optJSONObject("capture_protocol")
        val avoid = capture?.optJSONArray("avoid_lighting")
        val standards = v1.optJSONObject("comparison_standards")
        return StrikeOffRules(
            passMax = pass,
            checkMax = check,
            failAbove = fail,
            visualCheckColours = flags?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            whiteReference = capture?.optJSONObject("white_balance_reference")?.optBoolean("enabled", false) == true,
            preferredLighting = capture?.optString("preferred_lighting")?.takeIf { it.isNotBlank() },
            fallbackLighting = capture?.optString("fallback_lighting")?.takeIf { it.isNotBlank() },
            avoidLighting = avoid?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            firstStrikeOffStandard = standards?.optString("initial_strike_off")?.takeIf { it.isNotBlank() },
            bulkStandard = standards?.optString("bulk_and_rechecks")?.takeIf { it.isNotBlank() },
            source = so.optString("source").takeIf { it.isNotBlank() }
        )
    }

    /** dE00 <= pass_max is Pass; up to check_max is Check; above fail_above is Fail. */
    fun verdict(deltaE00: Double, rules: StrikeOffRules): StrikeOffVerdict = when {
        deltaE00 <= rules.passMax -> StrikeOffVerdict.PASS
        deltaE00 <= rules.checkMax -> StrikeOffVerdict.CHECK
        deltaE00 > rules.failAbove -> StrikeOffVerdict.FAIL
        else -> StrikeOffVerdict.CHECK
    }

    /** The whole sample: Fail if any colour fails, Check if any needs checking, else Pass. */
    fun overall(verdicts: List<StrikeOffVerdict>): StrikeOffVerdict? = when {
        verdicts.isEmpty() -> null
        StrikeOffVerdict.FAIL in verdicts -> StrikeOffVerdict.FAIL
        StrikeOffVerdict.CHECK in verdicts -> StrikeOffVerdict.CHECK
        else -> StrikeOffVerdict.PASS
    }
}
