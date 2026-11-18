package com.mos.nira.features.artwork.domain

import com.mos.nira.core.image.InputKind
import com.mos.nira.core.image.InputKindAssessment
import android.graphics.Bitmap
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.data.ProcessInksConfig

/**
 * Normalized rectangle in relative [0.0, 1.0] coordinates for interactive canvas erasing.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val minX: Float get() = minOf(left, right).coerceIn(0f, 1f)
    val maxX: Float get() = maxOf(left, right).coerceIn(0f, 1f)
    val minY: Float get() = minOf(top, bottom).coerceIn(0f, 1f)
    val maxY: Float get() = maxOf(top, bottom).coerceIn(0f, 1f)

    fun contains(normX: Float, normY: Float): Boolean {
        return normX in minX..maxX && normY in minY..maxY
    }
}

/**
 * Profile of the measured garment background fabric.
 */
data class GarmentProfile(
    val estimatedHex: String,
    val medianLab: ColorConverter.Lab,
    val spreadDeltaE00: Double,
    val adaptiveThresholdDeltaE00: Double,
    val isUserOverridden: Boolean = false
) {
    val formattedSpread: String get() = String.format("Spread: ±%.1f ΔE", spreadDeltaE00)
    val formattedThreshold: String get() = String.format("Adaptive: %.1f ΔE", adaptiveThresholdDeltaE00)
}

/**
 * Input parameters for screen print calculation and engineering report.
 */
data class PrintJobInputs(
    val garmentColorHex: String = "#FFFFFF",
    val garmentThresholdDeltaE00: Double = 5.0, // User-adjustable CIEDE2000 threshold for non-white garments
    val printWidthCm: Double = 30.0,
    val meshTpi: Int = 156,
    val strokes: Int = 1,
    val wastagePercent: Double = 10.0, // 10%
    val erasedRectangles: List<NormalizedRect> = emptyList(),
    val printBox: NormalizedRect? = null, // Bounding box detected or adjusted by user
    val userGarmentHex: String? = null, // "Tap the fabric" override
    val useWhiteBaseOverride: Boolean? = null, // User switch to force or disable white base
    // If white is one of the print colours it also acts as the base, so no extra screen is added
    // (print_rules.json, white_base.white_print_colour_can_act_as_base). Switch off for a separate base.
    val whitePrintActsAsBase: Boolean = true,
    val orderPieces: Int = 100, // Order quantity for ink consumption
    val highlightWhiteThreshold: Double = 80.0,
    // What the user loaded (print_rules.json photo_vs_artwork "primary": the user's chosen mode),
    // whether they confirmed it after the app asked, and whether the file has camera information
    // (null = not known).
    val inputKind: InputKind = InputKind.ARTWORK,
    val inputKindConfirmed: Boolean = false,
    val cameraExif: Boolean? = null,
    // The user's answers for regions marked "Possible fold shadow - tap to confirm".
    val regionAnswers: List<RegionAnswer> = emptyList()
)

/** The user's answer for one uncertain region: a point inside it (normalised) and ink or shadow. */
data class RegionAnswer(val normX: Float, val normY: Float, val isInk: Boolean)

/**
 * Measured colour layer in the analyzed artwork print area.
 */
data class MeasuredColor(
    val id: Int,
    val hex: String,
    val rgb: ColorConverter.Rgb,
    val lab: ColorConverter.Lab,
    val pixelCount: Int,
    val coveragePercent: Double, // % of active print mask pixels
    val totalBoxCoveragePercent: Double, // % of total bounding box pixels
    val areaCm2: Double, // Physical print area in cm²
    val nearestShade: ShadeEntry? = null,
    val nearestShadeDeltaE00: Double? = null,
    val matchGrade: String? = null,
    /** Some pixels of this colour come from regions that may be fold shadows (not confirmed). */
    val possibleFoldShadow: Boolean = false,
    /** A point inside each of those regions (normalised), used to record the user's answer. */
    val uncertainRegionPoints: List<Pair<Float, Float>> = emptyList()
) {
    val formattedRgb: String get() = "RGB(${rgb.r}, ${rgb.g}, ${rgb.b})"
    val formattedLab: String get() = String.format("L* %.1f  a* %.1f  b* %.1f", lab.l, lab.a, lab.b)
    val formattedCoverage: String get() = String.format("%.1f%%", coveragePercent)
    val formattedArea: String get() = String.format("%.2f cm²", areaCm2)
}

/**
 * Single evaluation point on the flat-separation posterisation error curve.
 */
data class ErrorCurvePoint(
    val k: Int,
    val meanDeltaE00: Double
) {
    val formattedMeanError: String get() = String.format("ΔE00 %.2f", meanDeltaE00)
}

/**
 * Output result of Part A: Print area, mask, measured palette, and flat-separation error curve.
 */
data class PartAResult(
    val printWidthCm: Double,
    val printHeightCm: Double,
    val totalBoxAreaCm2: Double,
    val effectivePrintAreaCm2: Double,
    val printPixelCount: Int,
    val totalPixelCount: Int,
    val printMaskCoveragePercent: Double,
    val measuredPalette: List<MeasuredColor>,
    val errorCurve: List<ErrorCurvePoint>,
    val maskBitmap: Bitmap? = null,
    val garmentProfile: GarmentProfile? = null,
    val printBox: NormalizedRect = NormalizedRect(0f, 0f, 1f, 1f),
    val pixelsPerCm: Double = 0.0,
    val lowResolutionWarning: String? = null,
    /** Photo or artwork signals against the user's chosen mode (print_rules.json photo_vs_artwork). */
    val inputKindAssessment: InputKindAssessment? = null,
    /** Regions removed as fold shadows (print_rules.json shadow_vs_ink). */
    val foldShadowsRemoved: Int = 0,
    /** True when the fold-shadow edge test could not run because the resolution is too low. */
    val shadowTestLowResolution: Boolean = false
)

// ==========================================
// PART B: Separation Strategies Models
// ==========================================

enum class SeparationStrategyType {
    CMYK_PROCESS,
    BEST_N_SPOT_HALFTONE,
    FLAT_SPOT_SOLID,
    EXTENDED_SPOT
}

/**
 * Evaluated separation strategy metrics and proof.
 */
data class StrategyEvaluationResult(
    val strategyType: SeparationStrategyType,
    val name: String,
    val screenCount: Int,
    val inkHexes: List<String>,
    val inkNames: List<String>,
    val meanDeltaE00: Double,
    val percentile95DeltaE00: Double,
    val percentWithinDeltaE3: Double,
    val darkGarmentWarning: String? = null,
    val isDarkGarment: Boolean = false,
    val isRecommended: Boolean = false,
    val recommendationReason: String = "",
    val transitionNote: String? = null,
    val notes: String = "",
    val proofBitmap: Bitmap? = null
) {
    val formattedMeanDeltaE00: String get() = String.format("ΔE00 %.2f", meanDeltaE00)
    val formattedPercentile95: String get() = String.format("ΔE00 %.2f", percentile95DeltaE00)
    val formattedPercentWithin3: String get() = String.format("%.1f%%", percentWithinDeltaE3)
}

/**
 * Complete Part B output containing strategy comparisons and proof models.
 */
data class PartBResult(
    val strategies: List<StrategyEvaluationResult>,
    val selectedStrategyType: SeparationStrategyType = SeparationStrategyType.CMYK_PROCESS,
    val processInks: ProcessInksConfig = ProcessInksConfig(),
    val isDarkGarment: Boolean = false,
    val executionTimeMs: Long = 0L,
    val disclaimer: String = "Model result. No dot gain or registration error included.",
    /** True when spot inks are modelled as opaque over the garment (dark or coloured garment). */
    val opaqueInkModel: Boolean = false
)

// ==========================================
// PART C: Screens, Proof & Film Positives
// ==========================================

/**
 * Greyscale film positive channel for a single ink screen.
 * Black (0) = 100% ink coverage, White (255) = 0% ink coverage.
 */
data class FilmPositiveChannel(
    val channelIndex: Int,
    val inkName: String,
    val inkHex: String,
    val inkRgb: ColorConverter.Rgb,
    val averageCoveragePercent: Double, // Over active print mask
    val boxCoveragePercent: Double,     // Over entire print bounding box
    val filmPositiveBitmap: Bitmap? = null,
    val rawGreyscalePixels: IntArray? = null,
    val recommendedMesh: String = "156 tpi"
) {
    val formattedCoverage: String get() = String.format("%.1f%%", averageCoveragePercent)
    val formattedBoxCoverage: String get() = String.format("%.1f%%", boxCoveragePercent)
}

/**
 * Complete Part C output containing original, proof, and all film positive channels.
 */
data class PartCResult(
    val strategyType: SeparationStrategyType,
    val strategyName: String,
    val originalBitmap: Bitmap? = null,
    val proofBitmap: Bitmap? = null,
    val proofPixels: IntArray? = null,
    val channels: List<FilmPositiveChannel> = emptyList(),
    val compositeSheetBitmap: Bitmap? = null,
    val executionTimeMs: Long = 0L,
    val disclaimer: String = "Preliminary digital separation, not print-ready films. Model has no dot gain or registration error. Needs original artwork at about 300 dpi at print size."
)

// ==========================================
// PART D: Ink Consumption Models
// ==========================================

/**
 * Calculated ink consumption for an individual separation screen channel.
 */
data class InkChannelConsumption(
    val channelIndex: Int,
    val inkName: String,
    val inkHex: String,
    val meshTpi: Int,
    val boxCoveragePercent: Double,
    val depositGPerM2Default: Double,
    val depositGPerM2Min: Double,
    val depositGPerM2Max: Double,
    val depositConfidence: String,
    val strokes: Int,
    val wastagePercent: Double,
    val inkGramsPerGarment: Double,
    val inkGramsPerGarmentMin: Double,
    val inkGramsPerGarmentMax: Double,
    val inkGramsPer100Pieces: Double,
    val inkGramsPer100PiecesMin: Double,
    val inkGramsPer100PiecesMax: Double
) {
    val formattedPerGarment: String get() = String.format("%.2f g (min %.2f, max %.2f)", inkGramsPerGarment, inkGramsPerGarmentMin, inkGramsPerGarmentMax)
    val formattedPer100: String get() = String.format("%.1f g (min %.1f, max %.1f)", inkGramsPer100Pieces, inkGramsPer100PiecesMin, inkGramsPer100PiecesMax)
}

/**
 * Complete Part D output containing all channel consumptions and job totals.
 */
data class PartDResult(
    val channels: List<InkChannelConsumption>,
    val totalGramsPerGarment: Double,
    val totalGramsPerGarmentMin: Double,
    val totalGramsPerGarmentMax: Double,
    val totalGramsPer100Pieces: Double,
    val totalGramsPer100PiecesMin: Double,
    val totalGramsPer100PiecesMax: Double,
    val printBoxAreaM2: Double,
    val orderPieces: Int = 100,
    val note: String = "Screen residue and set-up loss are not included."
)

