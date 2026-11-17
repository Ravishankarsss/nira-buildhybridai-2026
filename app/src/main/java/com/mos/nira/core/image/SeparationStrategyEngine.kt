package com.mos.nira.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.mos.nira.features.artwork.data.MeshDepositRule
import com.mos.nira.features.artwork.data.ProcessInksConfig
import com.mos.nira.features.artwork.data.SeparationDecisionRules
import com.mos.nira.features.artwork.domain.FilmPositiveChannel
import com.mos.nira.features.artwork.domain.InkChannelConsumption
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PartDResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import com.mos.nira.features.artwork.domain.StrategyEvaluationResult
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic engineering calculation engine for NIRA Print Report — Parts B, C, & D:
 * - Garment-relative opaque ink model on dark garments & absorbance model on light garments
 * - Lawson-Hanson Non-Negative Least Squares (NNLS) solver with 6-bit quantization & exact error metrics
 * - Recommendation logic: fewest screens meeting quality tolerance (ΔE00 limit from print_rules.json)
 * - White screen generation: base white with physical choke (mm -> px) & highlight white
 * - Side-by-side proof preview & composite separation sheet export
 * - Part D: Industrial ink consumption calculations per channel and job totals
 */
object SeparationStrategyEngine {

    const val MODEL_DISCLAIMER = "Model result. No dot gain or registration error included."
    const val PRELIMINARY_DISCLAIMER =
        "Preliminary digital separation, not print-ready films. Model has no dot gain or registration error. Needs original artwork at about 300 dpi at print size."

    private const val MIN_LINEAR = 0.004
    private const val MAX_LINEAR = 1.0

    /**
     * Converts an sRGB color (0..255) to 3D absorbance vector [-ln(R), -ln(G), -ln(B)].
     */
    fun rgbToAbsorbance(r: Int, g: Int, b: Int): DoubleArray {
        val rLin = ColorConverter.sRgbToLinear(r).coerceIn(MIN_LINEAR, MAX_LINEAR)
        val gLin = ColorConverter.sRgbToLinear(g).coerceIn(MIN_LINEAR, MAX_LINEAR)
        val bLin = ColorConverter.sRgbToLinear(b).coerceIn(MIN_LINEAR, MAX_LINEAR)
        return doubleArrayOf(-ln(rLin), -ln(gLin), -ln(bLin))
    }

    fun hexToAbsorbance(hex: String): DoubleArray {
        val rgb = ColorConverter.hexToRgb(hex)
        return rgbToAbsorbance(rgb.r, rgb.g, rgb.b)
    }

    /**
     * Reconstructs an sRGB pixel from an absorbance vector: exp(-a) -> linear -> sRGB.
     */
    fun absorbanceToRgb(absorbance: DoubleArray): ColorConverter.Rgb {
        val rLin = exp(-absorbance[0]).coerceIn(0.0, 1.0)
        val gLin = exp(-absorbance[1]).coerceIn(0.0, 1.0)
        val bLin = exp(-absorbance[2]).coerceIn(0.0, 1.0)
        return ColorConverter.Rgb(
            ColorConverter.linearToSRgb(rLin),
            ColorConverter.linearToSRgb(gLin),
            ColorConverter.linearToSRgb(bLin)
        )
    }

    /**
     * Evaluates all separation strategies for the given artwork and Part A result.
     */
    fun evaluateStrategies(
        maskedPixels: IntArray, // Array of original print-masked RGB pixels
        partA: PartAResult,
        inputs: PrintJobInputs,
        processInks: ProcessInksConfig,
        decisionRules: SeparationDecisionRules = SeparationDecisionRules(),
        selectedStrategy: SeparationStrategyType = SeparationStrategyType.CMYK_PROCESS,
        generateProofBitmaps: Boolean = false
    ): PartBResult {
        val startTime = System.currentTimeMillis()

        // Garment colour as measured in Part A (image border, or the tapped fabric colour).
        // The preset is used only when nothing was measured.
        val garmentLab = measuredGarmentLab(partA, inputs)
        val garmentRgb = ColorConverter.labToRgb(garmentLab.l, garmentLab.a, garmentLab.b)
        val garmentLinear = doubleArrayOf(
            ColorConverter.sRgbToLinear(garmentRgb.r),
            ColorConverter.sRgbToLinear(garmentRgb.g),
            ColorConverter.sRgbToLinear(garmentRgb.b)
        )

        // print_rules.json white_base: a base is needed when the garment is below the lightness
        // limit or above the chroma limit, and the print has colours lighter than the garment.
        // The same garment test selects the opaque ink model (ink_model).
        val garmentIsDarkOrColoured = garmentLab.l < decisionRules.darkGarmentThresholdL ||
            hypot(garmentLab.a, garmentLab.b) > decisionRules.garmentChromaThreshold
        val printIsLighterThanGarment = partA.measuredPalette.isEmpty() ||
            partA.measuredPalette.any { it.lab.l > garmentLab.l }
        val isDarkGarment = inputs.useWhiteBaseOverride ?: (garmentIsDarkOrColoured && printIsLighterThanGarment)

        if (maskedPixels.isEmpty()) {
            val emptyList = listOf(
                createEmptyStrategy(SeparationStrategyType.CMYK_PROCESS, "CMYK Process", 4, isDarkGarment),
                createEmptyStrategy(SeparationStrategyType.BEST_N_SPOT_HALFTONE, "Best N Spot (Halftone)", 4, isDarkGarment),
                createEmptyStrategy(SeparationStrategyType.FLAT_SPOT_SOLID, "Flat Spot (Solid Screen)", 4, isDarkGarment)
            )
            return PartBResult(
                strategies = emptyList,
                selectedStrategyType = selectedStrategy,
                processInks = processInks,
                isDarkGarment = isDarkGarment,
                executionTimeMs = System.currentTimeMillis() - startTime,
                disclaimer = MODEL_DISCLAIMER,
                opaqueInkModel = garmentIsDarkOrColoured
            )
        }

        // Unique quantized colors map (6-bit quantization = 64 levels/channel)
        val quantizedPalette = quantizePixels6Bit(maskedPixels)

        // Filter out any measured colors that are the garment colour
        val nonGarmentColors = partA.measuredPalette.filter {
            ColorConverter.deltaE00(it.lab, garmentLab) > 3.8
        }

        // Detect if white is already in the print palette
        val hasWhitePrintColor = nonGarmentColors.any {
            it.lab.l > decisionRules.highlightWhiteThresholdL && hypot(it.lab.a, it.lab.b) < decisionRules.highlightWhiteMaxChroma
        }
        val whiteActsAsBase = hasWhitePrintColor && inputs.whitePrintActsAsBase

        // White screens added on top of the spot inks:
        // - base white, unless a white print colour acts as the base
        // - highlight white, only when white is not already one of the print colours
        val spotExtraWhites = (if (isDarkGarment && !whiteActsAsBase) 1 else 0) +
            (if (isDarkGarment && !hasWhitePrintColor) 1 else 0)
        val spotWhiteNote = when {
            spotExtraWhites == 2 -> "Base white underbase and highlight white added."
            spotExtraWhites == 1 -> "Separate base white underbase added."
            isDarkGarment && whiteActsAsBase -> "White print colour also acts as the base. No extra screen."
            else -> null
        }
        // CMYK process needs a white substrate: base white and highlight white on a dark garment.
        val cmykExtraWhites = if (isDarkGarment) 2 else 0

        // Detect gradient transitions between pairs of inks (e.g. blue fading into white)
        val transitionNote = detectTransitions(nonGarmentColors)

        val strategies = mutableListOf<StrategyEvaluationResult>()

        // -------------------------------------------------------------
        // Strategy 1: Spot Halftone (Best N Spot with Halftone Blending)
        // -------------------------------------------------------------
        val bestNCount = min(6, max(1, nonGarmentColors.size))
        val bestNColors = nonGarmentColors.take(bestNCount)
        val bestNHexes = bestNColors.map { it.hex }
        val bestNNames = bestNColors.map { it.nearestShade?.name ?: it.hex }

        val totalMaskedCount = maskedPixels.size

        val bestNResult = evaluateStrategyImplementation(
            strategyType = SeparationStrategyType.BEST_N_SPOT_HALFTONE,
            name = if (spotExtraWhites > 0) "Best $bestNCount Spot Inks (+ Whites)" else "Best $bestNCount Spot Inks (Halftone)",
            inkHexes = bestNHexes,
            inkNames = bestNNames,
            totalPixelCount = totalMaskedCount,
            quantizedPalette = quantizedPalette,
            isDarkGarment = isDarkGarment,
            opaque = garmentIsDarkOrColoured,
            garmentLinear = garmentLinear,
            extraWhiteScreens = spotExtraWhites,
            darkWarning = spotWhiteNote,
            transitionNote = transitionNote,
            notes = "Halftone screen blending of top $bestNCount dominant artwork inks."
        )
        strategies.add(bestNResult)

        // -------------------------------------------------------------
        // Strategy 2: Flat Spot Colours (Pure Solid Screens)
        // -------------------------------------------------------------
        val flatSpotResult = evaluateFlatSpotStrategy(
            measuredColors = nonGarmentColors,
            totalPixelCount = totalMaskedCount,
            quantizedPalette = quantizedPalette,
            isDarkGarment = isDarkGarment,
            extraWhiteScreens = spotExtraWhites,
            whiteNote = spotWhiteNote
        )
        strategies.add(flatSpotResult)

        // -------------------------------------------------------------
        // Strategy 3: CMYK Process (4 process inks)
        // -------------------------------------------------------------
        // Offer CMYK only for tonal artwork with enough distinct hue families
        // (print_rules.json recommendation.cmyk_min_hue_families).
        val hueFamilies = countHueFamilies(nonGarmentColors)
        val isTonalRichArtwork = hueFamilies >= decisionRules.cmykMinHueFamilies
        val cmykInks = listOf(
            processInks.cyanHex,
            processInks.magentaHex,
            processInks.yellowHex,
            processInks.blackHex
        )
        val cmykNames = listOf("Cyan", "Magenta", "Yellow", "Black")
        val cmykResult = evaluateStrategyImplementation(
            strategyType = SeparationStrategyType.CMYK_PROCESS,
            name = if (cmykExtraWhites > 0) "CMYK Process (+ Whites)" else "CMYK Process",
            inkHexes = cmykInks,
            inkNames = cmykNames,
            totalPixelCount = totalMaskedCount,
            quantizedPalette = quantizedPalette,
            isDarkGarment = isDarkGarment,
            // Process inks are transparent and always print over white (garment or base white).
            opaque = false,
            garmentLinear = garmentLinear,
            extraWhiteScreens = cmykExtraWhites,
            darkWarning = if (isDarkGarment) {
                "On a dark garment, CMYK alone is not suitable without an underbase white."
            } else null,
            transitionNote = null,
            notes = if (isTonalRichArtwork) "Continuous halftone process print. ${processInks.confidence.uppercase()} confidence."
            else "CMYK process is not suitable: the artwork has $hueFamilies hue families, fewer than ${decisionRules.cmykMinHueFamilies}."
        )
        strategies.add(cmykResult)

        // -------------------------------------------------------------
        // Strategy 4: Extended Spot Gamut (Top 8 with NNLS mixing)
        // -------------------------------------------------------------
        if (nonGarmentColors.size > bestNCount) {
            val extCount = min(8, nonGarmentColors.size)
            val extColors = nonGarmentColors.take(extCount)
            val extHexes = extColors.map { it.hex }
            val extNames = extColors.map { it.nearestShade?.name ?: it.hex }

            val extResult = evaluateStrategyImplementation(
                strategyType = SeparationStrategyType.EXTENDED_SPOT,
                name = if (spotExtraWhites > 0) "Extended $extCount Spot Inks (+ Whites)" else "Extended $extCount Spot Inks",
                inkHexes = extHexes,
                inkNames = extNames,
                totalPixelCount = totalMaskedCount,
                quantizedPalette = quantizedPalette,
                isDarkGarment = isDarkGarment,
                opaque = garmentIsDarkOrColoured,
                garmentLinear = garmentLinear,
                extraWhiteScreens = spotExtraWhites,
                darkWarning = spotWhiteNote,
                transitionNote = transitionNote,
                notes = "Extended spot gamut utilizing top $extCount color screen separations."
            )
            strategies.add(extResult)
        }

        // Recommendation: the FEWEST screens that meet the quality tolerance (mean and 95%
        // limits from print_rules.json), not the lowest colour error. CMYK is a candidate only
        // for tonal artwork with enough hue families. The user can still pick any option.
        val qualityTolerance = decisionRules.qualityToleranceDeltaE00
        val qualityToleranceP95 = decisionRules.qualityToleranceP95DeltaE00
        val candidates = strategies.filter {
            it.strategyType != SeparationStrategyType.CMYK_PROCESS || isTonalRichArtwork
        }
        val candidatesMeetingTolerance = candidates.filter {
            it.meanDeltaE00 <= qualityTolerance && it.percentile95DeltaE00 <= qualityToleranceP95
        }
        val meetsTolerance = candidatesMeetingTolerance.isNotEmpty()

        val bestStrategy = if (meetsTolerance) {
            candidatesMeetingTolerance.minByOrNull { it.screenCount }
        } else {
            candidates.minByOrNull { it.meanDeltaE00 }
        }

        // Say plainly when CMYK was left out by the hue-family rule, so a lower CMYK error
        // is not mistaken for a missed recommendation.
        val cmykNote = if (!isTonalRichArtwork) {
            " CMYK process is offered only for tonal artwork with at least ${decisionRules.cmykMinHueFamilies} hue families; this artwork has $hueFamilies."
        } else ""

        val finalizedStrategies = strategies.map { s ->
            if (s.strategyType == bestStrategy?.strategyType) {
                val figures = String.format(
                    java.util.Locale.US,
                    "mean ΔE00 %.2f, 95%% ΔE00 %.2f",
                    s.meanDeltaE00,
                    s.percentile95DeltaE00
                )
                s.copy(
                    isRecommended = true,
                    recommendationReason = if (meetsTolerance) {
                        "Recommended: fewest screens (${s.screenCount}) within tolerance ($figures; limits $qualityTolerance and $qualityToleranceP95).$cmykNote"
                    } else {
                        "No offered option is within tolerance (limits $qualityTolerance and $qualityToleranceP95). Lowest colour error among the offered options: $figures.$cmykNote"
                    }
                )
            } else s
        }

        val totalTime = System.currentTimeMillis() - startTime

        return PartBResult(
            strategies = finalizedStrategies,
            selectedStrategyType = selectedStrategy,
            processInks = processInks,
            isDarkGarment = isDarkGarment,
            executionTimeMs = totalTime,
            disclaimer = MODEL_DISCLAIMER,
            opaqueInkModel = garmentIsDarkOrColoured
        )
    }

    /**
     * Garment colour measured in Part A (median of the image border, or the tapped fabric
     * colour). Falls back to the chosen preset only when Part A has no garment profile.
     */
    fun measuredGarmentLab(partA: PartAResult, inputs: PrintJobInputs): ColorConverter.Lab {
        partA.garmentProfile?.let { return it.medianLab }
        val rgb = ColorConverter.hexToRgb(inputs.userGarmentHex ?: inputs.garmentColorHex)
        return ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
    }

    /**
     * Algorithm settings, not print rules (to be confirmed by Ravi): a colour with chroma below
     * NEUTRAL_CHROMA_LIMIT is neutral, and chromatic colours are grouped into hue families of
     * HUE_FAMILY_WIDTH_DEGREES around the Lab hue circle.
     */
    const val NEUTRAL_CHROMA_LIMIT = 12.0
    const val HUE_FAMILY_WIDTH_DEGREES = 30.0

    /** Number of distinct hue families among the chromatic print colours. */
    fun countHueFamilies(colors: List<MeasuredColor>): Int {
        val families = HashSet<Int>()
        for (c in colors) {
            if (hypot(c.lab.a, c.lab.b) < NEUTRAL_CHROMA_LIMIT) continue
            var hue = Math.toDegrees(kotlin.math.atan2(c.lab.b, c.lab.a))
            if (hue < 0) hue += 360.0
            families.add((hue / HUE_FAMILY_WIDTH_DEGREES).toInt())
        }
        return families.size
    }

    /**
     * Quantized color bin holding representative RGB and total pixel count.
     */
    data class QuantizedBin(val r: Int, val g: Int, val b: Int, var count: Int)

    /**
     * Solves NNLS per 6-bit colour bin. Quality figures compare the proof with the colour of
     * the bin (the pixel colour rounded to 6 bits per channel), weighted by pixel count.
     * PrintReportRegressionTest measures how much that rounding adds.
     */
    private fun evaluateStrategyImplementation(
        strategyType: SeparationStrategyType,
        name: String,
        inkHexes: List<String>,
        inkNames: List<String>,
        totalPixelCount: Int,
        quantizedPalette: Map<Int, QuantizedBin>,
        isDarkGarment: Boolean,
        opaque: Boolean,
        garmentLinear: DoubleArray,
        extraWhiteScreens: Int,
        darkWarning: String?,
        transitionNote: String?,
        notes: String
    ): StrategyEvaluationResult {
        val numInks = inkHexes.size
        if (numInks == 0) {
            return createEmptyStrategy(strategyType, name, 0, isDarkGarment)
        }

        // Cache solved proof colors for each quantized color bin
        val binProofMap = HashMap<Int, ColorConverter.Rgb>(quantizedPalette.size)

        if (opaque) {
            // OPAQUE INK MODEL on dark or coloured garments (print_rules.json ink_model):
            // pixel = (1 - sum c_j) * garment + sum (c_j * ink_j), in linear RGB.
            // Solved as: sum c_j * (ink_j - garment) = pixel - garment, so a pixel equal to the
            // garment has zero ink on every screen.
            val inkLinears = Array(numInks) { i ->
                val rgb = ColorConverter.hexToRgb(inkHexes[i])
                doubleArrayOf(
                    ColorConverter.sRgbToLinear(rgb.r) - garmentLinear[0],
                    ColorConverter.sRgbToLinear(rgb.g) - garmentLinear[1],
                    ColorConverter.sRgbToLinear(rgb.b) - garmentLinear[2]
                )
            }

            for ((binKey, bin) in quantizedPalette) {
                val targetR = ColorConverter.sRgbToLinear(bin.r) - garmentLinear[0]
                val targetG = ColorConverter.sRgbToLinear(bin.g) - garmentLinear[1]
                val targetB = ColorConverter.sRgbToLinear(bin.b) - garmentLinear[2]
                val target = doubleArrayOf(targetR, targetG, targetB)

                val amounts = LawsonHansonNnls.solve(inkLinears, target)
                var sumC = 0.0
                for (j in amounts.indices) {
                    if (amounts[j] > 1.0) amounts[j] = 1.0
                    sumC += amounts[j]
                }
                if (sumC > 1.0) {
                    for (j in amounts.indices) amounts[j] /= sumC
                }

                var pR = garmentLinear[0]
                var pG = garmentLinear[1]
                var pB = garmentLinear[2]
                for (j in 0 until numInks) {
                    pR += amounts[j] * inkLinears[j][0]
                    pG += amounts[j] * inkLinears[j][1]
                    pB += amounts[j] * inkLinears[j][2]
                }

                binProofMap[binKey] = ColorConverter.Rgb(
                    ColorConverter.linearToSRgb(pR.coerceIn(0.0, 1.0)),
                    ColorConverter.linearToSRgb(pG.coerceIn(0.0, 1.0)),
                    ColorConverter.linearToSRgb(pB.coerceIn(0.0, 1.0))
                )
            }
        } else {
            // ABSORBANCE INK MODEL on light/white garments
            val inkAbsorbances = Array(numInks) { hexToAbsorbance(inkHexes[it]) }

            for ((binKey, bin) in quantizedPalette) {
                val targetAbs = rgbToAbsorbance(bin.r, bin.g, bin.b)
                val amounts = LawsonHansonNnls.solve(inkAbsorbances, targetAbs)

                for (j in amounts.indices) {
                    if (amounts[j] > 1.0) amounts[j] = 1.0
                }

                val proofAbs = DoubleArray(3)
                for (j in 0 until numInks) {
                    val cJ = amounts[j]
                    if (cJ > 0.0) {
                        proofAbs[0] += cJ * inkAbsorbances[j][0]
                        proofAbs[1] += cJ * inkAbsorbances[j][1]
                        proofAbs[2] += cJ * inkAbsorbances[j][2]
                    }
                }
                binProofMap[binKey] = absorbanceToRgb(proofAbs)
            }
        }

        // Compute error distribution: Evaluated across unique bins weighted by pixel count!
        var sumDelta = 0.0
        var countWithin3 = 0
        val binDeltas = ArrayList<Pair<Double, Int>>(quantizedPalette.size)

        for ((binKey, bin) in quantizedPalette) {
            val proofRgb = binProofMap[binKey] ?: ColorConverter.Rgb(bin.r, bin.g, bin.b)
            val origLab = ColorConverter.rgbToLab(bin.r, bin.g, bin.b)
            val proofLab = ColorConverter.rgbToLab(proofRgb.r, proofRgb.g, proofRgb.b)
            val dE = ColorConverter.deltaE00(origLab, proofLab)

            sumDelta += dE * bin.count
            if (dE <= 3.0) {
                countWithin3 += bin.count
            }
            binDeltas.add(Pair(dE, bin.count))
        }

        binDeltas.sortBy { it.first }
        var acc = 0
        val target95 = (totalPixelCount * 0.95).toInt()
        var p95 = 0.0
        for ((dE, count) in binDeltas) {
            acc += count
            if (acc >= target95) {
                p95 = dE
                break
            }
        }

        val meanDeltaE = if (totalPixelCount > 0) sumDelta / totalPixelCount else 0.0
        val pctWithin3 = if (totalPixelCount > 0) (countWithin3.toDouble() / totalPixelCount) * 100.0 else 0.0

        val screenCount = numInks + extraWhiteScreens

        return StrategyEvaluationResult(
            strategyType = strategyType,
            name = name,
            screenCount = screenCount,
            inkHexes = inkHexes,
            inkNames = inkNames,
            meanDeltaE00 = meanDeltaE,
            percentile95DeltaE00 = p95,
            percentWithinDeltaE3 = pctWithin3,
            darkGarmentWarning = darkWarning,
            isDarkGarment = isDarkGarment,
            transitionNote = transitionNote,
            notes = notes,
            proofBitmap = null
        )
    }

    private fun evaluateFlatSpotStrategy(
        measuredColors: List<MeasuredColor>,
        totalPixelCount: Int,
        quantizedPalette: Map<Int, QuantizedBin>,
        isDarkGarment: Boolean,
        extraWhiteScreens: Int,
        whiteNote: String?
    ): StrategyEvaluationResult {
        if (measuredColors.isEmpty()) {
            return createEmptyStrategy(SeparationStrategyType.FLAT_SPOT_SOLID, "Flat Spot (Solid Screen)", 0, isDarkGarment)
        }

        val inkHexes = measuredColors.map { it.hex }
        val inkNames = measuredColors.map { it.nearestShade?.name ?: it.hex }
        val centroids = measuredColors.map { it.lab }

        var sumDelta = 0.0
        var countWithin3 = 0
        val binDeltas = ArrayList<Pair<Double, Int>>(quantizedPalette.size)

        for ((_, bin) in quantizedPalette) {
            val lab = ColorConverter.rgbToLab(bin.r, bin.g, bin.b)
            var minD = Double.MAX_VALUE
            for (c in centroids) {
                val d = ColorConverter.deltaE00(lab, c)
                if (d < minD) {
                    minD = d
                }
            }
            sumDelta += minD * bin.count
            if (minD <= 3.0) {
                countWithin3 += bin.count
            }
            binDeltas.add(Pair(minD, bin.count))
        }

        binDeltas.sortBy { it.first }
        var acc = 0
        val target95 = (totalPixelCount * 0.95).toInt()
        var p95 = 0.0
        for ((dE, count) in binDeltas) {
            acc += count
            if (acc >= target95) {
                p95 = dE
                break
            }
        }

        val meanDeltaE = if (totalPixelCount > 0) sumDelta / totalPixelCount else 0.0
        val pctWithin3 = if (totalPixelCount > 0) (countWithin3.toDouble() / totalPixelCount) * 100.0 else 0.0

        val screenCount = measuredColors.size + extraWhiteScreens

        return StrategyEvaluationResult(
            strategyType = SeparationStrategyType.FLAT_SPOT_SOLID,
            name = if (extraWhiteScreens > 0) "Flat Spot Screens (+ Whites)" else "Flat Spot Screens (Solid)",
            screenCount = screenCount,
            inkHexes = inkHexes,
            inkNames = inkNames,
            meanDeltaE00 = meanDeltaE,
            percentile95DeltaE00 = p95,
            percentWithinDeltaE3 = pctWithin3,
            darkGarmentWarning = whiteNote,
            isDarkGarment = isDarkGarment,
            notes = "Discrete solid screen posterisation. No halftone blending required.",
            proofBitmap = null
        )
    }

    private fun detectTransitions(colors: List<MeasuredColor>): String? {
        if (colors.size < 2) return null
        val whiteColor = colors.find { it.lab.l > 80.0 && hypot(it.lab.a, it.lab.b) < NEUTRAL_CHROMA_LIMIT }
        val chromaticColor = colors.find { hypot(it.lab.a, it.lab.b) >= NEUTRAL_CHROMA_LIMIT }

        return if (whiteColor != null && chromaticColor != null) {
            val chromName = chromaticColor.nearestShade?.name ?: chromaticColor.hex
            val whiteName = whiteColor.nearestShade?.name ?: "White"
            "$chromName as halftone over $whiteName (${min(2, colors.size)} screens)"
        } else null
    }

    private fun quantizePixels6Bit(pixels: IntArray): Map<Int, QuantizedBin> {
        val map = HashMap<Int, QuantizedBin>(4096)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val key = getQuantizedKey6Bit(r, g, b)
            val existing = map[key]
            if (existing != null) {
                existing.count++
            } else {
                val repR = ((r shr 2) shl 2) + 2
                val repG = ((g shr 2) shl 2) + 2
                val repB = ((b shr 2) shl 2) + 2
                map[key] = QuantizedBin(repR, repG, repB, 1)
            }
        }
        return map
    }

    private fun getQuantizedKey6Bit(r: Int, g: Int, b: Int): Int {
        val qR = (r shr 2) and 0x3F // 6 bits = 64 levels
        val qG = (g shr 2) and 0x3F
        val qB = (b shr 2) and 0x3F
        return (qR shl 12) or (qG shl 6) or qB
    }

    private fun createEmptyStrategy(
        type: SeparationStrategyType,
        name: String,
        screenCount: Int,
        isDark: Boolean
    ): StrategyEvaluationResult {
        return StrategyEvaluationResult(
            strategyType = type,
            name = name,
            screenCount = if (isDark) screenCount + 2 else screenCount,
            inkHexes = emptyList(),
            inkNames = emptyList(),
            meanDeltaE00 = 0.0,
            percentile95DeltaE00 = 0.0,
            percentWithinDeltaE3 = 100.0,
            darkGarmentWarning = if (isDark) "Base white underbase and highlight white added." else null,
            isDarkGarment = isDark,
            notes = "No active print pixels."
        )
    }

    // ==========================================
    // PART C: Screens, Proof & Film Positives
    // ==========================================

    /**
     * Generates greyscale film positives (black = ink), reconstructed model proof,
     * and composite separation sheet for the chosen separation strategy.
     */
    fun generatePartC(
        pixels: IntArray,
        width: Int,
        height: Int,
        originalBitmap: Bitmap?,
        partA: PartAResult,
        partB: PartBResult,
        chosenStrategyType: SeparationStrategyType,
        inputs: PrintJobInputs,
        processInks: ProcessInksConfig,
        decisionRules: SeparationDecisionRules = SeparationDecisionRules()
    ): PartCResult {
        val startTime = System.currentTimeMillis()
        val totalPixels = width * height

        val strategy = partB.strategies.find { it.strategyType == chosenStrategyType }
            ?: partB.strategies.firstOrNull()
            ?: createEmptyStrategy(chosenStrategyType, "Unknown", 0, partB.isDarkGarment)

        val isDarkGarment = partB.isDarkGarment
        // Garment colour as measured in Part A, the same one Part B used.
        val garmentLab = measuredGarmentLab(partA, inputs)
        val garmentRgb = ColorConverter.labToRgb(garmentLab.l, garmentLab.a, garmentLab.b)
        val garmentColorInt = (0xFF shl 24) or (garmentRgb.r shl 16) or (garmentRgb.g shl 8) or garmentRgb.b
        val garmentLinear = doubleArrayOf(
            ColorConverter.sRgbToLinear(garmentRgb.r),
            ColorConverter.sRgbToLinear(garmentRgb.g),
            ColorConverter.sRgbToLinear(garmentRgb.b)
        )
        // Spot inks are opaque over a dark or coloured garment. Process inks stay transparent.
        val opaqueModel = partB.opaqueInkModel && chosenStrategyType != SeparationStrategyType.CMYK_PROCESS

        if (totalPixels == 0 || pixels.size != totalPixels) {
            return PartCResult(
                strategyType = chosenStrategyType,
                strategyName = strategy.name,
                originalBitmap = originalBitmap,
                proofBitmap = null,
                channels = emptyList(),
                compositeSheetBitmap = null,
                executionTimeMs = System.currentTimeMillis() - startTime,
                disclaimer = PRELIMINARY_DISCLAIMER
            )
        }

        // Ink is only laid inside the print box. Coverage "over the print box" is measured
        // against the box, not the whole photo.
        val box = partA.printBox
        val boxMinX = (box.minX * width).toInt().coerceIn(0, width - 1)
        val boxMaxX = (box.maxX * width).toInt().coerceIn(boxMinX + 1, width)
        val boxMinY = (box.minY * height).toInt().coerceIn(0, height - 1)
        val boxMaxY = (box.maxY * height).toInt().coerceIn(boxMinY + 1, height)
        val boxPixelCount = (boxMaxX - boxMinX) * (boxMaxY - boxMinY)

        val isPrintMask = PrintAreaAndPaletteEngine.computePrintMask(pixels, width, height, inputs)
        for (i in 0 until totalPixels) {
            if (!isPrintMask[i]) continue
            val x = i % width
            val y = i / width
            if (x < boxMinX || x >= boxMaxX || y < boxMinY || y >= boxMaxY) isPrintMask[i] = false
        }
        val printPixelCount = isPrintMask.count { it }

        // Determine if white print colour acts as base
        val hasWhitePrint = strategy.inkHexes.any {
            val lab = ColorConverter.rgbToLab(ColorConverter.hexToRgb(it))
            lab.l > decisionRules.highlightWhiteThresholdL && hypot(lab.a, lab.b) < decisionRules.highlightWhiteMaxChroma
        }
        val whiteActsAsBase = hasWhitePrint && inputs.whitePrintActsAsBase

        // Setup channel definitions based on chosen strategy
        data class InkSpec(
            val name: String,
            val hex: String,
            val rgb: ColorConverter.Rgb,
            val mesh: String,
            val isBaseWhite: Boolean = false,
            val isHighlightWhite: Boolean = false
        )

        val inkSpecs = mutableListOf<InkSpec>()

        // 1. Base White Underbase (choked)
        if (isDarkGarment && !whiteActsAsBase) {
            inkSpecs.add(
                InkSpec(
                    name = "Base White (Underbase)",
                    hex = "#FFFFFF",
                    rgb = ColorConverter.Rgb(255, 255, 255),
                    mesh = "110 tpi",
                    isBaseWhite = true
                )
            )
        }

        // 2. Color Inks
        when (chosenStrategyType) {
            SeparationStrategyType.CMYK_PROCESS -> {
                inkSpecs.add(InkSpec("Cyan", processInks.cyanHex, ColorConverter.hexToRgb(processInks.cyanHex), "305 tpi"))
                inkSpecs.add(InkSpec("Magenta", processInks.magentaHex, ColorConverter.hexToRgb(processInks.magentaHex), "305 tpi"))
                inkSpecs.add(InkSpec("Yellow", processInks.yellowHex, ColorConverter.hexToRgb(processInks.yellowHex), "305 tpi"))
                inkSpecs.add(InkSpec("Black", processInks.blackHex, ColorConverter.hexToRgb(processInks.blackHex), "305 tpi"))
            }
            SeparationStrategyType.BEST_N_SPOT_HALFTONE,
            SeparationStrategyType.EXTENDED_SPOT -> {
                for (i in strategy.inkHexes.indices) {
                    val hex = strategy.inkHexes[i]
                    val name = if (i < strategy.inkNames.size) strategy.inkNames[i] else hex
                    inkSpecs.add(InkSpec(name, hex, ColorConverter.hexToRgb(hex), "200 tpi"))
                }
            }
            SeparationStrategyType.FLAT_SPOT_SOLID -> {
                for (i in strategy.inkHexes.indices) {
                    val hex = strategy.inkHexes[i]
                    val name = if (i < strategy.inkNames.size) strategy.inkNames[i] else hex
                    inkSpecs.add(InkSpec(name, hex, ColorConverter.hexToRgb(hex), "156 tpi"))
                }
            }
        }

        // 3. Highlight White: only when white is not already one of the print colours
        if (isDarkGarment && !hasWhitePrint) {
            inkSpecs.add(
                InkSpec(
                    name = "Highlight White",
                    hex = "#FFFFFF",
                    rgb = ColorConverter.Rgb(255, 255, 255),
                    mesh = "230 tpi",
                    isHighlightWhite = true
                )
            )
        }

        val numChannels = inkSpecs.size
        val channelPixelArrays = Array(numChannels) { IntArray(totalPixels) }
        val sumCoverages = DoubleArray(numChannels)
        val proofPixels = IntArray(totalPixels)

        // Calculate physical choke for base white in pixels
        val chokeMm = decisionRules.chokeMmDefault
        // Choke formula: pixels = choke_mm / 10 x (print box width in pixels of THIS image / print width in cm).
        // Part A may have run at a different image size, so its pixels-per-cm is not used here.
        val pxPerCm = (boxMaxX - boxMinX).toDouble() / inputs.printWidthCm
        val chokePx = max(1, ((chokeMm / 10.0) * pxPerCm).roundToInt())
        val chokedBaseWhiteMask = if (isDarkGarment && !whiteActsAsBase) {
            PrintAreaAndPaletteEngine.erodeBinaryMask(isPrintMask, width, height, chokePx)
        } else BooleanArray(0)

        val coloredInks = inkSpecs.filter { !it.isBaseWhite && !it.isHighlightWhite }
        val coloredCount = coloredInks.size

        if (chosenStrategyType == SeparationStrategyType.FLAT_SPOT_SOLID) {
            // Flat Spot solid screens
            val spotCentroids = strategy.inkHexes.map { ColorConverter.rgbToLab(ColorConverter.hexToRgb(it)) }

            for (i in 0 until totalPixels) {
                if (!isPrintMask[i]) {
                    proofPixels[i] = garmentColorInt
                    for (ch in 0 until numChannels) {
                        channelPixelArrays[ch][i] = 0xFFFFFFFF.toInt() // 0% ink = white
                    }
                    continue
                }

                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lab = ColorConverter.rgbToLab(r, g, b)

                var bestIndex = 0
                var minD = Double.MAX_VALUE
                for (ci in spotCentroids.indices) {
                    val d = ColorConverter.deltaE00(lab, spotCentroids[ci])
                    if (d < minD) {
                        minD = d
                        bestIndex = ci
                    }
                }

                val chosenInk = coloredInks.getOrNull(bestIndex)
                if (chosenInk != null) {
                    proofPixels[i] = (0xFF shl 24) or (chosenInk.rgb.r shl 16) or (chosenInk.rgb.g shl 8) or chosenInk.rgb.b
                } else {
                    proofPixels[i] = p
                }

                for (ch in 0 until numChannels) {
                    val spec = inkSpecs[ch]
                    val isInkOn = when {
                        spec.isBaseWhite -> chokedBaseWhiteMask.getOrElse(i) { false }
                        spec.isHighlightWhite -> lab.l > decisionRules.highlightWhiteThresholdL && hypot(lab.a, lab.b) < decisionRules.highlightWhiteMaxChroma
                        else -> {
                            val spotIndex = coloredInks.indexOf(spec)
                            spotIndex == bestIndex
                        }
                    }

                    if (isInkOn) {
                        sumCoverages[ch] += 1.0
                        channelPixelArrays[ch][i] = 0xFF000000.toInt() // 100% ink = Black
                    } else {
                        channelPixelArrays[ch][i] = 0xFFFFFFFF.toInt() // 0% ink = White
                    }
                }
            }
        } else {
            // Halftone NNLS mixing strategies
            val quantizedPalette = quantizePixels6Bit(pixels)

            class SolvedBinData(
                val amounts: DoubleArray,
                val proofColor: Int
            )

            val binSolutions = HashMap<Int, SolvedBinData>(quantizedPalette.size)

            if (opaqueModel) {
                // Opaque model in linear RGB, relative to the garment:
                // sum c_j * (ink_j - garment) = pixel - garment
                val inkLinears = Array(coloredCount) { ci ->
                    val rgb = coloredInks[ci].rgb
                    doubleArrayOf(
                        ColorConverter.sRgbToLinear(rgb.r) - garmentLinear[0],
                        ColorConverter.sRgbToLinear(rgb.g) - garmentLinear[1],
                        ColorConverter.sRgbToLinear(rgb.b) - garmentLinear[2]
                    )
                }

                for ((binKey, bin) in quantizedPalette) {
                    val target = doubleArrayOf(
                        ColorConverter.sRgbToLinear(bin.r) - garmentLinear[0],
                        ColorConverter.sRgbToLinear(bin.g) - garmentLinear[1],
                        ColorConverter.sRgbToLinear(bin.b) - garmentLinear[2]
                    )
                    val rawAmounts = if (coloredCount > 0) LawsonHansonNnls.solve(inkLinears, target) else DoubleArray(0)

                    var sumC = 0.0
                    for (j in rawAmounts.indices) {
                        if (rawAmounts[j] > 1.0) rawAmounts[j] = 1.0
                        sumC += rawAmounts[j]
                    }
                    if (sumC > 1.0) {
                        for (j in rawAmounts.indices) rawAmounts[j] /= sumC
                    }

                    var pR = garmentLinear[0]
                    var pG = garmentLinear[1]
                    var pB = garmentLinear[2]
                    for (j in 0 until coloredCount) {
                        pR += rawAmounts[j] * inkLinears[j][0]
                        pG += rawAmounts[j] * inkLinears[j][1]
                        pB += rawAmounts[j] * inkLinears[j][2]
                    }

                    val proofInt = (0xFF shl 24) or
                        (ColorConverter.linearToSRgb(pR.coerceIn(0.0, 1.0)) shl 16) or
                        (ColorConverter.linearToSRgb(pG.coerceIn(0.0, 1.0)) shl 8) or
                        ColorConverter.linearToSRgb(pB.coerceIn(0.0, 1.0))

                    binSolutions[binKey] = SolvedBinData(rawAmounts, proofInt)
                }
            } else {
                // Absorbance model on white/light garments
                val coloredAbsorbances = Array(coloredCount) { hexToAbsorbance(coloredInks[it].hex) }

                for ((binKey, bin) in quantizedPalette) {
                    val targetAbs = rgbToAbsorbance(bin.r, bin.g, bin.b)
                    val rawAmounts = if (coloredCount > 0) LawsonHansonNnls.solve(coloredAbsorbances, targetAbs) else DoubleArray(0)

                    for (j in rawAmounts.indices) {
                        if (rawAmounts[j] > 1.0) rawAmounts[j] = 1.0
                    }

                    val proofAbs = DoubleArray(3)
                    for (j in 0 until coloredCount) {
                        val cJ = rawAmounts[j]
                        if (cJ > 0.0) {
                            proofAbs[0] += cJ * coloredAbsorbances[j][0]
                            proofAbs[1] += cJ * coloredAbsorbances[j][1]
                            proofAbs[2] += cJ * coloredAbsorbances[j][2]
                        }
                    }

                    val prRgb = absorbanceToRgb(proofAbs)
                    val proofInt = (0xFF shl 24) or (prRgb.r shl 16) or (prRgb.g shl 8) or prRgb.b
                    binSolutions[binKey] = SolvedBinData(rawAmounts, proofInt)
                }
            }

            for (i in 0 until totalPixels) {
                if (!isPrintMask[i]) {
                    proofPixels[i] = garmentColorInt
                    for (ch in 0 until numChannels) {
                        channelPixelArrays[ch][i] = 0xFFFFFFFF.toInt() // 0% ink = white
                    }
                    continue
                }

                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val key = getQuantizedKey6Bit(r, g, b)

                val sol = binSolutions[key]
                val amounts = sol?.amounts ?: DoubleArray(coloredCount)
                proofPixels[i] = sol?.proofColor ?: p

                val lab = ColorConverter.rgbToLab(r, g, b)

                for (ch in 0 until numChannels) {
                    val spec = inkSpecs[ch]
                    val amount = when {
                        spec.isBaseWhite -> {
                            if (chokedBaseWhiteMask.getOrElse(i) { false }) 1.0 else 0.0
                        }
                        spec.isHighlightWhite -> {
                            if (lab.l > decisionRules.highlightWhiteThresholdL && hypot(lab.a, lab.b) < decisionRules.highlightWhiteMaxChroma) {
                                ((lab.l - decisionRules.highlightWhiteThresholdL) / 20.0).coerceIn(0.0, 1.0)
                            } else 0.0
                        }
                        else -> {
                            val colorIdx = coloredInks.indexOf(spec)
                            if (colorIdx in amounts.indices) amounts[colorIdx] else 0.0
                        }
                    }

                    sumCoverages[ch] += amount
                    val grey = ((1.0 - amount) * 255.0).roundToInt().coerceIn(0, 255)
                    channelPixelArrays[ch][i] = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
                }
            }
        }

        // Construct bitmaps safely
        val proofBitmap = try {
            Bitmap.createBitmap(proofPixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            null
        }

        val channels = inkSpecs.mapIndexed { index, spec ->
            val avgCov = if (printPixelCount > 0) (sumCoverages[index] / printPixelCount) * 100.0 else 0.0
            val boxCov = if (boxPixelCount > 0) (sumCoverages[index] / boxPixelCount) * 100.0 else 0.0
            val filmBitmap = try {
                Bitmap.createBitmap(channelPixelArrays[index], width, height, Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) {
                null
            }

            FilmPositiveChannel(
                channelIndex = index + 1,
                inkName = spec.name,
                inkHex = spec.hex,
                inkRgb = spec.rgb,
                averageCoveragePercent = avgCov,
                boxCoveragePercent = boxCov,
                filmPositiveBitmap = filmBitmap,
                rawGreyscalePixels = channelPixelArrays[index],
                recommendedMesh = spec.mesh
            )
        }

        val compositeSheet = createCompositeSeparationSheet(
            originalBitmap = originalBitmap,
            proofBitmap = proofBitmap,
            channels = channels,
            strategyName = strategy.name,
            inputs = inputs,
            partA = partA
        )

        return PartCResult(
            strategyType = chosenStrategyType,
            strategyName = strategy.name,
            originalBitmap = originalBitmap,
            proofBitmap = proofBitmap,
            proofPixels = proofPixels,
            channels = channels,
            compositeSheetBitmap = compositeSheet,
            executionTimeMs = System.currentTimeMillis() - startTime,
            disclaimer = PRELIMINARY_DISCLAIMER
        )
    }

    /**
     * Creates a high-resolution composite separation sheet with original, proof,
     * and all film positive channel screens arranged side-by-side with swatch metadata.
     */
    fun createCompositeSeparationSheet(
        originalBitmap: Bitmap?,
        proofBitmap: Bitmap?,
        channels: List<FilmPositiveChannel>,
        strategyName: String,
        inputs: PrintJobInputs,
        partA: PartAResult
    ): Bitmap? {
        return try {
            val sheetWidth = 1200
            val numChannels = channels.size
            val numCols = 3
            val numRows = (numChannels + numCols - 1) / numCols
            val channelCardHeight = 360
            val gridHeight = numRows * channelCardHeight

            val sheetHeight = 120 + 440 + gridHeight + 120

            val bitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(0xFFF8F9FA.toInt())

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1E293B.toInt()
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 20f
            }

            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF334155.toInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 15f
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCBD5E1.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }

            // Header
            canvas.drawText("NIRA ARTWORK STUDIO — SEPARATION SHEET", 40f, 60f, titlePaint)
            val headerSub = "Strategy: $strategyName  |  Print Size: ${inputs.printWidthCm} cm × ${String.format("%.1f", partA.printHeightCm)} cm  |  Screens: ${channels.size}  |  Garment: ${inputs.userGarmentHex ?: inputs.garmentColorHex}"
            canvas.drawText(headerSub, 40f, 95f, subtitlePaint)

            // Side-by-side Top Section: Original vs Model Proof
            val topCardY = 130f
            val previewCardWidth = 540f
            val previewCardHeight = 400f

            // 1. Original Card
            canvas.drawRoundRect(RectF(40f, topCardY, 40f + previewCardWidth, topCardY + previewCardHeight), 16f, 16f, cardBgPaint)
            canvas.drawRoundRect(RectF(40f, topCardY, 40f + previewCardWidth, topCardY + previewCardHeight), 16f, 16f, borderPaint)
            canvas.drawText("Original Artwork", 60f, topCardY + 40f, bodyPaint)

            if (originalBitmap != null) {
                val destRect = RectF(60f, topCardY + 60f, 40f + previewCardWidth - 20f, topCardY + previewCardHeight - 20f)
                canvas.drawBitmap(originalBitmap, null, destRect, null)
            }

            // 2. Proof Card
            val proofLeft = 620f
            canvas.drawRoundRect(RectF(proofLeft, topCardY, proofLeft + previewCardWidth, topCardY + previewCardHeight), 16f, 16f, cardBgPaint)
            canvas.drawRoundRect(RectF(proofLeft, topCardY, proofLeft + previewCardWidth, topCardY + previewCardHeight), 16f, 16f, borderPaint)
            canvas.drawText("Reconstructed Proof", proofLeft + 20f, topCardY + 40f, bodyPaint)

            if (proofBitmap != null) {
                val destRect = RectF(proofLeft + 20f, topCardY + 60f, proofLeft + previewCardWidth - 20f, topCardY + previewCardHeight - 20f)
                canvas.drawBitmap(proofBitmap, null, destRect, null)
            }

            // Grid Section: Film Positives
            val gridStartY = topCardY + previewCardHeight + 30f
            val cellWidth = 353f
            val cellHeight = 330f
            val cellSpacing = 30f

            val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            for (i in channels.indices) {
                val ch = channels[i]
                val col = i % numCols
                val row = i / numCols
                val cellLeft = 40f + col * (cellWidth + cellSpacing)
                val cellTop = gridStartY + row * (cellHeight + cellSpacing)

                val cellRect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
                canvas.drawRoundRect(cellRect, 12f, 12f, cardBgPaint)
                canvas.drawRoundRect(cellRect, 12f, 12f, borderPaint)

                // Draw film positive image
                val filmBitmap = ch.filmPositiveBitmap
                if (filmBitmap != null) {
                    val imgRect = RectF(cellLeft + 16f, cellTop + 16f, cellLeft + cellWidth - 16f, cellTop + 200f)
                    canvas.drawBitmap(filmBitmap, null, imgRect, null)
                    canvas.drawRoundRect(imgRect, 4f, 4f, borderPaint)
                }

                // Swatch + Metadata
                val metaTop = cellTop + 225f
                swatchPaint.color = (0xFF shl 24) or (ch.inkRgb.r shl 16) or (ch.inkRgb.g shl 8) or ch.inkRgb.b
                val swatchRect = RectF(cellLeft + 16f, metaTop, cellLeft + 44f, metaTop + 28f)
                canvas.drawRoundRect(swatchRect, 6f, 6f, swatchPaint)
                canvas.drawRoundRect(swatchRect, 6f, 6f, borderPaint)

                canvas.drawText("Screen ${ch.channelIndex}: ${ch.inkName}", cellLeft + 54f, metaTop + 22f, bodyPaint)
                canvas.drawText("HEX: ${ch.inkHex}  |  Mesh: ${ch.recommendedMesh}", cellLeft + 16f, metaTop + 55f, smallPaint)
                canvas.drawText("Active Coverage: ${ch.formattedCoverage}  (Box: ${ch.formattedBoxCoverage})", cellLeft + 16f, metaTop + 80f, smallPaint)
            }

            // Footer Section
            val footerY = sheetHeight - 50f
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText(PRELIMINARY_DISCLAIMER, 40f, footerY, footerPaint)

            bitmap
        } catch (e: Throwable) {
            null
        }
    }

    // ==========================================
    // PART D: Ink Consumption Calculations
    // ==========================================

    /**
     * Calculates industrial ink consumption per channel and job totals based on:
     * ink_g = coverageFraction x boxAreaM2 x deposit_g_m2 x strokes x (1 + wastageFraction)
     */
    fun calculatePartD(
        partA: PartAResult,
        partC: PartCResult,
        inputs: PrintJobInputs,
        depositRules: Map<Int, MeshDepositRule> = emptyMap()
    ): PartDResult {
        val boxAreaM2 = partA.totalBoxAreaCm2 / 10000.0 // cm² to m²
        val strokes = inputs.strokes
        val wastageFraction = inputs.wastagePercent / 100.0

        val channelConsumptions = mutableListOf<InkChannelConsumption>()

        var totalGarment = 0.0
        var totalGarmentMin = 0.0
        var totalGarmentMax = 0.0

        for (ch in partC.channels) {
            // Extract mesh count integer (e.g. "156 tpi" -> 156)
            val meshTpi = ch.recommendedMesh.filter { it.isDigit() }.toIntOrNull() ?: inputs.meshTpi
            val depositRule = depositRules[meshTpi] ?: MeshDepositRule(
                tpi = meshTpi,
                depositGPerM2Default = 36.0,
                depositGPerM2Min = 30.0,
                depositGPerM2Max = 48.0,
                confidence = "interpolated"
            )

            val coverageFraction = ch.boxCoveragePercent / 100.0 // Coverage over print box

            val inkGrams = coverageFraction * boxAreaM2 * depositRule.depositGPerM2Default * strokes * (1.0 + wastageFraction)
            val inkGramsMin = coverageFraction * boxAreaM2 * depositRule.depositGPerM2Min * strokes * (1.0 + wastageFraction)
            val inkGramsMax = coverageFraction * boxAreaM2 * depositRule.depositGPerM2Max * strokes * (1.0 + wastageFraction)

            val inkGrams100 = inkGrams * 100.0
            val inkGrams100Min = inkGramsMin * 100.0
            val inkGrams100Max = inkGramsMax * 100.0

            totalGarment += inkGrams
            totalGarmentMin += inkGramsMin
            totalGarmentMax += inkGramsMax

            channelConsumptions.add(
                InkChannelConsumption(
                    channelIndex = ch.channelIndex,
                    inkName = ch.inkName,
                    inkHex = ch.inkHex,
                    meshTpi = meshTpi,
                    boxCoveragePercent = ch.boxCoveragePercent,
                    depositGPerM2Default = depositRule.depositGPerM2Default,
                    depositGPerM2Min = depositRule.depositGPerM2Min,
                    depositGPerM2Max = depositRule.depositGPerM2Max,
                    depositConfidence = depositRule.confidence,
                    strokes = strokes,
                    wastagePercent = inputs.wastagePercent,
                    inkGramsPerGarment = inkGrams,
                    inkGramsPerGarmentMin = inkGramsMin,
                    inkGramsPerGarmentMax = inkGramsMax,
                    inkGramsPer100Pieces = inkGrams100,
                    inkGramsPer100PiecesMin = inkGrams100Min,
                    inkGramsPer100PiecesMax = inkGrams100Max
                )
            )
        }

        return PartDResult(
            channels = channelConsumptions,
            totalGramsPerGarment = totalGarment,
            totalGramsPerGarmentMin = totalGarmentMin,
            totalGramsPerGarmentMax = totalGarmentMax,
            totalGramsPer100Pieces = totalGarment * 100.0,
            totalGramsPer100PiecesMin = totalGarmentMin * 100.0,
            totalGramsPer100PiecesMax = totalGarmentMax * 100.0,
            printBoxAreaM2 = boxAreaM2,
            orderPieces = inputs.orderPieces,
            note = "Screen residue and set-up loss are not included."
        )
    }
}
