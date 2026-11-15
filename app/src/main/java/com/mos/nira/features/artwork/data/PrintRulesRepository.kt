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
 * Data container for separation decision rules read from assets/print_rules.json.
 */
data class SeparationDecisionRules(
    val darkGarmentThresholdL: Double = 45.0,
    val qualityToleranceDeltaE00: Double = 4.5,
    val minResolutionPxPerCm: Double = 40.0,
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

        if (root.has("process_inks")) {
            val p = root.getJSONObject("process_inks")
            val config = ProcessInksConfig(
                cyanHex = p.optString("cyan", "#00AEEF"),
                magentaHex = p.optString("magenta", "#EC008C"),
                yellowHex = p.optString("yellow", "#FFF200"),
                blackHex = p.optString("black", "#231F20"),
                confidence = p.optString("confidence", "assumption"),
                note = p.optString("note", "")
            )
            cachedProcessInks = config
            return@withContext config
        }
        ProcessInksConfig()
    }

    /**
     * Loads the separation decision rules from print_rules.json.
     */
    suspend fun getSeparationDecisionRules(): SeparationDecisionRules = withContext(Dispatchers.IO) {
        cachedDecisionRules?.let { return@withContext it }
        val root = loadJson() ?: return@withContext SeparationDecisionRules()

        val choke = if (root.has("underbase_rules")) {
            root.getJSONObject("underbase_rules").optDouble("choke_mm_default", 0.5)
        } else 0.5

        if (root.has("separation_decision_rules")) {
            val s = root.getJSONObject("separation_decision_rules")
            val rules = SeparationDecisionRules(
                darkGarmentThresholdL = s.optDouble("dark_garment_threshold_L", 45.0),
                qualityToleranceDeltaE00 = s.optDouble("quality_tolerance_deltaE00", 4.5),
                minResolutionPxPerCm = s.optDouble("min_resolution_px_per_cm", 40.0),
                highlightWhiteThresholdL = s.optDouble("highlight_white_threshold_L", 80.0),
                highlightWhiteMaxChroma = s.optDouble("highlight_white_max_chroma", 10.0),
                chokeMmDefault = choke,
                confidence = s.optString("dark_garment_confidence", "assumption")
            )
            cachedDecisionRules = rules
            return@withContext rules
        }

        SeparationDecisionRules(chokeMmDefault = choke)
    }

    /**
     * Loads plastisol ink deposits by mesh from print_rules.json.
     */
    suspend fun getMeshDepositRules(): Map<Int, MeshDepositRule> = withContext(Dispatchers.IO) {
        cachedDeposits?.let { return@withContext it }
        val root = loadJson() ?: return@withContext defaultDepositRules()

        val map = mutableMapOf<Int, MeshDepositRule>()
        if (root.has("ink_consumption")) {
            val ic = root.getJSONObject("ink_consumption")
            if (ic.has("plastisol_deposit_g_m2_by_mesh")) {
                val arr = ic.getJSONArray("plastisol_deposit_g_m2_by_mesh")
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
            }
        }

        if (map.isEmpty()) {
            defaultDepositRules().also { cachedDeposits = it }
        } else {
            cachedDeposits = map
            map
        }
    }

    private fun defaultDepositRules(): Map<Int, MeshDepositRule> {
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
