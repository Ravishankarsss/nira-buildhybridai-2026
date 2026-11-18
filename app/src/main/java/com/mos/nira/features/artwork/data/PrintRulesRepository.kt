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
 * - garmentChromaThreshold    <- white_base.garment_chroma_threshold
 * - qualityToleranceP95DeltaE00 <- recommendation.quality_tolerance_p95_de00
 * - cmykMinHueFamilies        <- recommendation.cmyk_min_hue_families
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
    val confidence: String = "assumption",
    val garmentChromaThreshold: Double = 15.0,
    val qualityToleranceP95DeltaE00: Double = 8.0,
    val cmykMinHueFamilies: Int = 4
)

/**
 * Garment mask rules read from "garment_mask" in assets/print_rules.json.
 * Defaults are only a fallback and must equal the file (checked by PrintRulesRepositoryTest).
 */
data class GarmentMaskRules(
    val borderFraction: Double = 0.08,
    val spreadMultiplier: Double = 3.0,
    val minThresholdDeltaE00: Double = 3.0,
    val speckMinAreaFraction: Double = 0.0005,
    val confidence: String = "assumption"
)

/**
 * print_rules.json "shadow_vs_ink": telling a fold shadow from a grey ink on a light garment.
 * No default values: every number comes from the file. If the section is missing or incomplete
 * the loader returns null and the check is not run.
 */
data class ShadowVsInkRules(
    val inkEdgeMaxMm: Double,
    val shadowEdgeMinMm: Double,
    val shadowIfTouchesPlainFabricOrLeavesPrintBox: Boolean,
    val hueTestOnlyIfGarmentChromaAtLeast: Double,
    val shadowKeepsGarmentHueWithinDeg: Double,
    /** Resolved from the reference in the file (resolution.px_per_cm_min_for_fine_detail). */
    val minPxPerCmForEdgeTest: Double,
    val uncertainAction: String,
    val confidence: String
)

/**
 * print_rules.json "photo_vs_artwork": telling a garment photo from a flat artwork file.
 * No default values: every number comes from the file; null if the section is incomplete.
 */
data class PhotoVsArtworkRules(
    val artworkFlatNoiseLStdMax: Double,
    val photoFlatNoiseLStdMin: Double,
    val photoBackgroundLRangeMin: Double,
    val askWhenSignalsDisagreeWithMode: Int,
    val mergeShadesInPhoto: String,
    /** Two shades in a photo can be one ink when their hues differ by at most this (degrees). */
    val mergeShadesInPhotoHueWithinDeg: Double,
    val mergeShadesInArtwork: String,
    val confidence: String
)

/**
 * print_rules.json "edge_blend": thin bands of mixed colour between two inks (or ink and fabric)
 * from camera blur or anti-aliasing. No default values; null if the section is incomplete.
 */
data class EdgeBlendRules(
    val onMixingLineMaxDeltaE00: Double,
    val bandMaxPxFromMeasuredBlurFactor: Double,
    val bandMaxPxMin: Double,
    val bandMaxPxCap: Double,
    val bandMaxPxFallback: Double,
    val bandMaxPxArtwork: Double,
    val sandwichedMinFraction: Double,
    val plateauMinPx: Int,
    val plateauRatioTolerance: Double,
    val monotonicRequired: Boolean,
    /** keyline_protection: a band darker than both neighbours by at least this (L*) is a keyline, never blur. */
    val keylineMinLDrop: Double,
    val confidence: String
)

/**
 * print_rules.json "small_colours": a colour below the minimum coverage is flagged when it is a
 * compact real area, and removed only when it is scattered specks. No default values.
 */
data class SmallColourRules(
    val minCoveragePercent: Double,
    val minRegionMm2: Double,
    val mergeThresholdDeltaE00: Double,
    val confidence: String
)

/**
 * print_rules.json "photo_background": table, floor or wall at the edge of a garment photo.
 * No default values; null if the section is incomplete.
 */
data class PhotoBackgroundRules(
    val minBorderTouchFraction: Double,
    val minAreaPercent: Double,
    val centreBoxFraction: Double,
    val confidence: String
)

/** print_rules.json "artwork_background": the plain background of an artwork file. No defaults. */
data class ArtworkBackgroundRules(val borderShareMin: Double, val flatSpreadMaxDeltaE00: Double, val confidence: String)

/** One entry of print_rules.json "mesh_rules": a mesh range for a kind of screen. */
data class MeshRule(val id: String, val appliesTo: String, val tpiMin: Int, val tpiMax: Int, val confidence: String)

/** Screen types of the Print Report, as named in print_rules.json "screen_type_mesh_rule". */
enum class ScreenType(val key: String) {
    UNDERBASE_WHITE("underbase_white"),
    HIGHLIGHT_WHITE("highlight_white"),
    CMYK_PROCESS("cmyk_process"),
    SPOT_HALFTONE("spot_halftone"),
    FLAT_SPOT("flat_spot")
}

/** The mesh rule for each screen type, from "screen_type_mesh_rule" and "mesh_rules". No defaults. */
data class ScreenMeshRules(val byScreenType: Map<ScreenType, MeshRule>, val mappingConfidence: String)

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
        val root = loadJson() ?: return@withContext emptyMap()
        val map = parseMeshDepositRules(root)
        map.also { cachedDeposits = it }
    }

    /**
     * Loads the garment mask rules from print_rules.json.
     */
    suspend fun getGarmentMaskRules(): GarmentMaskRules = withContext(Dispatchers.IO) {
        val root = loadJson() ?: return@withContext GarmentMaskRules()
        parseGarmentMaskRules(root)
    }

    /**
     * Loads minimum line width in mm by mesh from print_rules.json. Empty if not available.
     */
    /** "shadow_vs_ink" from print_rules.json, or null if it is missing or incomplete. */
    suspend fun getShadowVsInkRules(): ShadowVsInkRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parseShadowVsInkRules(it) }
    }

    /** "photo_vs_artwork" from print_rules.json, or null if it is missing or incomplete. */
    suspend fun getPhotoVsArtworkRules(): PhotoVsArtworkRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parsePhotoVsArtworkRules(it) }
    }

    /** "photo_background" from print_rules.json, or null if it is missing or incomplete. */
    suspend fun getPhotoBackgroundRules(): PhotoBackgroundRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parsePhotoBackgroundRules(it) }
    }

    /** "small_colours" from print_rules.json, or null if it is missing or incomplete. */
    suspend fun getSmallColourRules(): SmallColourRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parseSmallColourRules(it) }
    }

    /** "artwork_background" from print_rules.json, or null if it is missing or incomplete. */
    suspend fun getArtworkBackgroundRules(): ArtworkBackgroundRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parseArtworkBackgroundRules(it) }
    }

    /** Mesh rule per screen type, or null if the sections are missing. */
    suspend fun getScreenMeshRules(): ScreenMeshRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parseScreenMeshRules(it) }
    }

    /** "edge_blend" from print_rules.json, or null if it is missing or incomplete. */
    suspend fun getEdgeBlendRules(): EdgeBlendRules? = withContext(Dispatchers.IO) {
        loadJson()?.let { parseEdgeBlendRules(it) }
    }

    suspend fun getMinLineWidthMmByMesh(): Map<Int, Double> = withContext(Dispatchers.IO) {
        val root = loadJson() ?: return@withContext emptyMap()
        parseMinLineWidthMmByMesh(root)
    }

    companion object {

        /**
         * A number in the rules file, or a "section.key" reference to a number elsewhere in it
         * (for example "resolution.px_per_cm_min_for_fine_detail"). Throws if neither.
         */
        private fun numberOrReference(root: JSONObject, section: JSONObject, key: String): Double {
            val value = section.get(key)
            if (value is Number) return value.toDouble()
            val path = value.toString().split('.')
            require(path.size == 2) { "$key must be a number or a section.key reference" }
            return root.getJSONObject(path[0]).getDouble(path[1])
        }

        /** Reads "shadow_vs_ink". Null if the section or any value is missing. */
        fun parseShadowVsInkRules(root: JSONObject): ShadowVsInkRules? = try {
            val r = root.getJSONObject("shadow_vs_ink")
            ShadowVsInkRules(
                inkEdgeMaxMm = r.getDouble("ink_edge_max_mm"),
                shadowEdgeMinMm = r.getDouble("shadow_edge_min_mm"),
                shadowIfTouchesPlainFabricOrLeavesPrintBox = r.getBoolean("shadow_if_touches_plain_fabric_or_leaves_print_box"),
                hueTestOnlyIfGarmentChromaAtLeast = r.getDouble("hue_test_only_if_garment_chroma_at_least"),
                shadowKeepsGarmentHueWithinDeg = r.getDouble("shadow_keeps_garment_hue_within_deg"),
                minPxPerCmForEdgeTest = numberOrReference(root, r, "min_px_per_cm_for_edge_test"),
                uncertainAction = r.getString("uncertain_action"),
                confidence = r.getString("confidence")
            )
        } catch (e: Exception) {
            null
        }

        /** Reads "photo_background". Null if the section or any value is missing. */
        fun parsePhotoBackgroundRules(root: JSONObject): PhotoBackgroundRules? = try {
            val r = root.getJSONObject("photo_background")
            PhotoBackgroundRules(
                minBorderTouchFraction = r.getDouble("min_border_touch_fraction"),
                minAreaPercent = r.getDouble("min_area_percent"),
                centreBoxFraction = r.getDouble("centre_box_fraction"),
                confidence = r.getString("confidence")
            )
        } catch (e: Exception) {
            null
        }

        /** Reads "small_colours". Null if the section or any value is missing. */
        fun parseSmallColourRules(root: JSONObject): SmallColourRules? = try {
            val r = root.getJSONObject("small_colours")
            SmallColourRules(
                minCoveragePercent = r.getDouble("min_coverage_percent"),
                minRegionMm2 = r.getDouble("min_region_mm2"),
                mergeThresholdDeltaE00 = r.getDouble("merge_threshold_de00"),
                confidence = r.getString("confidence")
            )
        } catch (e: Exception) {
            null
        }

        /**
         * Reads "screen_type_mesh_rule" and "mesh_rules". A screen type whose rule id is not in
         * mesh_rules is left out (the report then shows "not available"). Null if either is missing.
         */
        fun parseScreenMeshRules(root: JSONObject): ScreenMeshRules? = try {
            val mapping = root.getJSONObject("screen_type_mesh_rule")
            val list = root.getJSONArray("mesh_rules")
            val rules = (0 until list.length()).map { list.getJSONObject(it) }.associateBy { it.getString("id") }
            val byType = ScreenType.values().mapNotNull { type ->
                val id = mapping.optString(type.key).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val r = rules[id] ?: return@mapNotNull null
                type to MeshRule(id, r.optString("applies_to"), r.getInt("tpi_min"), r.getInt("tpi_max"), r.optString("confidence"))
            }.toMap()
            ScreenMeshRules(byType, mapping.getString("confidence"))
        } catch (e: Exception) {
            null
        }

        /** Reads "artwork_background". Null if the section or any value is missing. */
        fun parseArtworkBackgroundRules(root: JSONObject): ArtworkBackgroundRules? = try {
            val r = root.getJSONObject("artwork_background")
            ArtworkBackgroundRules(r.getDouble("border_share_min"), r.getDouble("flat_spread_max_de00"), r.getString("confidence"))
        } catch (e: Exception) {
            null
        }

        /** Reads "edge_blend". Null if the section or any value is missing. */
        fun parseEdgeBlendRules(root: JSONObject): EdgeBlendRules? = try {
            val r = root.getJSONObject("edge_blend")
            EdgeBlendRules(
                onMixingLineMaxDeltaE00 = r.getDouble("on_mixing_line_max_de00"),
                bandMaxPxFromMeasuredBlurFactor = r.getDouble("band_max_px_from_measured_blur_factor"),
                bandMaxPxMin = r.getDouble("band_max_px_min"),
                bandMaxPxCap = r.getDouble("band_max_px_cap"),
                bandMaxPxFallback = r.getDouble("band_max_px_fallback"),
                bandMaxPxArtwork = r.getDouble("band_max_px_artwork"),
                sandwichedMinFraction = r.getDouble("sandwiched_min_fraction"),
                plateauMinPx = r.getInt("plateau_min_px"),
                plateauRatioTolerance = r.getDouble("plateau_ratio_tolerance"),
                monotonicRequired = r.getBoolean("monotonic_required"),
                keylineMinLDrop = r.getJSONObject("keyline_protection").getDouble("keyline_min_L_drop"),
                confidence = r.getString("confidence")
            )
        } catch (e: Exception) {
            null
        }

        /** Reads "photo_vs_artwork". Null if the section or any value is missing. */
        fun parsePhotoVsArtworkRules(root: JSONObject): PhotoVsArtworkRules? = try {
            val r = root.getJSONObject("photo_vs_artwork")
            PhotoVsArtworkRules(
                artworkFlatNoiseLStdMax = r.getDouble("artwork_flat_noise_L_std_max"),
                photoFlatNoiseLStdMin = r.getDouble("photo_flat_noise_L_std_min"),
                photoBackgroundLRangeMin = r.getDouble("photo_background_L_range_min"),
                askWhenSignalsDisagreeWithMode = r.getInt("ask_when_signals_disagree_with_mode"),
                mergeShadesInPhoto = r.getString("merge_shades_in_photo"),
                mergeShadesInPhotoHueWithinDeg = r.getDouble("merge_shades_in_photo_hue_within_deg"),
                mergeShadesInArtwork = r.getString("merge_shades_in_artwork"),
                confidence = r.getString("confidence")
            )
        } catch (e: Exception) {
            null
        }

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
                confidence = whiteBase?.optString("confidence", fallback.confidence) ?: fallback.confidence,
                garmentChromaThreshold = whiteBase?.optDouble("garment_chroma_threshold", fallback.garmentChromaThreshold)
                    ?: fallback.garmentChromaThreshold,
                qualityToleranceP95DeltaE00 = recommendation?.optDouble("quality_tolerance_p95_de00", fallback.qualityToleranceP95DeltaE00)
                    ?: fallback.qualityToleranceP95DeltaE00,
                cmykMinHueFamilies = recommendation?.optInt("cmyk_min_hue_families", fallback.cmykMinHueFamilies)
                    ?: fallback.cmykMinHueFamilies
            )
        }

        /**
         * Reads "garment_mask": border size, spread multiplier, minimum threshold and speck size.
         */
        fun parseGarmentMaskRules(root: JSONObject): GarmentMaskRules {
            val fallback = GarmentMaskRules()
            val g = root.optJSONObject("garment_mask") ?: return fallback
            return GarmentMaskRules(
                borderFraction = g.optDouble("border_fraction", fallback.borderFraction),
                spreadMultiplier = g.optDouble("spread_multiplier", fallback.spreadMultiplier),
                minThresholdDeltaE00 = g.optDouble("min_threshold_de00", fallback.minThresholdDeltaE00),
                speckMinAreaFraction = g.optDouble("speck_min_area_fraction", fallback.speckMinAreaFraction),
                confidence = g.optString("confidence", fallback.confidence)
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
                // An entry missing any of the three values is left out: no number is made up.
                if (!item.has("tpi") || !item.has("deposit_g_m2_default") ||
                    !item.has("deposit_g_m2_min") || !item.has("deposit_g_m2_max")
                ) continue
                val tpi = item.getInt("tpi")
                map[tpi] = MeshDepositRule(
                    tpi = tpi,
                    depositGPerM2Default = item.getDouble("deposit_g_m2_default"),
                    depositGPerM2Min = item.getDouble("deposit_g_m2_min"),
                    depositGPerM2Max = item.getDouble("deposit_g_m2_max"),
                    confidence = item.optString("confidence", "assumption")
                )
            }
            return map
        }

        /**
         * Reads "detail_rules.min_line_width_mm_by_mesh": mesh tpi -> minimum line width in mm.
         * Empty if the section is missing; callers then show "not available".
         */
        fun parseMinLineWidthMmByMesh(root: JSONObject): Map<Int, Double> {
            val map = mutableMapOf<Int, Double>()
            val arr = root.optJSONObject("detail_rules")
                ?.optJSONArray("min_line_width_mm_by_mesh") ?: return map
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.has("tpi") && item.has("min_line_mm")) {
                    map[item.getInt("tpi")] = item.getDouble("min_line_mm")
                }
            }
            return map
        }

    }
}
