package com.mos.nira.features.artwork.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream

/**
 * Data container for CMYK process inks read from assets/print_rules.json.
 */
data class ProcessInksConfig(
    val cyanHex: String = "#00AEEF",
    val magentaHex: String = "#EC008C",
    val yellowHex: String = "#FFF200",
    val blackHex: String = "#231F20",
    val confidence: String = "assumption",
    val note: String = "Nominal CMYK placeholder inks. Must be replaced with the ink maker's technical data sheet values."
)

/**
 * Data container for separation decision rules read from assets/print_rules.json (version 2).
 *
 * Where each value comes from in the rules file:
 * - darkGarmentThresholdL     <- white_base.garment_L_threshold
 * - qualityToleranceDeltaE00  <- recommendation.quality_tolerance_mean_de00
 * - minResolutionPxPerCm      <- resolution.px_per_cm_min_for_fine_detail
 * - chokeMmDefault            <- underbase_rules.choke_mm_default
 * - confidence                <- white_base.confidence
 *
 * The defaults below are only a fallback for when the file cannot be read. They must equal the
 * values in print_rules.json; PrintRulesRepositoryTest fails if they drift apart.
 *
 * highlightWhiteThresholdL and highlightWhiteMaxChroma have no value in the rules file yet
 * (white_base.highlight_white only says "adjustable threshold"), so they stay as defaults here.
 */
data class SeparationDecisionRules(
    val darkGarmentThresholdL: Double = 70.0,
    val qualityToleranceDeltaE00: Double = 3.0,
    val minResolutionPxPerCm: Double = 59.0,
    val highlightWhiteThresholdL: Double = 80.0,
    val highlightWhiteMaxChroma: Double = 10.0,
    val chokeMmDefault: Double = 0.5,
    val confidence: String = "assumption"
)

/**
 * Ink deposit rule for a given screen mesh count.
 */
data class MeshDepositRule(
    val tpi: Int,
    val depositGPerM2Default: Double,
    val depositGPerM2Min: Double,
    val depositGPerM2Max: Double,
    val confidence: String
)

/**
 * Repository to load technical printing rules from assets/print_rules.json.
 */
class PrintRulesRepository(private val context: Context?) {

    private var cachedProcessInks: ProcessInksConfig? = null
    private var cachedDecisionRules: SeparationDecisionRules? = null
    private var cachedDeposits: Map<Int, MeshDepositRule>? = null

    private fun loadJson(): JSONObject? {
        if (context == null) return null
        return try {
            val jsonString = context.assets.open("print_rules.json").use { stream: InputStream ->
                stream.bufferedReader().use { it.readText() }
            }
            JSONObject(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads the process inks configuration from print_rules.json.
     */
    suspend fun getProcessInks(): ProcessInksConfig = withContext(Dispatchers.IO) {
        cachedProcessInks?.let { return@withContext it }
        val root = loadJson() ?: return@withContext ProcessInksConfig()
        parseProcessInks(root).also { cachedProcessInks = it }
    }

    /**
     * Loads the separation decision rules from print_rules.json.
     */
    suspend fun getSeparationDecisionRules(): SeparationDecisionRules = withContext(Dispatchers.IO) {
        cachedDecisionRules?.let { return@withContext it }
        val root = loadJson() ?: return@withContext SeparationDecisionRules()
        parseSeparationDecisionRules(root).also { cachedDecisionRules = it }
    }

    /**
     * Loads plastisol ink deposits by mesh from print_rules.json.
     */
    suspend fun getMeshDepositRules(): Map<Int, MeshDepositRule> = withContext(Dispatchers.IO) {
        cachedDeposits?.let { return@withContext it }
        val root = loadJson() ?: return@withContext defaultDepositRules()
        val map = parseMeshDepositRules(root)
        (if (map.isEmpty()) defaultDepositRules() else map).also { cachedDeposits = it }
    }

    companion object {

        /**
         * Reads "process_inks" (cyan, magenta, yellow, black) from the rules file.
         */
        fun parseProcessInks(root: JSONObject): ProcessInksConfig {
            val fallback = ProcessInksConfig()
            val p = root.optJSONObject("process_inks") ?: return fallback
            return ProcessInksConfig(
                cyanHex = p.optString("cyan", fallback.cyanHex),
                magentaHex = p.optString("magenta", fallback.magentaHex),
                yellowHex = p.optString("yellow", fallback.yellowHex),
                blackHex = p.optString("black", fallback.blackHex),
                confidence = p.optString("confidence", fallback.confidence),
                note = p.optString("note", "")
            )
        }

        /**
         * Reads the separation decision rules from the version 2 sections
         * "white_base", "recommendation", "resolution" and "underbase_rules".
         */
        fun parseSeparationDecisionRules(root: JSONObject): SeparationDecisionRules {
            val fallback = SeparationDecisionRules()
            val whiteBase = root.optJSONObject("white_base")
            val recommendation = root.optJSONObject("recommendation")
            val resolution = root.optJSONObject("resolution")
            val underbase = root.optJSONObject("underbase_rules")

            return SeparationDecisionRules(
                darkGarmentThresholdL = whiteBase?.optDouble("garment_L_threshold", fallback.darkGarmentThresholdL)
                    ?: fallback.darkGarmentThresholdL,
                qualityToleranceDeltaE00 = recommendation?.optDouble("quality_tolerance_mean_de00", fallback.qualityToleranceDeltaE00)
                    ?: fallback.qualityToleranceDeltaE00,
                minResolutionPxPerCm = resolution?.optDouble("px_per_cm_min_for_fine_detail", fallback.minResolutionPxPerCm)
                    ?: fallback.minResolutionPxPerCm,
                highlightWhiteThresholdL = fallback.highlightWhiteThresholdL,
                highlightWhiteMaxChroma = fallback.highlightWhiteMaxChroma,
                chokeMmDefault = underbase?.optDouble("choke_mm_default", fallback.chokeMmDefault)
                    ?: fallback.chokeMmDefault,
                confidence = whiteBase?.optString("confidence", fallback.confidence) ?: fallback.confidence
            )
        }

        /**
         * Reads plastisol ink deposits by mesh from "ink_consumption". Empty if the section is missing.
         */
        fun parseMeshDepositRules(root: JSONObject): Map<Int, MeshDepositRule> {
            val map = mutableMapOf<Int, MeshDepositRule>()
            val arr = root.optJSONObject("ink_consumption")
                ?.optJSONArray("plastisol_deposit_g_m2_by_mesh") ?: return map
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val tpi = item.getInt("tpi")
                map[tpi] = MeshDepositRule(
                    tpi = tpi,
                    depositGPerM2Default = item.optDouble("deposit_g_m2_default", 36.0),
                    depositGPerM2Min = item.optDouble("deposit_g_m2_min", 30.0),
                    depositGPerM2Max = item.optDouble("deposit_g_m2_max", 48.0),
                    confidence = item.optString("confidence", "interpolated")
                )
            }
            return map
        }

        fun defaultDepositRules(): Map<Int, MeshDepositRule> {
            return mapOf(
                110 to MeshDepositRule(110, 45.0, 38.0, 63.0, "sourced"),
                137 to MeshDepositRule(137, 40.0, 33.0, 54.0, "interpolated"),
                156 to MeshDepositRule(156, 36.0, 30.0, 48.0, "interpolated"),
                200 to MeshDepositRule(200, 28.0, 23.0, 38.0, "interpolated"),
                230 to MeshDepositRule(230, 24.0, 19.0, 33.0, "interpolated"),
                305 to MeshDepositRule(305, 18.0, 13.0, 25.0, "sourced")
            )
        }
    }
}
