package com.mos.nira.core.image

import android.graphics.Bitmap
import com.mos.nira.features.artwork.data.GarmentMaskRules
import com.mos.nira.features.artwork.data.SeparationDecisionRules
import com.mos.nira.features.artwork.domain.ErrorCurvePoint
import com.mos.nira.features.artwork.domain.GarmentProfile
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.ShadeEntry
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic engineering calculation engine for NIRA Print Report — Part A:
 * - Real Garment Background Measurement (Border Median Lab + Spread)
 * - Uneven Lighting Flattening & Knit Fabric Texture Smoothing
 * - Automatic Connected Component Print Box Detection & User Boundary Dragging
 * - Resolution (pixels/cm) & Fine Line Detail Check
 * - 16-Cluster K-Means Palette Extraction with Garment Exclusion & CIEDE2000 Merging
 * - Shade Library Nearest Match Assignment
 * - Flat-Separation Posterisation Error Curve (K = 4, 6, 8, 10, 12, 16)
 */
object PrintAreaAndPaletteEngine {

    const val DEFAULT_MERGE_THRESHOLD_DELTA_E00 = 3.2
    const val MIN_COVERAGE_PERCENT = 1.5 // 1.5%
    const val MAX_KMEANS_ITERATIONS = 20
    const val FIXED_SEED = 42L

    /**
     * Executes complete Part A calculation for a given pixel buffer.
     */
    fun computePartA(
        pixels: IntArray,
        width: Int,
        height: Int,
        inputs: PrintJobInputs,
        shadeCandidates: List<ShadeEntry> = emptyList(),
        seed: Long = FIXED_SEED,
        generateMaskBitmap: Boolean = true,
        decisionRules: SeparationDecisionRules = SeparationDecisionRules(),
        maskRules: GarmentMaskRules = GarmentMaskRules()
    ): PartAResult {
        if (width <= 0 || height <= 0 || pixels.isEmpty() || pixels.size != width * height) {
            return PartAResult(
                printWidthCm = inputs.printWidthCm,
                printHeightCm = 0.0,
                totalBoxAreaCm2 = 0.0,
                effectivePrintAreaCm2 = 0.0,
                printPixelCount = 0,
                totalPixelCount = 0,
                printMaskCoveragePercent = 0.0,
                measuredPalette = emptyList(),
                errorCurve = listOf(4, 6, 8, 10, 12, 16).map { ErrorCurvePoint(it, 0.0) },
                maskBitmap = null,
                garmentProfile = null,
                printBox = NormalizedRect(0f, 0f, 1f, 1f),
                pixelsPerCm = 0.0,
                lowResolutionWarning = null
            )
        }

        val totalPixels = width * height

        // Step 1: Measure Garment Background (Median Lab + Spread)
        val garmentProfile = estimateGarmentProfile(pixels, width, height, inputs.userGarmentHex, inputs.garmentColorHex, maskRules)
        val garmentLab = garmentProfile.medianLab
        val isWhiteGarment = garmentLab.l > 80.0 && hypot(garmentLab.a, garmentLab.b) < 9.0
        val effectiveGarmentThreshold = max(inputs.garmentThresholdDeltaE00, garmentProfile.adaptiveThresholdDeltaE00)

        // Step 2: Illumination Flattening on fabric & knit smoothing
        val illuminationCorrectedPixels = if (!isWhiteGarment) {
            flattenUnevenLighting(pixels, width, height, garmentLab, garmentProfile.spreadDeltaE00)
        } else pixels

        val smoothedPixels = if (!isWhiteGarment) {
            smoothKnitTexture(illuminationCorrectedPixels, width, height)
        } else illuminationCorrectedPixels

        // Step 3: Raw Print Masking
        val rawPrintMask = BooleanArray(totalPixels)
        val labCache = HashMap<Int, Pair<ColorConverter.Lab, Boolean>>(8192)

        for (y in 0 until height) {
            val normY = (y + 0.5f) / height
            val rowOffset = y * width
            for (x in 0 until width) {
                val index = rowOffset + x
                val normX = (x + 0.5f) / width

                // User erased rectangle exclusion
                val isErased = inputs.erasedRectangles.any { it.contains(normX, normY) }
                if (isErased) {
                    rawPrintMask[index] = false
                    continue
                }

                val p = smoothedPixels[index]
                val alpha = (p shr 24) and 0xFF
                if (alpha < 128) {
                    rawPrintMask[index] = false
                    continue
                }

                val cached = labCache[p]
                val (lab, isGarment) = if (cached != null) {
                    cached
                } else {
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val computedLab = ColorConverter.rgbToLab(r, g, b)
                    val garmentFlag = if (isWhiteGarment) {
                        computedLab.l > 80.0 && hypot(computedLab.a, computedLab.b) < 9.0
                    } else {
                        ColorConverter.deltaE00(computedLab, garmentLab) <= effectiveGarmentThreshold
                    }
                    val entry = Pair(computedLab, garmentFlag)
                    labCache[p] = entry
                    entry
                }

                rawPrintMask[index] = !isGarment
            }
        }

        // Step 4: Speck Removal & Print Box Isolation
        val cleanedPrintMask = removeSpecks(rawPrintMask, width, height, maskRules.speckMinAreaFraction)
        val detectedBox = detectPrintBox(cleanedPrintMask, width, height)
        val activePrintBox = inputs.printBox ?: detectedBox

        // Step 5: Dimensions, Resolution & Bounding Box Scaling
        val boxMinXPx = (activePrintBox.minX * width).toInt().coerceIn(0, width - 1)
        val boxMaxXPx = (activePrintBox.maxX * width).toInt().coerceIn(boxMinXPx + 1, width)
        val boxMinYPx = (activePrintBox.minY * height).toInt().coerceIn(0, height - 1)
        val boxMaxYPx = (activePrintBox.maxY * height).toInt().coerceIn(boxMinYPx + 1, height)

        val boxWidthPx = max(1, boxMaxXPx - boxMinXPx)
        val boxHeightPx = max(1, boxMaxYPx - boxMinYPx)
        val totalBoxPixels = boxWidthPx * boxHeightPx

        val printWidthCm = inputs.printWidthCm
        val printHeightCm = printWidthCm * (boxHeightPx.toDouble() / boxWidthPx.toDouble())
        val totalBoxAreaCm2 = printWidthCm * printHeightCm

        val pixelsPerCm = boxWidthPx.toDouble() / printWidthCm
        // Limit comes from print_rules.json (resolution.px_per_cm_min_for_fine_detail).
        val lowResolutionWarning = if (pixelsPerCm < decisionRules.minResolutionPxPerCm) {
            String.format(
                java.util.Locale.US,
                "Resolution on the print box is %.1f px/cm (below %.0f px/cm). Fine lines and small text cannot be trusted. Retake closer so the print fills the frame, or supply the original artwork.",
                pixelsPerCm,
                decisionRules.minResolutionPxPerCm
            )
        } else null

        // Step 6: Collect Active Print Pixels INSIDE the Print Box
        val maskedRgbList = ArrayList<Int>(totalBoxPixels)
        val maskedLabList = ArrayList<ColorConverter.Lab>(totalBoxPixels)
        val finalPrintMask = BooleanArray(totalPixels)

        for (y in boxMinYPx until boxMaxYPx) {
            val rowOffset = y * width
            for (x in boxMinXPx until boxMaxXPx) {
                val index = rowOffset + x
                if (cleanedPrintMask[index]) {
                    finalPrintMask[index] = true
                    val p = smoothedPixels[index]
                    maskedRgbList.add(p)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    maskedLabList.add(ColorConverter.rgbToLab(r, g, b))
                }
            }
        }

        val printPixelCount = maskedRgbList.size
        val printMaskCoveragePercent = if (totalBoxPixels > 0) {
            (printPixelCount.toDouble() / totalBoxPixels.toDouble()) * 100.0
        } else 0.0

        val effectivePrintAreaCm2 = totalBoxAreaCm2 * (printPixelCount.toDouble() / totalBoxPixels.toDouble())

        // If no print pixels found
        if (printPixelCount == 0) {
            val maskBitmap = if (generateMaskBitmap) {
                createMaskPreviewBitmap(pixels, finalPrintMask, width, height, inputs.erasedRectangles, activePrintBox)
            } else null

            return PartAResult(
                printWidthCm = printWidthCm,
                printHeightCm = printHeightCm,
                totalBoxAreaCm2 = totalBoxAreaCm2,
                effectivePrintAreaCm2 = 0.0,
                printPixelCount = 0,
                totalPixelCount = totalBoxPixels,
                printMaskCoveragePercent = 0.0,
                measuredPalette = emptyList(),
                errorCurve = listOf(4, 6, 8, 10, 12, 16).map { ErrorCurvePoint(it, 0.0) },
                maskBitmap = maskBitmap,
                garmentProfile = garmentProfile,
                printBox = activePrintBox,
                pixelsPerCm = pixelsPerCm,
                lowResolutionWarning = lowResolutionWarning
            )
        }

        // Subsample for fast clustering if pixel count > 10,000
        val trainingIndices: List<Int> = if (printPixelCount > 10000) {
            val step = printPixelCount / 10000
            (0 until printPixelCount step step).take(10000)
        } else {
            (0 until printPixelCount).toList()
        }

        val trainingLabList = trainingIndices.map { maskedLabList[it] }

        // Step 7: Overcluster in Lab (K = 16, fixed seed)
        val kInitial = min(16, trainingLabList.size)
        val clusterCenters = runKMeans(trainingLabList, kInitial, seed)

        // Assign all masked pixels to these initial clusters
        val initialAssignments = assignPixelsToCenters(maskedRgbList, maskedLabList, clusterCenters)

        // Build cluster summaries
        class ClusterBucket(
            var centerLab: ColorConverter.Lab,
            var count: Int = 0,
            var sumR: Long = 0L,
            var sumG: Long = 0L,
            var sumB: Long = 0L
        ) {
            /** Set when this bucket was chosen as an ink: its colour stays the ink colour. */
            var inkRgb: IntArray? = null
            val r: Int get() = if (count > 0) (sumR / count).toInt().coerceIn(0, 255) else 0
            val g: Int get() = if (count > 0) (sumG / count).toInt().coerceIn(0, 255) else 0
            val b: Int get() = if (count > 0) (sumB / count).toInt().coerceIn(0, 255) else 0
        }

        val buckets = ArrayList<ClusterBucket>()
        for (c in 0 until kInitial) {
            buckets.add(ClusterBucket(clusterCenters[c]))
        }

        for (i in 0 until printPixelCount) {
            val c = initialAssignments[i]
            val p = maskedRgbList[i]
            val b = buckets[c]
            b.count++
            b.sumR += (p shr 16) and 0xFF
            b.sumG += (p shr 8) and 0xFF
            b.sumB += p and 0xFF
        }

        // Filter out empty buckets AND clusters close to garment colour
        var activeBuckets = buckets.filter { b ->
            if (b.count <= 0) false
            else {
                val dToGarment = ColorConverter.deltaE00(b.centerLab, garmentLab)
                dToGarment > 3.8 // Never create print ink in the garment colour!
            }
        }.toMutableList()

        if (activeBuckets.isEmpty() && buckets.any { it.count > 0 }) {
            activeBuckets = buckets.filter { it.count > 0 }.take(1).toMutableList()
        }

        // Step 8: Merge clusters under CIEDE2000 threshold (3.2 ΔE00)
        var merged = true
        while (merged && activeBuckets.size > 1) {
            merged = false
            var bestI = -1
            var bestJ = -1
            var minDistance = Double.MAX_VALUE

            for (i in 0 until activeBuckets.size) {
                for (j in i + 1 until activeBuckets.size) {
                    val dE = ColorConverter.deltaE00(activeBuckets[i].centerLab, activeBuckets[j].centerLab)
                    if (dE < minDistance) {
                        minDistance = dE
                        bestI = i
                        bestJ = j
                    }
                }
            }

            if (minDistance < DEFAULT_MERGE_THRESHOLD_DELTA_E00 && bestI != -1 && bestJ != -1) {
                val b1 = activeBuckets[bestI]
                val b2 = activeBuckets[bestJ]
                val totalCount = b1.count + b2.count

                val mergedLab = ColorConverter.Lab(
                    l = (b1.centerLab.l * b1.count + b2.centerLab.l * b2.count) / totalCount,
                    a = (b1.centerLab.a * b1.count + b2.centerLab.a * b2.count) / totalCount,
                    b = (b1.centerLab.b * b1.count + b2.centerLab.b * b2.count) / totalCount
                )

                val mergedBucket = ClusterBucket(
                    centerLab = mergedLab,
                    count = totalCount,
                    sumR = b1.sumR + b2.sumR,
                    sumG = b1.sumG + b2.sumG,
                    sumB = b1.sumB + b2.sumB
                )

                activeBuckets.removeAt(bestJ)
                activeBuckets.removeAt(bestI)
                activeBuckets.add(mergedBucket)
                merged = true
            }
        }

        // Step 9: Drop clusters under 1.5% coverage
        val thresholdCount = (printPixelCount * (MIN_COVERAGE_PERCENT / 100.0)).toInt()
        val prunedBuckets = activeBuckets.filter { it.count >= thresholdCount }.toMutableList()
        var finalBuckets = if (prunedBuckets.isNotEmpty()) prunedBuckets else activeBuckets.take(1).toMutableList()

        // Step 9b: On a non-white garment, a colour that is a halftone of one ink over the garment,
        // or a smooth transition between two inks, is not a separate ink. Keep the fewest inks
        // that reproduce every colour within the quality tolerance from print_rules.json.
        if (!isWhiteGarment && finalBuckets.size > 1) {
            val garmentRgb = ColorConverter.labToRgb(garmentLab.l, garmentLab.a, garmentLab.b)
            val keep = selectInksByHalftoneReduction(
                colours = finalBuckets.map { intArrayOf(it.r, it.g, it.b) },
                counts = finalBuckets.map { it.count },
                garment = intArrayOf(garmentRgb.r, garmentRgb.g, garmentRgb.b),
                toleranceMean = decisionRules.qualityToleranceDeltaE00,
                toleranceP95 = decisionRules.qualityToleranceP95DeltaE00
            )
            if (keep.size < finalBuckets.size) {
                finalBuckets = keep.map { index ->
                    finalBuckets[index].also { it.inkRgb = intArrayOf(it.r, it.g, it.b) }
                }.toMutableList()
            }
        }

        // Reassign all masked pixels to final cluster centroids for precise counts & coverage
        val finalCenters = finalBuckets.map { it.centerLab }
        val finalAssignments = assignPixelsToCenters(maskedRgbList, maskedLabList, finalCenters)

        finalBuckets.forEach {
            it.count = 0
            it.sumR = 0L
            it.sumG = 0L
            it.sumB = 0L
        }

        for (i in 0 until printPixelCount) {
            val c = finalAssignments[i]
            val p = maskedRgbList[i]
            val b = finalBuckets[c]
            b.count++
            b.sumR += (p shr 16) and 0xFF
            b.sumG += (p shr 8) and 0xFF
            b.sumB += p and 0xFF
        }

        // Build MeasuredColor palette
        val measuredPalette = finalBuckets
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .mapIndexed { index, b ->
                val inkR = b.inkRgb?.get(0) ?: b.r
                val inkG = b.inkRgb?.get(1) ?: b.g
                val inkB = b.inkRgb?.get(2) ?: b.b
                val hex = ColorConverter.rgbToHex(inkR, inkG, inkB)
                val rgb = ColorConverter.Rgb(inkR, inkG, inkB)
                val lab = ColorConverter.rgbToLab(inkR, inkG, inkB)
                val covPercent = (b.count.toDouble() / printPixelCount.toDouble()) * 100.0
                val totalBoxCovPercent = (b.count.toDouble() / totalBoxPixels.toDouble()) * 100.0
                val area = totalBoxAreaCm2 * (b.count.toDouble() / totalBoxPixels.toDouble())

                // Match against shade candidates
                val nearestShadeMatch = findNearestShade(lab, shadeCandidates)

                MeasuredColor(
                    id = index + 1,
                    hex = hex,
                    rgb = rgb,
                    lab = lab,
                    pixelCount = b.count,
                    coveragePercent = covPercent,
                    totalBoxCoveragePercent = totalBoxCovPercent,
                    areaCm2 = area,
                    nearestShade = nearestShadeMatch?.first,
                    nearestShadeDeltaE00 = nearestShadeMatch?.second,
                    matchGrade = nearestShadeMatch?.second?.let { ColorConverter.matchGrade(it) }
                )
            }

        // Step 10: Flat-separation posterisation error curve for K = 4, 6, 8, 10, 12, 16
        val targetKs = listOf(4, 6, 8, 10, 12, 16)
        val errorCurve = targetKs.map { k ->
            val effectiveK = min(k, trainingLabList.size)
            if (effectiveK <= 0) {
                ErrorCurvePoint(k, 0.0)
            } else {
                val kCenters = runKMeans(trainingLabList, effectiveK, seed)
                val evalList = if (maskedLabList.size > 5000) trainingLabList else maskedLabList
                var totalDeltaE = 0.0
                for (pixelLab in evalList) {
                    var minDist = Double.MAX_VALUE
                    for (center in kCenters) {
                        val d = ColorConverter.deltaE00(pixelLab, center)
                        if (d < minDist) {
                            minDist = d
                        }
                    }
                    totalDeltaE += minDist
                }
                val meanDeltaE = totalDeltaE / evalList.size.toDouble()
                ErrorCurvePoint(k = k, meanDeltaE00 = meanDeltaE)
            }
        }

        // Step 11: Render Mask Bitmap with print box overlay
        val maskBitmap = if (generateMaskBitmap) {
            createMaskPreviewBitmap(pixels, finalPrintMask, width, height, inputs.erasedRectangles, activePrintBox)
        } else null

        return PartAResult(
            printWidthCm = printWidthCm,
            printHeightCm = printHeightCm,
            totalBoxAreaCm2 = totalBoxAreaCm2,
            effectivePrintAreaCm2 = effectivePrintAreaCm2,
            printPixelCount = printPixelCount,
            totalPixelCount = totalBoxPixels,
            printMaskCoveragePercent = printMaskCoveragePercent,
            measuredPalette = measuredPalette,
            errorCurve = errorCurve,
            maskBitmap = maskBitmap,
            garmentProfile = garmentProfile,
            printBox = activePrintBox,
            pixelsPerCm = pixelsPerCm,
            lowResolutionWarning = lowResolutionWarning
        )
    }

    /**
     * Estimates garment color by sampling perimeter/border pixels (median Lab + spread).
     * Only a "tap the fabric" colour (userGarmentHex) is trusted as the exact fabric colour.
     * A preset (presetHex) is only an initial guess: the fabric is still measured from the border,
     * because a photo never shows fabric as one pure colour (print_rules.json, garment_mask).
     */
    fun estimateGarmentProfile(
        pixels: IntArray,
        width: Int,
        height: Int,
        userGarmentHex: String?,
        presetHex: String? = null,
        maskRules: GarmentMaskRules = GarmentMaskRules()
    ): GarmentProfile {
        val preferredHex = userGarmentHex ?: presetHex
        if (userGarmentHex != null && userGarmentHex != "#FFFFFF") {
            val rgb = ColorConverter.hexToRgb(userGarmentHex)
            val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
            return GarmentProfile(
                estimatedHex = userGarmentHex,
                medianLab = lab,
                spreadDeltaE00 = 2.0,
                adaptiveThresholdDeltaE00 = 5.0,
                isUserOverridden = true
            )
        }

        // Border size from print_rules.json (garment_mask.border_fraction).
        val margin = max(1, (maskRules.borderFraction * min(width, height)).toInt())
        val borderLabs = ArrayList<ColorConverter.Lab>()

        for (y in 0 until height) {
            val rowOffset = y * width
            val isYBorder = (y < margin || y >= height - margin)
            for (x in 0 until width) {
                if (isYBorder || x < margin || x >= width - margin) {
                    val p = pixels[rowOffset + x]
                    val alpha = (p shr 24) and 0xFF
                    if (alpha >= 128) {
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        borderLabs.add(ColorConverter.rgbToLab(r, g, b))
                    }
                }
            }
        }

        if (borderLabs.isEmpty()) {
            val defLab = ColorConverter.rgbToLab(255, 255, 255)
            return GarmentProfile("#FFFFFF", defLab, 2.0, 5.0, false)
        }

        // Compute median L, a, b
        val medL = borderLabs.map { it.l }.sorted()[borderLabs.size / 2]
        val medA = borderLabs.map { it.a }.sorted()[borderLabs.size / 2]
        val medB = borderLabs.map { it.b }.sorted()[borderLabs.size / 2]
        val medLab = ColorConverter.Lab(medL, medA, medB)

        // Lighting changes how light or dark the fabric looks, but not its colour cast.
        // If the border's colour cast (a, b) is far from the chosen garment colour, the artwork
        // extends to the image border and the border is NOT garment fabric: keep the chosen colour.
        // (For a white garment this is the same check as before: border chroma >= 12.)
        if (preferredHex != null) {
            val guessRgb = ColorConverter.hexToRgb(preferredHex)
            val guessLab = ColorConverter.rgbToLab(guessRgb.r, guessRgb.g, guessRgb.b)
            if (hypot(medA - guessLab.a, medB - guessLab.b) >= 12.0) {
                return GarmentProfile(preferredHex, guessLab, 2.0, 5.0, false)
            }
        }

        // Compute spread = median deltaE00 of border pixels to median Lab
        val deltas = borderLabs.map { ColorConverter.deltaE00(it, medLab) }.sorted()
        val spread = deltas[deltas.size / 2].coerceIn(1.0, 5.0)
        // Threshold adapts to the fabric spread (garment_mask.spread_multiplier, min_threshold_de00).
        val adaptiveThreshold = max(maskRules.minThresholdDeltaE00, spread * maskRules.spreadMultiplier)

        val estRgb = ColorConverter.labToRgb(medLab.l, medLab.a, medLab.b)
        val estHex = ColorConverter.rgbToHex(estRgb.r, estRgb.g, estRgb.b)

        return GarmentProfile(
            estimatedHex = estHex,
            medianLab = medLab,
            spreadDeltaE00 = spread,
            adaptiveThresholdDeltaE00 = adaptiveThreshold,
            isUserOverridden = false
        )
    }

    /**
     * Flattens uneven lighting across fabric pixels using a coarse 8x8 lightness field.
     */
    private fun flattenUnevenLighting(
        pixels: IntArray,
        width: Int,
        height: Int,
        garmentLab: ColorConverter.Lab,
        spread: Double
    ): IntArray {
        val gridDim = 8
        val blockW = max(1, width / gridDim)
        val blockH = max(1, height / gridDim)
        val field = Array(gridDim) { DoubleArray(gridDim) { garmentLab.l } }

        for (gy in 0 until gridDim) {
            val startY = gy * blockH
            val endY = min(height, startY + blockH)
            for (gx in 0 until gridDim) {
                val startX = gx * blockW
                val endX = min(width, startX + blockW)

                val blockLs = ArrayList<Double>()
                for (y in startY until endY) {
                    val row = y * width
                    for (x in startX until endX) {
                        val p = pixels[row + x]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        val lab = ColorConverter.rgbToLab(r, g, b)
                        if (ColorConverter.deltaE00(lab, garmentLab) <= spread * 2.5) {
                            blockLs.add(lab.l)
                        }
                    }
                }
                if (blockLs.size >= 8) {
                    field[gy][gx] = blockLs.sorted()[blockLs.size / 2]
                }
            }
        }

        val result = IntArray(pixels.size)
        for (y in 0 until height) {
            val gy = (y.toDouble() / height * gridDim).toInt().coerceIn(0, gridDim - 1)
            val row = y * width
            for (x in 0 until width) {
                val gx = (x.toDouble() / width * gridDim).toInt().coerceIn(0, gridDim - 1)
                // Same lower limit on both sides: a pure black garment (L = 0) must not
                // give ratio 0, which would turn every pixel, including the print, black.
                val localL = max(4.0, field[gy][gx])
                val ratio = max(4.0, garmentLab.l) / localL

                if (abs(ratio - 1.0) < 0.1) {
                    result[row + x] = pixels[row + x]
                } else {
                    val p = pixels[row + x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val lab = ColorConverter.rgbToLab(r, g, b)
                    val newL = (lab.l * ratio).coerceIn(0.0, 100.0)
                    val newRgb = ColorConverter.labToRgb(newL, lab.a, lab.b)
                    result[row + x] = (0xFF shl 24) or (newRgb.r shl 16) or (newRgb.g shl 8) or newRgb.b
                }
            }
        }
        return result
    }

    /**
     * Edge-preserving smoothing filter to remove subtle knit fabric jersey loops without blurring artwork edges.
     */
    private fun smoothKnitTexture(pixels: IntArray, width: Int, height: Int): IntArray {
        if (width < 3 || height < 3) return pixels
        val n = pixels.size

        // Lab once per pixel (cached by colour), instead of nine conversions per pixel.
        val labCache = HashMap<Int, ColorConverter.Lab>(4096)
        val labs = Array(n) { i ->
            val p = pixels[i] and 0xFFFFFF
            labCache.getOrPut(p) {
                ColorConverter.rgbToLab((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            }
        }

        // The colour difference between two neighbours is the same in both directions, so it is
        // calculated once per pair: right, down-left, down, down-right.
        val offsets = intArrayOf(1, width - 1, width, width + 1)
        val dxs = intArrayOf(1, -1, 0, 1)
        val dys = intArrayOf(0, 1, 1, 1)
        val pairDelta = Array(4) { FloatArray(n) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                for (k in 0..3) {
                    val nx = x + dxs[k]
                    val ny = y + dys[k]
                    if (nx < 0 || nx >= width || ny >= height) continue
                    val j = i + offsets[k]
                    pairDelta[k][i] = if (pixels[i] == pixels[j]) 0f
                    else ColorConverter.deltaE00(labs[i], labs[j]).toFloat()
                }
            }
        }

        val result = IntArray(n)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val i = row + x
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    result[i] = pixels[i]
                    continue
                }

                val pCenter = pixels[i]
                var sumR = ((pCenter shr 16) and 0xFF).toDouble()
                var sumG = ((pCenter shr 8) and 0xFF).toDouble()
                var sumB = (pCenter and 0xFF).toDouble()
                var totalW = 1.0

                for (k in 0..3) {
                    // Forward neighbour (pair stored at this pixel) and backward neighbour
                    // (pair stored at the neighbour).
                    for (side in 0..1) {
                        val j = if (side == 0) i + offsets[k] else i - offsets[k]
                        val dE = (if (side == 0) pairDelta[k][i] else pairDelta[k][j]).toDouble()

                        // Bilateral weight: only smooth neighbours with small difference (knit texture)
                        if (dE < 8.0) {
                            val pN = pixels[j]
                            val w = exp(-(dE * dE) / 32.0)
                            sumR += ((pN shr 16) and 0xFF) * w
                            sumG += ((pN shr 8) and 0xFF) * w
                            sumB += (pN and 0xFF) * w
                            totalW += w
                        }
                    }
                }

                val finalR = (sumR / totalW).roundToInt().coerceIn(0, 255)
                val finalG = (sumG / totalW).roundToInt().coerceIn(0, 255)
                val finalB = (sumB / totalW).roundToInt().coerceIn(0, 255)
                result[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }
        return result
    }

    /**
     * Removes isolated single-pixel specks and noise from the binary mask.
     */
    private fun removeSpecks(mask: BooleanArray, width: Int, height: Int, speckMinAreaFraction: Double): BooleanArray {
        if (width <= 2 || height <= 2) return mask
        val clean = BooleanArray(mask.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (mask[idx]) {
                    var neighborCount = 0
                    var totalNeighbors = 0
                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny in 0 until height) {
                            val nRow = ny * width
                            for (dx in -1..1) {
                                val nx = x + dx
                                if (nx in 0 until width) {
                                    if (dx == 0 && dy == 0) continue
                                    totalNeighbors++
                                    if (mask[nRow + nx]) neighborCount++
                                }
                            }
                        }
                    }
                    if (neighborCount >= 2 || (totalNeighbors < 8 && neighborCount >= 1)) {
                        clean[idx] = true
                    }
                }
            }
        }

        // Drop connected regions smaller than garment_mask.speck_min_area_fraction of the image.
        val minArea = kotlin.math.ceil(speckMinAreaFraction * mask.size).toInt()
        if (minArea > 1) {
            val components = KotlinImageOps.connectedComponents(clean, width, height)
            for (i in clean.indices) {
                if (clean[i] && components.areas[components.labels[i]] < minArea) clean[i] = false
            }
        }
        return clean
    }

    /**
     * Chooses the fewest inks that reproduce every measured colour within tolerance, where a
     * colour may be printed as a halftone between two of: the garment and the chosen inks
     * (opaque ink model, linear RGB). Greedy: add the ink that lowers the mean error most.
     * Returns the indices of the chosen colours.
     */
    fun selectInksByHalftoneReduction(
        colours: List<IntArray>,
        counts: List<Int>,
        garment: IntArray,
        toleranceMean: Double,
        toleranceP95: Double
    ): List<Int> {
        val n = colours.size
        if (n <= 1) return colours.indices.toList()

        fun linear(rgb: IntArray) = doubleArrayOf(
            ColorConverter.sRgbToLinear(rgb[0]),
            ColorConverter.sRgbToLinear(rgb[1]),
            ColorConverter.sRgbToLinear(rgb[2])
        )
        fun lab(lin: DoubleArray) = ColorConverter.rgbToLab(
            ColorConverter.linearToSRgb(lin[0].coerceIn(0.0, 1.0)),
            ColorConverter.linearToSRgb(lin[1].coerceIn(0.0, 1.0)),
            ColorConverter.linearToSRgb(lin[2].coerceIn(0.0, 1.0))
        )

        val lin = colours.map { linear(it) }
        val labs = colours.map { ColorConverter.rgbToLab(it[0], it[1], it[2]) }
        val garmentLin = linear(garment)
        val total = counts.sum().coerceAtLeast(1)

        /** Smallest error of colour c printed as a mix of two of the given end points. */
        fun explainError(c: Int, ends: List<DoubleArray>): Double {
            var best = Double.MAX_VALUE
            for (i in ends.indices) {
                for (j in i until ends.size) {
                    val p = ends[i]
                    val q = ends[j]
                    var dot = 0.0
                    var len = 0.0
                    for (k in 0..2) {
                        dot += (lin[c][k] - p[k]) * (q[k] - p[k])
                        len += (q[k] - p[k]) * (q[k] - p[k])
                    }
                    val t = if (len > 0.0) (dot / len).coerceIn(0.0, 1.0) else 0.0
                    val mix = DoubleArray(3) { p[it] + t * (q[it] - p[it]) }
                    val d = ColorConverter.deltaE00(labs[c], lab(mix))
                    if (d < best) best = d
                }
            }
            return best
        }

        fun errors(selected: List<Int>): DoubleArray {
            val ends = ArrayList<DoubleArray>(selected.size + 1)
            ends.add(garmentLin)
            selected.forEach { ends.add(lin[it]) }
            return DoubleArray(n) { if (it in selected) 0.0 else explainError(it, ends) }
        }

        fun weightedMean(err: DoubleArray): Double {
            var sum = 0.0
            for (i in 0 until n) sum += err[i] * counts[i]
            return sum / total
        }

        fun weightedP95(err: DoubleArray): Double {
            val order = (0 until n).sortedBy { err[it] }
            var acc = 0
            for (i in order) {
                acc += counts[i]
                if (acc >= total * 0.95) return err[i]
            }
            return err[order.last()]
        }

        val selected = ArrayList<Int>()
        while (selected.size < n) {
            if (selected.isNotEmpty()) {
                val err = errors(selected)
                if (weightedMean(err) <= toleranceMean && weightedP95(err) <= toleranceP95) break
            }
            var bestIndex = -1
            var bestMean = Double.MAX_VALUE
            for (candidate in 0 until n) {
                if (candidate in selected) continue
                val mean = weightedMean(errors(selected + candidate))
                if (mean < bestMean) {
                    bestMean = mean
                    bestIndex = candidate
                }
            }
            if (bestIndex < 0) break
            selected.add(bestIndex)
        }
        return selected
    }

    /**
     * Detects tight bounding box around significant connected print pixels, with a 2% margin.
     */
    fun detectPrintBox(mask: BooleanArray, width: Int, height: Int): NormalizedRect {
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var count = 0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (mask[row + x]) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (count < 20 || minX > maxX || minY > maxY) {
            return NormalizedRect(0f, 0f, 1f, 1f)
        }

        val marginX = (width * 0.02f).coerceAtLeast(2f)
        val marginY = (height * 0.02f).coerceAtLeast(2f)

        val leftNorm = max(0f, (minX - marginX) / width)
        val rightNorm = min(1f, (maxX + marginX) / width)
        val topNorm = max(0f, (minY - marginY) / height)
        val bottomNorm = min(1f, (maxY + marginY) / height)

        return NormalizedRect(leftNorm, topNorm, rightNorm, bottomNorm)
    }

    /**
     * Performs morphological erosion on binary mask for base white underbase choke.
     */
    fun erodeBinaryMask(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray {
        if (radiusPx <= 0) return mask
        val eroded = BooleanArray(mask.size)

        for (y in radiusPx until height - radiusPx) {
            val row = y * width
            for (x in radiusPx until width - radiusPx) {
                if (!mask[row + x]) continue

                var allIn = true
                for (dy in -radiusPx..radiusPx) {
                    val nRow = (y + dy) * width
                    for (dx in -radiusPx..radiusPx) {
                        if (dx * dx + dy * dy <= radiusPx * radiusPx) {
                            if (!mask[nRow + (x + dx)]) {
                                allIn = false
                                break
                            }
                        }
                    }
                    if (!allIn) break
                }
                eroded[row + x] = allIn
            }
        }
        return eroded
    }

    /**
     * Executes K-Means clustering with K-Means++ initialization and CIEDE2000 distance.
     */
    fun runKMeans(
        points: List<ColorConverter.Lab>,
        k: Int,
        seed: Long
    ): MutableList<ColorConverter.Lab> {
        val n = points.size
        if (n == 0 || k <= 0) return mutableListOf()
        val actualK = min(k, n)

        val random = Random(seed)
        val centers = ArrayList<ColorConverter.Lab>(actualK)

        // K-Means++ Initialization
        centers.add(points[random.nextInt(n)])

        val distances = DoubleArray(n)
        for (step in 1 until actualK) {
            var sumDist = 0.0
            for (i in 0 until n) {
                var minD = Double.MAX_VALUE
                val pt = points[i]
                for (c in centers) {
                    val d = ColorConverter.deltaE00(pt, c)
                    if (d < minD) minD = d
                }
                distances[i] = minD * minD
                sumDist += distances[i]
            }

            val target = random.nextDouble() * sumDist
            var cumulative = 0.0
            var chosen = 0
            for (i in 0 until n) {
                cumulative += distances[i]
                if (cumulative >= target) {
                    chosen = i
                    break
                }
            }
            centers.add(points[chosen])
        }

        // Iteration
        val assignments = IntArray(n)
        var changed = true
        var iterations = 0

        while (changed && iterations < MAX_KMEANS_ITERATIONS) {
            changed = false
            iterations++

            for (i in 0 until n) {
                val pt = points[i]
                var bestCluster = 0
                var minDist = Double.MAX_VALUE
                for (c in 0 until actualK) {
                    val d = ColorConverter.deltaE00(pt, centers[c])
                    if (d < minDist) {
                        minDist = d
                        bestCluster = c
                    }
                }
                if (assignments[i] != bestCluster) {
                    assignments[i] = bestCluster
                    changed = true
                }
            }

            val counts = IntArray(actualK)
            val sumL = DoubleArray(actualK)
            val sumA = DoubleArray(actualK)
            val sumB = DoubleArray(actualK)

            for (i in 0 until n) {
                val c = assignments[i]
                val pt = points[i]
                counts[c]++
                sumL[c] += pt.l
                sumA[c] += pt.a
                sumB[c] += pt.b
            }

            for (c in 0 until actualK) {
                if (counts[c] > 0) {
                    centers[c] = ColorConverter.Lab(
                        sumL[c] / counts[c],
                        sumA[c] / counts[c],
                        sumB[c] / counts[c]
                    )
                }
            }
        }

        return centers
    }

    private fun assignPixelsToCenters(
        pixelsRgb: List<Int>,
        pixelsLab: List<ColorConverter.Lab>,
        centers: List<ColorConverter.Lab>
    ): IntArray {
        val n = pixelsLab.size
        val result = IntArray(n)
        val k = centers.size
        if (k == 0) return result

        val cache = HashMap<Int, Int>(4096)
        for (i in 0 until n) {
            val rgbKey = pixelsRgb[i] and 0x00FFFFFF
            val cached = cache[rgbKey]
            if (cached != null) {
                result[i] = cached
            } else {
                val pt = pixelsLab[i]
                var bestC = 0
                var minDist = Double.MAX_VALUE
                for (c in 0 until k) {
                    val dist = ColorConverter.deltaE00(pt, centers[c])
                    if (dist < minDist) {
                        minDist = dist
                        bestC = c
                    }
                }
                cache[rgbKey] = bestC
                result[i] = bestC
            }
        }
        return result
    }

    private fun findNearestShade(
        targetLab: ColorConverter.Lab,
        candidates: List<ShadeEntry>
    ): Pair<ShadeEntry, Double>? {
        if (candidates.isEmpty()) return null
        var bestShade: ShadeEntry? = null
        var minDE = Double.MAX_VALUE

        for (shade in candidates) {
            val sLab = ColorConverter.Lab(shade.labL, shade.labA, shade.labB)
            val dE = ColorConverter.deltaE00(targetLab, sLab)
            if (dE < minDE) {
                minDE = dE
                bestShade = shade
            }
        }

        return if (bestShade != null) Pair(bestShade, minDE) else null
    }

    /**
     * Computes the boolean print mask for an image given dimensions and job inputs.
     */
    fun computePrintMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        inputs: PrintJobInputs,
        maskRules: GarmentMaskRules = GarmentMaskRules()
    ): BooleanArray {
        val totalPixels = width * height
        val isPrintMask = BooleanArray(totalPixels)
        if (totalPixels == 0 || pixels.size != totalPixels) return isPrintMask

        val profile = estimateGarmentProfile(pixels, width, height, inputs.userGarmentHex, inputs.garmentColorHex, maskRules)
        val garmentLab = profile.medianLab
        val isWhiteGarment = garmentLab.l > 80.0 && hypot(garmentLab.a, garmentLab.b) < 9.0
        val effectiveThreshold = max(inputs.garmentThresholdDeltaE00, profile.adaptiveThresholdDeltaE00)

        val labCache = HashMap<Int, Boolean>(8192)

        for (y in 0 until height) {
            val normY = (y + 0.5f) / height
            val rowOffset = y * width
            for (x in 0 until width) {
                val index = rowOffset + x
                val normX = (x + 0.5f) / width

                val isErased = inputs.erasedRectangles.any { it.contains(normX, normY) }
                if (isErased) {
                    isPrintMask[index] = false
                    continue
                }

                val p = pixels[index]
                val alpha = (p shr 24) and 0xFF
                if (alpha < 128) {
                    isPrintMask[index] = false
                    continue
                }

                val cached = labCache[p]
                val isGarment = if (cached != null) {
                    cached
                } else {
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val computedLab = ColorConverter.rgbToLab(r, g, b)
                    val garmentFlag = if (isWhiteGarment) {
                        computedLab.l > 80.0 && hypot(computedLab.a, computedLab.b) < 9.0
                    } else {
                        ColorConverter.deltaE00(computedLab, garmentLab) <= effectiveThreshold
                    }
                    labCache[p] = garmentFlag
                    garmentFlag
                }

                isPrintMask[index] = !isGarment
            }
        }
        return isPrintMask
    }

    private fun createMaskPreviewBitmap(
        originalPixels: IntArray,
        isPrintMask: BooleanArray,
        width: Int,
        height: Int,
        erasedRectangles: List<NormalizedRect>,
        printBox: NormalizedRect? = null
    ): Bitmap? {
        return try {
            val outputPixels = IntArray(width * height)
            for (y in 0 until height) {
                val normY = (y + 0.5f) / height
                val rowOffset = y * width
                for (x in 0 until width) {
                    val index = rowOffset + x
                    val normX = (x + 0.5f) / width

                    val isInsideErased = erasedRectangles.any { it.contains(normX, normY) }

                    if (isInsideErased) {
                        outputPixels[index] = 0x88FF3B30.toInt()
                    } else if (isPrintMask[index]) {
                        val p = originalPixels[index]
                        outputPixels[index] = (0xFF shl 24) or (p and 0x00FFFFFF)
                    } else {
                        val isChecker = ((x / 16) + (y / 16)) % 2 == 0
                        outputPixels[index] = if (isChecker) 0xFFE0E0E0.toInt() else 0xFFF5F5F5.toInt()
                    }

                    // Print Box border highlight (2px dashed/solid border)
                    if (printBox != null) {
                        val minXPx = (printBox.minX * width).toInt()
                        val maxXPx = (printBox.maxX * width).toInt()
                        val minYPx = (printBox.minY * height).toInt()
                        val maxYPx = (printBox.maxY * height).toInt()

                        val isBorderX = (abs(x - minXPx) <= 1 || abs(x - maxXPx) <= 1) && y in minYPx..maxYPx
                        val isBorderY = (abs(y - minYPx) <= 1 || abs(y - maxYPx) <= 1) && x in minXPx..maxXPx
                        if (isBorderX || isBorderY) {
                            outputPixels[index] = 0xFF00E5FF.toInt() // Bright Cyan Print Box Boundary
                        }
                    }
                }
            }
            Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            null
        }
    }
}
