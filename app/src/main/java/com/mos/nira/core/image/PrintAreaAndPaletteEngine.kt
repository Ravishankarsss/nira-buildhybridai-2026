package com.mos.nira.core.image

import android.graphics.Bitmap
import com.mos.nira.features.artwork.data.ArtworkBackgroundRules
import com.mos.nira.features.artwork.data.EdgeBlendRules
import com.mos.nira.features.artwork.data.GarmentMaskRules
import com.mos.nira.features.artwork.data.PhotoBackgroundRules
import com.mos.nira.features.artwork.data.PhotoVsArtworkRules
import com.mos.nira.features.artwork.data.ShadowVsInkRules
import com.mos.nira.features.artwork.data.SmallColourRules
import com.mos.nira.features.artwork.data.SeparationDecisionRules
import com.mos.nira.features.artwork.domain.ArtworkBackground
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
    /**
     * Runs body(i) for i in 0 until n on all processor cores. Each i must write only its own
     * output slot, so the result is exactly the same as a plain loop.
     */
    private inline fun parallelFor(n: Int, crossinline body: (Int) -> Unit) {
        if (n <= 0) return
        java.util.stream.IntStream.range(0, n).parallel().forEach { body(it) }
    }

    /** The distinct Lab colours in a list of points, and which distinct colour each point is. */
    private class DistinctLab(val colours: List<ColorConverter.Lab>, val indexOfPoint: IntArray) {
        companion object {
            fun of(points: List<ColorConverter.Lab>): DistinctLab {
                val indexOf = HashMap<ColorConverter.Lab, Int>(points.size * 2)
                val colours = ArrayList<ColorConverter.Lab>()
                val idx = IntArray(points.size)
                for (i in points.indices) {
                    val p = points[i]
                    idx[i] = indexOf.getOrPut(p) { colours.add(p); colours.size - 1 }
                }
                return DistinctLab(colours, idx)
            }
        }
    }

    /** Lab of every distinct colour in the image, computed once. Read-only after it is built. */
    private fun labByColour(pixels: IntArray): HashMap<Int, ColorConverter.Lab> {
        val map = HashMap<Int, ColorConverter.Lab>(4096)
        for (p in pixels) {
            val key = p and 0xFFFFFF
            if (!map.containsKey(key)) {
                map[key] = ColorConverter.rgbToLab((key shr 16) and 0xFF, (key shr 8) and 0xFF, key and 0xFF)
            }
        }
        return map
    }



    const val DEFAULT_MERGE_THRESHOLD_DELTA_E00 = 3.2
    const val MIN_COVERAGE_PERCENT = 1.5 // 1.5%
    const val MAX_KMEANS_ITERATIONS = 20

    /** Edge blending: label for fabric and for print outside the print box, and sample limits. */
    private const val EDGE_FABRIC = -2
    private const val EDGE_NONE = -3
    private const val EDGE_MAX_BLUR_SAMPLES = 512
    private const val EDGE_MAX_CANDIDATE_SAMPLES = 3_000
    private const val EDGE_KEY_SHIFT = 8
    private const val EDGE_KEY_BASE = 1_000_003L

    /** Shade merge in photos: border points measured per pair of shades, and the fewest that count as touching. */
    private const val MAX_MERGE_BORDER_SAMPLES = 512
    private const val MIN_MERGE_BORDER_SAMPLES = 8
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
        maskRules: GarmentMaskRules = GarmentMaskRules(),
        // From print_rules.json; null when the section is missing, and then the check is skipped.
        photoRules: PhotoVsArtworkRules? = null,
        shadowRules: ShadowVsInkRules? = null,
        edgeBlendRules: EdgeBlendRules? = null,
        smallColourRules: SmallColourRules? = null,
        backgroundRules: PhotoBackgroundRules? = null,
        artworkBackgroundRules: ArtworkBackgroundRules? = null
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
        val garmentProfile = estimateGarmentProfile(
            pixels, width, height, inputs.userGarmentHex, inputs.garmentColorHex, maskRules,
            uniformBorderShareMin = artworkBackgroundRules?.borderShareMin
        )
        val garmentLab = garmentProfile.medianLab
        // Light garment = one that needs no white base, by the white_base limits in print_rules.json.
        // Light garments use the same fabric rules as every other garment (ΔE00 to the measured
        // fabric, lighting flattened, texture smoothed); only the automatic halftone reduction differs.
        val isLightGarment = garmentLab.l >= decisionRules.darkGarmentThresholdL &&
            hypot(garmentLab.a, garmentLab.b) <= decisionRules.garmentChromaThreshold
        val effectiveGarmentThreshold = max(inputs.garmentThresholdDeltaE00, garmentProfile.adaptiveThresholdDeltaE00)

        // Step 2: Illumination Flattening on fabric & knit smoothing
        // print_rules.json garment_mask: flatten_lighting and smooth_texture apply to every garment.
        val illuminationCorrectedPixels = flattenUnevenLighting(pixels, width, height, garmentLab, garmentProfile.spreadDeltaE00)
        val smoothedPixels = smoothKnitTexture(illuminationCorrectedPixels, width, height)

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
                    val garmentFlag = ColorConverter.deltaE00(computedLab, garmentLab) <= effectiveGarmentThreshold
                    val entry = Pair(computedLab, garmentFlag)
                    labCache[p] = entry
                    entry
                }

                rawPrintMask[index] = !isGarment
            }
        }

        // Step 3a: The plain background of an artwork file (print_rules.json artwork_background).
        // It is left out (not ink) unless the user chose to print it.
        val artworkBackground = if (inputs.inputKind == InputKind.ARTWORK && artworkBackgroundRules != null &&
            !garmentProfile.isUserOverridden && garmentProfile.spreadDeltaE00 <= artworkBackgroundRules.flatSpreadMaxDeltaE00
        ) {
            val margin = max(1, (maskRules.borderFraction * min(width, height)).toInt())
            var border = 0
            var matching = 0
            val near = HashMap<Int, Boolean>()
            for (y in 0 until height) {
                val yBorder = y < margin || y >= height - margin
                for (x in 0 until width) {
                    if (!yBorder && x >= margin && x < width - margin) continue
                    val p = pixels[y * width + x]
                    if ((p ushr 24) < 128) continue
                    border++
                    val key = p and 0xFFFFFF
                    val isNear = near.getOrPut(key) {
                        ColorConverter.deltaE00(
                            ColorConverter.rgbToLab((key shr 16) and 0xFF, (key shr 8) and 0xFF, key and 0xFF), garmentLab
                        ) <= effectiveGarmentThreshold
                    }
                    if (isNear) matching++
                }
            }
            val share = if (border > 0) matching.toDouble() / border else 0.0
            if (share >= artworkBackgroundRules.borderShareMin) {
                var opaque = 0
                var background = 0
                for (i in 0 until totalPixels) {
                    if ((pixels[i] ushr 24) < 128) continue
                    opaque++
                    if (!rawPrintMask[i]) background++
                }
                ArtworkBackground(
                    hex = garmentProfile.estimatedHex,
                    borderSharePercent = share * 100.0,
                    imageCoveragePercent = if (opaque > 0) background * 100.0 / opaque else 0.0,
                    printed = inputs.artworkBackgroundAnswer == true
                )
            } else null
        } else null
        if (artworkBackground?.printed == true) {
            // "Print it": the background is ink, so every opaque pixel outside erased areas is print.
            for (i in 0 until totalPixels) {
                if ((pixels[i] ushr 24) < 128) continue
                val nx = (i % width + 0.5f) / width
                val ny = (i / width + 0.5f) / height
                if (inputs.erasedRectangles.none { it.contains(nx, ny) }) rawPrintMask[i] = true
            }
        }

        // Step 3b: Photo or artwork? The user's mode is used; the signals only decide whether to ask.
        val inputKindAssessment = photoRules?.let { rules ->
            val background = BooleanArray(totalPixels) { !rawPrintMask[it] && ((pixels[it] ushr 24) >= 128) }
            InputKindClassifier.assess(
                chosen = inputs.inputKind,
                pixels = pixels,
                width = width,
                height = height,
                backgroundMask = background,
                cameraExif = inputs.cameraExif,
                hasTransparency = pixels.any { (it ushr 24) < 255 },
                rules = rules
            )
        }

        // Step 4: Speck Removal & Print Box Isolation
        val maskWithSpecksRemoved = removeSpecks(rawPrintMask, width, height, maskRules.speckMinAreaFraction)

        // Step 4a: Table, floor or wall at the photo edge (print_rules.json photo_background).
        val photoEdgeExcluded = if (inputs.inputKind == InputKind.PHOTO && backgroundRules != null && !inputs.includePhotoEdgeArea) {
            findPhotoEdgeBackground(maskWithSpecksRemoved, width, height, backgroundRules)
        } else null
        val maskWithoutSpecks = if (photoEdgeExcluded == null) maskWithSpecksRemoved
            else BooleanArray(totalPixels) { maskWithSpecksRemoved[it] && !photoEdgeExcluded[it] }
        val photoEdgeExcludedPercent = photoEdgeExcluded?.let { m -> m.count { it } * 100.0 / totalPixels } ?: 0.0

        // Step 4b: Fold shadows (print_rules.json shadow_vs_ink), only on photos of light garments.
        val shadowCheck = if (inputs.inputKind == InputKind.PHOTO && isLightGarment && shadowRules != null) {
            checkFoldShadows(smoothedPixels, width, height, maskWithoutSpecks, garmentLab, inputs, shadowRules, maskRules.speckMinAreaFraction)
        } else null
        val cleanedPrintMask = shadowCheck?.mask ?: maskWithoutSpecks
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
        val maskedIndexList = ArrayList<Int>(totalBoxPixels)
        val finalPrintMask = BooleanArray(totalPixels)

        for (y in boxMinYPx until boxMaxYPx) {
            val rowOffset = y * width
            for (x in boxMinXPx until boxMaxXPx) {
                val index = rowOffset + x
                if (cleanedPrintMask[index]) {
                    finalPrintMask[index] = true
                    val p = smoothedPixels[index]
                    maskedRgbList.add(p)
                    maskedIndexList.add(index)
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
                createMaskPreviewBitmap(pixels, finalPrintMask, width, height, inputs.erasedRectangles, activePrintBox, photoEdgeExcluded)
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
                lowResolutionWarning = lowResolutionWarning,
                inputKindAssessment = inputKindAssessment,
                foldShadowsRemoved = shadowCheck?.removed ?: 0,
                shadowTestLowResolution = shadowCheck?.lowResolution ?: false,
                artworkBackground = artworkBackground
            )
        }

        // Subsample for fast clustering if pixel count > 10,000
        // Evenly spread over the whole print. (Before 18 Nov 2026 the step was rounded down and the
        // list cut at 10,000, so the last rows of the print were never sampled and a colour that
        // appears only near the bottom could be lost.)
        val trainingIndices: List<Int> = if (printPixelCount > 10000) {
            List(10000) { i -> (i.toLong() * printPixelCount / 10000).toInt() }
        } else {
            (0 until printPixelCount).toList()
        }

        val trainingLabList = trainingIndices.map { maskedLabList[it] }

        // Step 7: Overcluster in Lab (K = 16, fixed seed)
        val kInitial = min(16, trainingLabList.size)
        val clusterCenters = runKMeans(trainingLabList, kInitial, seed)
        // Same points, same K and same seed as the K=16 point of the error curve (Step 10), so
        // that point reuses these centres instead of clustering again.
        val initialCentersSnapshot: List<ColorConverter.Lab> = ArrayList(clusterCenters)

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
            /** Small colour kept for the user to confirm: a point in its largest area. */
            var smallColourPoint: Pair<Float, Float>? = null
            /** Kept by the small-colour rule (flagged or confirmed): never dropped by later steps. */
            var keptSmallColour: Boolean = false
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
        // When an artwork's background is printed, no fabric is visible in the file, so no colour is
        // dropped for being close to the "fabric" (that would drop the printed background itself).
        val backgroundPrinted = artworkBackground?.printed == true
        var activeBuckets = buckets.filter { b ->
            if (b.count <= 0) false
            else if (backgroundPrinted) true
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

        // Step 9: Colours under the minimum coverage (print_rules.json small_colours). A compact real
        // area, distinct from every other colour, is kept and flagged for the user to confirm; only
        // scattered specks are removed (their pixels go to the nearest ink) and counted. Without the
        // section the old behaviour remains: drop under MIN_COVERAGE_PERCENT.
        val minCoverage = smallColourRules?.minCoveragePercent ?: MIN_COVERAGE_PERCENT
        val thresholdCount = (printPixelCount * (minCoverage / 100.0)).toInt()
        val prunedBuckets = activeBuckets.filter { it.count >= thresholdCount }.toMutableList()
        var speckPixelsRemoved = 0
        if (smallColourRules != null && prunedBuckets.isNotEmpty()) {
            val smallOnes = activeBuckets.filter { it.count in 1 until thresholdCount }
            if (smallOnes.isNotEmpty()) {
                val assign = assignPixelsToCenters(maskedRgbList, maskedLabList, activeBuckets.map { it.centerLab })
                val labelOf = IntArray(totalPixels) { -1 }
                for (i in 0 until printPixelCount) labelOf[maskedIndexList[i]] = assign[i]
                val minRegionPx = smallColourRules.minRegionMm2 * (pixelsPerCm / 10.0) * (pixelsPerCm / 10.0)
                for (s in smallOnes) {
                    val idx = activeBuckets.indexOf(s)
                    // A shade of a colour already kept is not a new ink: it joins the nearest one.
                    val distinct = prunedBuckets.all {
                        ColorConverter.deltaE00(it.centerLab, s.centerLab) >= smallColourRules.mergeThresholdDeltaE00
                    }
                    if (!distinct) continue
                    val areas = ShadowInkClassifier.regions(BooleanArray(totalPixels) { labelOf[it] == idx }, width, height)
                    val largest = areas.maxByOrNull { it.size }
                    if (largest == null || largest.size < minRegionPx) {
                        speckPixelsRemoved += s.count // scattered specks: noise
                        continue
                    }
                    val first = largest.minOrNull()!!
                    val point = Pair((first % width + 0.5f) / width, (first / width + 0.5f) / height)
                    val answer = inputs.smallColourAnswers.firstOrNull { ans ->
                        val x = (ans.normX * width).toInt().coerceIn(0, width - 1)
                        val y = (ans.normY * height).toInt().coerceIn(0, height - 1)
                        labelOf[y * width + x] == idx
                    }
                    when {
                        answer == null -> { s.smallColourPoint = point; s.keptSmallColour = true; prunedBuckets.add(s) }
                        answer.isInk -> { s.keptSmallColour = true; prunedBuckets.add(s) } // confirmed: a screen, no flag
                        else -> Unit // "merge into the nearest ink"
                    }
                }
            }
        }
        var finalBuckets = if (prunedBuckets.isNotEmpty()) prunedBuckets else activeBuckets.take(1).toMutableList()

        // Step 9b: On a garment that needs a white base (not light), a colour that is a halftone of one ink over the garment,
        // or a smooth transition between two inks, is not a separate ink. Keep the fewest inks
        // that reproduce every colour within the quality tolerance from print_rules.json.
        // Small colours kept by the small-colour rule are left out: their few pixels barely move the
        // mean or p95 error, so the reduction would drop a real accent ink (the magenta eye).
        val smallKept = finalBuckets.filter { it.keptSmallColour }
        val reducible = finalBuckets.filter { !it.keptSmallColour }
        if (!isLightGarment && reducible.size > 1) {
            val garmentRgb = ColorConverter.labToRgb(garmentLab.l, garmentLab.a, garmentLab.b)
            val keep = selectInksByHalftoneReduction(
                colours = reducible.map { intArrayOf(it.r, it.g, it.b) },
                counts = reducible.map { it.count },
                garment = intArrayOf(garmentRgb.r, garmentRgb.g, garmentRgb.b),
                toleranceMean = decisionRules.qualityToleranceDeltaE00,
                toleranceP95 = decisionRules.qualityToleranceP95DeltaE00
            )
            if (keep.size < reducible.size) {
                finalBuckets = (keep.map { index ->
                    reducible[index].also { it.inkRgb = intArrayOf(it.r, it.g, it.b) }
                } + smallKept).toMutableList()
            }
        }

        // Step 9c: In a photo, a light and a dark shade of one ink under uneven light are one ink
        // (print_rules.json photo_vs_artwork merge_shades_in_photo): same hue within the limit, they
        // touch, and the lightness change across their border is wider than an ink edge.
        if (inputs.inputKind == InputKind.PHOTO && photoRules != null && shadowRules != null && finalBuckets.size > 1) {
            val n = finalBuckets.size
            val neutralLimit = shadowRules.hueTestOnlyIfGarmentChromaAtLeast
            val chromatic = BooleanArray(n) { hypot(finalBuckets[it].centerLab.a, finalBuckets[it].centerLab.b) >= neutralLimit }
            val hue = DoubleArray(n) { Math.toDegrees(kotlin.math.atan2(finalBuckets[it].centerLab.b, finalBuckets[it].centerLab.a)) }
            fun sameHue(a: Int, b: Int): Boolean {
                if (!chromatic[a] || !chromatic[b]) return false // neutral shades have no reliable hue
                val d = abs(hue[a] - hue[b]) % 360.0
                return (if (d > 180.0) 360.0 - d else d) <= photoRules.mergeShadesInPhotoHueWithinDeg
            }

            val assign = assignPixelsToCenters(maskedRgbList, maskedLabList, finalBuckets.map { it.centerLab })
            val labelOf = IntArray(totalPixels) { -1 }
            for (i in 0 until printPixelCount) labelOf[maskedIndexList[i]] = assign[i]

            // Border points between same-hue shades: (pixel, step) with the first shade at pixel.
            val samples = HashMap<Long, ArrayList<Long>>()
            for (i in 0 until printPixelCount) {
                val p = maskedIndexList[i]
                val a = labelOf[p]
                for (step in intArrayOf(1, width)) {
                    val q = p + step
                    if (q >= totalPixels || (step == 1 && q / width != p / width)) continue
                    val b = labelOf[q]
                    if (b < 0 || b == a || !sameHue(a, b)) continue
                    val key = min(a, b).toLong() * n + max(a, b)
                    val list = samples.getOrPut(key) { ArrayList() }
                    if (list.size < MAX_MERGE_BORDER_SAMPLES) list.add(p.toLong() * 2 + (if (step == 1) 0 else 1))
                }
            }

            val lCache = HashMap<Int, Double>()
            fun lAt(p: Int): Double {
                val c = smoothedPixels[p] and 0xFFFFFF
                return lCache.getOrPut(c) { ColorConverter.rgbToLab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).l }
            }
            // Same measuring window as the fold-shadow edge test.
            val span = kotlin.math.ceil(2.0 * shadowRules.shadowEdgeMinMm * pixelsPerCm / 10.0).toInt().coerceAtLeast(2)
            val parent = IntArray(n) { it }
            fun root(x: Int): Int { var y = x; while (parent[y] != y) y = parent[y]; return y }
            for ((_, list) in samples) {
                if (list.size < MIN_MERGE_BORDER_SAMPLES) continue // they barely touch
                val widths = ArrayList<Double>()
                for (code in list) {
                    val p = (code / 2).toInt()
                    val step = if (code % 2 == 0L) 1 else width
                    val a = labelOf[p]
                    val b = labelOf[p + step]
                    val first = ArrayList<Double>()
                    var x = p
                    for (k in 0 until span) {
                        if (labelOf[x] != a) break
                        first.add(lAt(x))
                        val nx = x - step
                        if (nx < 0 || (step == 1 && nx / width != x / width)) break
                        x = nx
                    }
                    val second = ArrayList<Double>()
                    x = p + step
                    for (k in 0 until span) {
                        if (labelOf[x] != b) break
                        second.add(lAt(x))
                        val nx = x + step
                        if (nx >= totalPixels || (step == 1 && nx / width != x / width)) break
                        x = nx
                    }
                    ShadowInkClassifier.edgeWidth10to90Px(first.asReversed() + second, first.size)?.let { widths.add(it) }
                }
                if (widths.isEmpty()) continue
                widths.sort()
                val edgeMm = widths[widths.size / 2] / (pixelsPerCm / 10.0)
                if (edgeMm > shadowRules.inkEdgeMaxMm) {
                    val first = list.first()
                    val p = (first / 2).toInt()
                    val step = if (first % 2 == 0L) 1 else width
                    val ra = root(labelOf[p])
                    val rb = root(labelOf[p + step])
                    if (ra != rb) parent[max(ra, rb)] = min(ra, rb)
                }
            }

            val groups = (0 until n).groupBy { root(it) }
            if (groups.size < n) {
                finalBuckets = groups.values.map { members ->
                    val m = members.map { finalBuckets[it] }
                    val merged = ClusterBucket(m.maxByOrNull { it.count }!!.centerLab)
                    for (b in m) {
                        merged.count += b.count
                        merged.sumR += b.sumR
                        merged.sumG += b.sumG
                        merged.sumB += b.sumB
                    }
                    merged.inkRgb = m.maxByOrNull { it.count }!!.inkRgb
                    if (merged.count > 0) {
                        merged.centerLab = ColorConverter.rgbToLab(merged.r, merged.g, merged.b)
                    }
                    merged
                }.toMutableList()
            }
        }

        // Reassign all masked pixels to final cluster centroids for precise counts & coverage
        val finalCenters = finalBuckets.map { it.centerLab }
        val finalAssignments = assignPixelsToCenters(maskedRgbList, maskedLabList, finalCenters)

        // Step 9d: Edge blending (print_rules.json edge_blend). Thin bands of mixed colour between two
        // inks (or ink and fabric) from camera blur or anti-aliasing go to the nearer neighbour; a
        // pixel nearer the fabric leaves the print area. Undone by "Keep edge colours as separate inks".
        // Repeated: once a band is merged, a band next to it (blur often makes two in-between
        // colours) is judged again between its new neighbours.
        var edgeBlendPixelsMerged = 0
        var edgeBandMaxPx: Double? = null
        val edgeFlagged = HashSet<Int>()
        if (edgeBlendRules != null && !inputs.keepEdgeColours && finalBuckets.size >= 2) {
            val garmentRgb = ColorConverter.labToRgb(garmentLab.l, garmentLab.a, garmentLab.b)
            for (round in 0 until finalBuckets.size) {
                // Each colour as it is now: the mean of the pixels assigned to it (ink colour if set).
                val sumR = LongArray(finalBuckets.size)
                val sumG = LongArray(finalBuckets.size)
                val sumB = LongArray(finalBuckets.size)
                val cnt = IntArray(finalBuckets.size)
                for (i in 0 until printPixelCount) {
                    val c = finalAssignments[i]
                    if (c < 0) continue
                    val p = maskedRgbList[i]
                    sumR[c] += (p shr 16) and 0xFF; sumG[c] += (p shr 8) and 0xFF; sumB[c] += p and 0xFF; cnt[c]++
                }
                val bucketRgb = finalBuckets.mapIndexed { k, b ->
                    val ink = b.inkRgb
                    when {
                        ink != null -> ColorConverter.Rgb(ink[0], ink[1], ink[2])
                        cnt[k] > 0 -> ColorConverter.Rgb((sumR[k] / cnt[k]).toInt(), (sumG[k] / cnt[k]).toInt(), (sumB[k] / cnt[k]).toInt())
                        else -> ColorConverter.Rgb(b.r, b.g, b.b)
                    }
                }
                val plan = planEdgeBlends(
                    smoothedPixels, width, height, cleanedPrintMask, maskedIndexList, finalAssignments,
                    bucketRgb, garmentRgb, inputs, pixelsPerCm, decisionRules.minResolutionPxPerCm, edgeBlendRules
                )
                edgeBandMaxPx = edgeBandMaxPx ?: plan.bandMaxPx
                edgeFlagged.addAll(plan.flagged)
                if (plan.targets.isEmpty()) break
                for (i in 0 until printPixelCount) {
                    val c0 = finalAssignments[i]
                    if (c0 < 0) continue
                    val (a, b) = plan.targets[c0] ?: continue
                    val px = smoothedPixels[maskedIndexList[i]]
                    val c = ColorConverter.Rgb((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
                    val ra = if (a == EDGE_FABRIC) garmentRgb else bucketRgb[a]
                    val rb = if (b == EDGE_FABRIC) garmentRgb else bucketRgb[b]
                    val target = if (EdgeBlend.mixRatio(c, ra, rb) < 0.5) a else b
                    finalAssignments[i] = if (target == EDGE_FABRIC) -1 else target
                    if (target == EDGE_FABRIC) finalPrintMask[maskedIndexList[i]] = false
                    edgeBlendPixelsMerged++
                }
            }
        }

        finalBuckets.forEach {
            it.count = 0
            it.sumR = 0L
            it.sumG = 0L
            it.sumB = 0L
        }

        var keptPrintPixelCount = 0
        for (i in 0 until printPixelCount) {
            val c = finalAssignments[i]
            if (c < 0) continue // a blur pixel nearer the fabric: no longer print
            keptPrintPixelCount++
            val p = maskedRgbList[i]
            val b = finalBuckets[c]
            b.count++
            b.sumR += (p shr 16) and 0xFF
            b.sumG += (p shr 8) and 0xFF
            b.sumB += p and 0xFF
        }
        val finalCoveragePercent = if (totalBoxPixels > 0) keptPrintPixelCount * 100.0 / totalBoxPixels else 0.0
        val finalEffectiveAreaCm2 = totalBoxAreaCm2 * (keptPrintPixelCount.toDouble() / totalBoxPixels.toDouble())

        // A colour is flagged "possible fold shadow" when it is the main colour of an uncertain region.
        val flaggedPoints = HashMap<Int, MutableList<Pair<Float, Float>>>()
        shadowCheck?.uncertain?.takeIf { it.isNotEmpty() }?.let { uncertain ->
            val regionOfPixel = IntArray(totalPixels) { -1 }
            uncertain.forEachIndexed { u, r -> for (px in r.pixels) regionOfPixel[px] = u }
            val counts = Array(uncertain.size) { IntArray(finalBuckets.size) }
            for (i in 0 until printPixelCount) {
                val u = regionOfPixel[maskedIndexList[i]]
                if (u >= 0 && finalAssignments[i] >= 0) counts[u][finalAssignments[i]]++
            }
            // On a neutral garment a shadow is neutral, so a clearly coloured palette colour is
            // never labelled "possible fold shadow" (limit: shadow_vs_ink hue-test chroma).
            val neutralLimit = shadowRules!!.hueTestOnlyIfGarmentChromaAtLeast
            val garmentNeutral = hypot(garmentLab.a, garmentLab.b) < neutralLimit
            uncertain.forEachIndexed { u, r ->
                val main = counts[u].indices.maxByOrNull { counts[u][it] } ?: return@forEachIndexed
                val c = finalBuckets[main].centerLab
                if (garmentNeutral && hypot(c.a, c.b) >= neutralLimit) return@forEachIndexed
                if (counts[u][main] > 0) flaggedPoints.getOrPut(main) { ArrayList() }.add(r.point)
            }
        }

        // Build MeasuredColor palette
        val measuredPalette = finalBuckets
            .withIndex()
            .filter { it.value.count > 0 }
            .sortedByDescending { it.value.count }
            .mapIndexed { index, (bucketIndex, b) ->
                val inkR = b.inkRgb?.get(0) ?: b.r
                val inkG = b.inkRgb?.get(1) ?: b.g
                val inkB = b.inkRgb?.get(2) ?: b.b
                val hex = ColorConverter.rgbToHex(inkR, inkG, inkB)
                val rgb = ColorConverter.Rgb(inkR, inkG, inkB)
                val lab = ColorConverter.rgbToLab(inkR, inkG, inkB)
                val covPercent = (b.count.toDouble() / maxOf(1, keptPrintPixelCount).toDouble()) * 100.0
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
                    matchGrade = nearestShadeMatch?.second?.let { ColorConverter.matchGrade(it) },
                    possibleFoldShadow = flaggedPoints.containsKey(bucketIndex),
                    uncertainRegionPoints = flaggedPoints[bucketIndex] ?: emptyList(),
                    possibleEdgeBlend = bucketIndex in edgeFlagged,
                    // Flagged only while it is still under the minimum: if more pixels joined it in the
                    // final assignment it is an ordinary colour.
                    possibleSmallColour = b.smallColourPoint != null && covPercent < minCoverage,
                    smallColourPoint = b.smallColourPoint.takeIf { covPercent < minCoverage }
                )
            }

        // Step 10: Flat-separation posterisation error curve for K = 4, 6, 8, 10, 12, 16
        val targetKs = listOf(4, 6, 8, 10, 12, 16)
        // The six clusterings are independent, so they run at the same time. Each one is the
        // same calculation as before, so the curve is unchanged.
        val errorCurveSlots = arrayOfNulls<ErrorCurvePoint>(targetKs.size)
        val evalList = if (maskedLabList.size > 5000) trainingLabList else maskedLabList
        val evalDistinct = DistinctLab.of(evalList)
        parallelFor(targetKs.size) { slot ->
            val k = targetKs[slot]
            val effectiveK = min(k, trainingLabList.size)
            errorCurveSlots[slot] = if (effectiveK <= 0) {
                ErrorCurvePoint(k, 0.0)
            } else {
                val kCenters = if (effectiveK == kInitial) initialCentersSnapshot
                    else runKMeans(trainingLabList, effectiveK, seed)
                // Nearest-centre distance once per distinct colour, then copied to each point.
                val uniqMin = DoubleArray(evalDistinct.colours.size)
                parallelFor(uniqMin.size) { u ->
                    val pixelLab = evalDistinct.colours[u]
                    var minDist = Double.MAX_VALUE
                    for (center in kCenters) {
                        val d = ColorConverter.deltaE00(pixelLab, center)
                        if (d < minDist) {
                            minDist = d
                        }
                    }
                    uniqMin[u] = minDist
                }
                var totalDeltaE = 0.0
                for (i in evalList.indices) totalDeltaE += uniqMin[evalDistinct.indexOfPoint[i]] // same order as before
                val meanDeltaE = totalDeltaE / evalList.size.toDouble()
                ErrorCurvePoint(k = k, meanDeltaE00 = meanDeltaE)
            }
        }
        val errorCurve = errorCurveSlots.map { it!! }

        // Step 11: Render Mask Bitmap with print box overlay
        val maskBitmap = if (generateMaskBitmap) {
            createMaskPreviewBitmap(pixels, finalPrintMask, width, height, inputs.erasedRectangles, activePrintBox, photoEdgeExcluded)
        } else null

        return PartAResult(
            printWidthCm = printWidthCm,
            printHeightCm = printHeightCm,
            totalBoxAreaCm2 = totalBoxAreaCm2,
            effectivePrintAreaCm2 = finalEffectiveAreaCm2,
            printPixelCount = keptPrintPixelCount,
            totalPixelCount = totalBoxPixels,
            printMaskCoveragePercent = finalCoveragePercent,
            measuredPalette = measuredPalette,
            errorCurve = errorCurve,
            maskBitmap = maskBitmap,
            garmentProfile = garmentProfile,
            printBox = activePrintBox,
            pixelsPerCm = pixelsPerCm,
            lowResolutionWarning = lowResolutionWarning,
            inputKindAssessment = inputKindAssessment,
            foldShadowsRemoved = shadowCheck?.removed ?: 0,
            shadowTestLowResolution = shadowCheck?.lowResolution ?: false,
            edgeBlendPixelsMerged = edgeBlendPixelsMerged,
            edgeBlendBandMaxPx = edgeBandMaxPx,
            speckPixelsRemoved = speckPixelsRemoved,
            photoEdgeExcludedPercent = photoEdgeExcludedPercent,
            artworkBackground = artworkBackground
        )
    }

    /**
     * print_rules.json photo_background: a non-fabric region that touches the photo border along at
     * least min_border_touch_fraction of one side, covers at least min_area_percent of the photo and
     * does not reach the centre box is a table, floor or wall. Returns those pixels, or null if none.
     */
    private fun findPhotoEdgeBackground(
        mask: BooleanArray,
        width: Int,
        height: Int,
        rules: PhotoBackgroundRules
    ): BooleanArray? {
        val total = width * height
        val margin = (1.0 - rules.centreBoxFraction) / 2.0
        val cx0 = (width * margin).toInt()
        val cx1 = (width * (1.0 - margin)).toInt()
        val cy0 = (height * margin).toInt()
        val cy1 = (height * (1.0 - margin)).toInt()
        var result: BooleanArray? = null
        for (region in ShadowInkClassifier.regions(mask, width, height)) {
            if (region.size * 100.0 / total < rules.minAreaPercent) continue
            var top = 0; var bottom = 0; var left = 0; var right = 0
            var reachesCentre = false
            for (p in region) {
                val x = p % width
                val y = p / width
                if (y == 0) top++
                if (y == height - 1) bottom++
                if (x == 0) left++
                if (x == width - 1) right++
                if (x in cx0 until cx1 && y in cy0 until cy1) reachesCentre = true
            }
            if (reachesCentre) continue
            val touch = maxOf(top.toDouble() / width, bottom.toDouble() / width, left.toDouble() / height, right.toDouble() / height)
            if (touch < rules.minBorderTouchFraction) continue
            val r = result ?: BooleanArray(total).also { result = it }
            for (p in region) r[p] = true
        }
        return result
    }

    /** Edge blending plan: blur colour -> its two neighbours (bucket index or EDGE_FABRIC), and colours only flagged. */
    private class EdgeBlendPlan(val targets: Map<Int, Pair<Int, Int>>, val flagged: Set<Int>, val bandMaxPx: Double)

    /**
     * print_rules.json edge_blend. A palette colour is camera blur or anti-aliasing when, for most of
     * its pixels, the band across it (between two other colours, or a colour and the fabric) is no
     * wider than the band limit; its colour lies on the mixing line between those two; and across the
     * band the mixing ratio changes step by step with no flat middle (a printed line has one).
     * The band limit comes from blur measured on clean ink-to-fabric edges in this image (photos), or
     * band_max_px_artwork. Below the minimum px/cm such colours are flagged unless the user answered.
     */
    private fun planEdgeBlends(
        pixels: IntArray,
        width: Int,
        height: Int,
        printMask: BooleanArray,
        maskedIndexList: List<Int>,
        assignments: IntArray,
        bucketRgb: List<ColorConverter.Rgb>,
        garmentRgb: ColorConverter.Rgb,
        inputs: PrintJobInputs,
        pixelsPerCm: Double,
        minPxPerCm: Double,
        rules: EdgeBlendRules
    ): EdgeBlendPlan {
        val total = width * height
        val n = bucketRgb.size
        val isPhoto = inputs.inputKind == InputKind.PHOTO
        // Labels: palette index, EDGE_FABRIC for fabric, EDGE_NONE for print outside the print box.
        val labelOf = IntArray(total) { if (printMask[it]) EDGE_NONE else EDGE_FABRIC }
        // A pixel an earlier round sent back to the fabric (-1) counts as fabric.
        for (i in maskedIndexList.indices) labelOf[maskedIndexList[i]] = if (assignments[i] >= 0) assignments[i] else EDGE_FABRIC
        fun rgbAt(p: Int): ColorConverter.Rgb {
            val c = pixels[p]
            return ColorConverter.Rgb((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
        val lCache = HashMap<Int, Double>()
        fun lAt(p: Int): Double {
            val c = pixels[p] and 0xFFFFFF
            return lCache.getOrPut(c) { ColorConverter.rgbToLab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).l }
        }
        fun step(p: Int, d: Int): Int? {
            val q = p + d
            if (q < 0 || q >= total) return null
            if ((d == 1 || d == -1) && q / width != p / width) return null
            return q
        }

        // Blur of this image: 10-90% width of clean ink-to-fabric edges (photos only).
        val measuredBlur = if (!isPhoto) null else {
            val reach = kotlin.math.ceil(2 * rules.bandMaxPxCap).toInt()
            val need = kotlin.math.ceil(rules.bandMaxPxCap).toInt()
            val widths = ArrayList<Double>()
            val stride = maxOf(1, maskedIndexList.size / 20_000)
            var i = 0
            while (i < maskedIndexList.size && widths.size < EDGE_MAX_BLUR_SAMPLES) {
                val p = maskedIndexList[i]
                i += stride
                for (d in intArrayOf(1, -1, width, -width)) {
                    val q = step(p, d) ?: continue
                    if (labelOf[q] != EDGE_FABRIC) continue
                    val fabric = ArrayList<Double>()
                    var x: Int? = q
                    while (x != null && fabric.size < reach && labelOf[x] == EDGE_FABRIC) { fabric.add(lAt(x)); x = step(x, d) }
                    val print = ArrayList<Double>()
                    x = p
                    while (x != null && print.size < reach && labelOf[x] >= 0) { print.add(lAt(x)); x = step(x, -d) }
                    if (fabric.size < need || print.size < need) continue
                    ShadowInkClassifier.edgeWidth10to90Px(fabric.asReversed() + print, fabric.size)?.let { widths.add(it) }
                    break
                }
            }
            EdgeBlend.median(widths)
        }
        val bandMax = EdgeBlend.bandMaxPx(isPhoto, measuredBlur, rules)
        val reachBand = kotlin.math.floor(bandMax).toInt() + 1

        // Pixels of each palette colour.
        val pixelsOf = Array(n) { ArrayList<Int>() }
        for (i in maskedIndexList.indices) if (assignments[i] >= 0) pixelsOf[assignments[i]].add(maskedIndexList[i])

        class Crossing(val from: Int, val dir: Int, val len: Int)
        val targets = HashMap<Int, Pair<Int, Int>>()
        val flagged = HashSet<Int>()
        val protected = HashSet<Int>()
        val lowResolution = pixelsPerCm < minPxPerCm
        for (c in (0 until n).sortedBy { pixelsOf[it].size }) {
            val own = pixelsOf[c]
            if (own.isEmpty() || c in protected) continue
            val stride = maxOf(1, own.size / EDGE_MAX_CANDIDATE_SAMPLES)
            var sampled = 0
            val pairCount = HashMap<Long, Int>()
            val crossings = HashMap<Long, ArrayList<Crossing>>()
            var k = 0
            while (k < own.size) {
                val p = own[k]
                k += stride
                sampled++
                var best: Triple<Long, Crossing, Int>? = null
                for (d in intArrayOf(1, width)) {
                    var back = p
                    var d1 = 0
                    var l1 = EDGE_NONE
                    while (true) {
                        val q = step(back, -d)
                        d1++
                        if (q == null) { l1 = EDGE_NONE; break }
                        if (labelOf[q] != c) { l1 = labelOf[q]; back = q; break }
                        back = q
                        if (d1 > reachBand) { l1 = EDGE_NONE; break }
                    }
                    var fwd = p
                    var d2 = 0
                    var l2 = EDGE_NONE
                    while (true) {
                        val q = step(fwd, d)
                        d2++
                        if (q == null) { l2 = EDGE_NONE; break }
                        if (labelOf[q] != c) { l2 = labelOf[q]; break }
                        fwd = q
                        if (d2 > reachBand) { l2 = EDGE_NONE; break }
                    }
                    val bandWidth = d1 + d2 - 1
                    if (l1 == EDGE_NONE || l2 == EDGE_NONE || l1 == l2 || bandWidth > bandMax) continue
                    if (best == null || bandWidth < best.third) {
                        // Pair key; labels are shifted so fabric (negative) still decodes correctly.
                        val key = (minOf(l1, l2) + EDGE_KEY_SHIFT).toLong() * EDGE_KEY_BASE + (maxOf(l1, l2) + EDGE_KEY_SHIFT)
                        // The crossing starts at the first band pixel after the neighbour "back".
                        best = Triple(key, Crossing(back + d, d, bandWidth), bandWidth)
                    }
                }
                best?.let { (key, crossing, _) ->
                    pairCount[key] = (pairCount[key] ?: 0) + 1
                    crossings.getOrPut(key) { ArrayList() }.add(crossing)
                }
            }
            if (sampled == 0) continue
            val (pairKey, pairHits) = pairCount.maxByOrNull { it.value } ?: continue
            if (pairCount.values.sum().toDouble() / sampled < rules.sandwichedMinFraction) continue
            val a = (pairKey / EDGE_KEY_BASE).toInt() - EDGE_KEY_SHIFT
            val b = (pairKey % EDGE_KEY_BASE).toInt() - EDGE_KEY_SHIFT
            if (a in targets || b in targets) continue
            val rgbA = if (a == EDGE_FABRIC) garmentRgb else bucketRgb[a]
            val rgbB = if (b == EDGE_FABRIC) garmentRgb else bucketRgb[b]

            // Keyline protection: blur is a mix of its neighbours, so it can never be darker than
            // both. A band much darker than both is a printed keyline: never merged, never flagged.
            val lC = ColorConverter.rgbToLab(bucketRgb[c]).l
            val lA = ColorConverter.rgbToLab(rgbA).l
            val lB = ColorConverter.rgbToLab(rgbB).l
            if (lC <= minOf(lA, lB) - rules.keylineMinLDrop) continue

            // On the mixing line, strictly between the two neighbours.
            val fit = EdgeBlend.mixFit(bucketRgb[c], rgbA, rgbB)
            if (fit.t <= 0.0 || fit.t >= 1.0 || fit.deltaE00 > rules.onMixingLineMaxDeltaE00) continue

            // Across the band: step-by-step change, no flat middle (in at least half the crossings).
            val list = crossings[pairKey] ?: continue
            val blurVotes = list.count { cr ->
                val ratios = (0 until cr.len).mapNotNull { j ->
                    val q = cr.from + j * cr.dir
                    if (q in 0 until total) EdgeBlend.mixRatio(rgbAt(q), rgbA, rgbB) else null
                }
                EdgeBlend.crossingLooksLikeBlur(ratios, rules)
            }
            if (blurVotes * 2 < list.size || pairHits == 0) continue

            if (lowResolution && inputs.edgeBlendAnswer != true) {
                if (inputs.edgeBlendAnswer == null) flagged.add(c) // a printed thin line looks like blur here
                continue
            }
            targets[c] = Pair(a, b)
            if (a >= 0) protected.add(a)
            if (b >= 0) protected.add(b)
        }
        return EdgeBlendPlan(targets, flagged, bandMax)
    }

    /** One region left as "possible fold shadow": its pixels and a point inside it (normalised). */
    private class UncertainRegion(val pixels: IntArray, val point: Pair<Float, Float>)

    private class FoldShadowCheck(
        val mask: BooleanArray,
        val removed: Int,
        val lowResolution: Boolean,
        val uncertain: List<UncertainRegion>
    )

    /**
     * print_rules.json shadow_vs_ink on the print mask of a light-garment photo: fold shadows are
     * removed, ink is kept, and uncertain regions are kept and reported so the user can answer.
     *
     * The print box is the user's box, or else the box around the regions that do not touch the
     * photo edge (a fold usually runs across the photo; a print sits inside it). It is used for the
     * location test, and with the print width it gives the pixels per cm.
     *
     * Below the minimum resolution the edge cannot be measured, so every region that is not already
     * settled (a clearly coloured region on a neutral garment is ink) is uncertain, and the screen
     * suggests a retake.
     */
    private fun checkFoldShadows(
        pixels: IntArray,
        width: Int,
        height: Int,
        mask: BooleanArray,
        garmentLab: ColorConverter.Lab,
        inputs: PrintJobInputs,
        rules: ShadowVsInkRules,
        speckMinAreaFraction: Double
    ): FoldShadowCheck {
        // On a neutral garment a shadow is neutral, so grey pixels are grouped apart from coloured
        // ones: a fold that runs into a coloured print is judged on its own, not as part of the ink.
        val neutralLimit = rules.hueTestOnlyIfGarmentChromaAtLeast
        val labelled = if (hypot(garmentLab.a, garmentLab.b) < neutralLimit) {
            val labCacheGroup = HashMap<Int, Boolean>()
            val neutral = BooleanArray(mask.size) { i ->
                if (!mask[i]) false else {
                    val c = pixels[i] and 0xFFFFFF
                    labCacheGroup.getOrPut(c) {
                        val lab = ColorConverter.rgbToLab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
                        hypot(lab.a, lab.b) < neutralLimit
                    }
                }
            }
            // Grey pieces smaller than a speck (garment_mask.speck_min_area_fraction) are camera
            // noise inside a coloured area: they stay with their neighbours.
            val minArea = speckMinAreaFraction * mask.size
            for (piece in ShadowInkClassifier.regions(neutral, width, height)) {
                if (piece.size < minArea) for (p in piece) neutral[p] = false
            }
            ShadowInkClassifier.labelRegionsByGroup(mask, neutral, width, height)
        } else {
            ShadowInkClassifier.labelRegions(mask, width, height)
        }
        if (labelled.regions.isEmpty()) return FoldShadowCheck(mask, 0, false, emptyList())

        val labCache = HashMap<Int, ColorConverter.Lab>()
        fun pxPerCmFor(box: NormalizedRect) = (box.maxX - box.minX) * width / inputs.printWidthCm

        // The whole print: the user's box, or the regions that do not touch the photo edge.
        // Pixels per cm come from this box and the print width.
        val wholePrintBox = inputs.printBox ?: run {
            val inner = BooleanArray(mask.size)
            var any = false
            for (r in labelled.regions) {
                if (!ShadowInkClassifier.touchesImageEdge(r, width, height)) {
                    for (p in r) inner[p] = true
                    any = true
                }
            }
            detectPrintBox(if (any) inner else mask, width, height)
        }
        val pixelsPerCm = pxPerCmFor(wholePrintBox)

        // The location test uses the same box. A fold that reaches inside it is kept as uncertain
        // (the user is asked) rather than removed: missing a real ink under-quotes the job.
        val printBox = wholePrintBox

        val result = mask.copyOf()
        var removed = 0
        var lowResolution = false
        val uncertain = ArrayList<UncertainRegion>()
        labelled.regions.forEachIndexed { id, region ->
            val a = ShadowInkClassifier.classify(
                pixels, width, height, mask, labelled.labels, id, region, garmentLab, printBox, pixelsPerCm, rules, labCache
            )
            var verdict = a.verdict
            if (verdict == RegionVerdict.UNCERTAIN) {
                // The user's answer for this region, if they gave one.
                val answer = inputs.regionAnswers.firstOrNull { ans ->
                    val x = (ans.normX * width).toInt().coerceIn(0, width - 1)
                    val y = (ans.normY * height).toInt().coerceIn(0, height - 1)
                    labelled.labels[y * width + x] == id
                }
                verdict = when {
                    answer != null -> if (answer.isInk) RegionVerdict.INK else RegionVerdict.FOLD_SHADOW
                    inputs.allUncertainAreFoldShadows -> RegionVerdict.FOLD_SHADOW
                    else -> RegionVerdict.UNCERTAIN
                }
                if (a.reason == UncertainReason.LOW_RESOLUTION) lowResolution = true
            }
            when (verdict) {
                RegionVerdict.FOLD_SHADOW -> {
                    for (p in region) result[p] = false
                    removed++
                }
                RegionVerdict.UNCERTAIN -> {
                    val first = region.minOrNull()!!
                    uncertain.add(
                        UncertainRegion(region, Pair((first % width + 0.5f) / width, (first / width + 0.5f) / height))
                    )
                }
                RegionVerdict.INK -> Unit
            }
        }
        return FoldShadowCheck(result, removed, lowResolution, uncertain)
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
        maskRules: GarmentMaskRules = GarmentMaskRules(),
        // print_rules.json artwork_background.border_share_min: when one colour holds at least this
        // share of the border, that colour is the fabric (or background), whatever the preset says.
        uniformBorderShareMin: Double? = null
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
        val borderLabCache = HashMap<Int, ColorConverter.Lab>(4096)

        for (y in 0 until height) {
            val rowOffset = y * width
            val isYBorder = (y < margin || y >= height - margin)
            for (x in 0 until width) {
                if (isYBorder || x < margin || x >= width - margin) {
                    val p = pixels[rowOffset + x]
                    val alpha = (p shr 24) and 0xFF
                    if (alpha >= 128) {
                        val key = p and 0xFFFFFF
                        borderLabs.add(borderLabCache.getOrPut(key) {
                            ColorConverter.rgbToLab((key shr 16) and 0xFF, (key shr 8) and 0xFF, key and 0xFF)
                        })
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
        // Compute spread = median deltaE00 of border pixels to median Lab
        val deltaArray = DoubleArray(borderLabs.size)
        parallelFor(borderLabs.size) { i -> deltaArray[i] = ColorConverter.deltaE00(borderLabs[i], medLab) }
        val deltas = deltaArray.sorted()
        val spread = deltas[deltas.size / 2].coerceIn(1.0, 5.0)
        // Threshold adapts to the fabric spread (garment_mask.spread_multiplier, min_threshold_de00).
        val adaptiveThreshold = max(maskRules.minThresholdDeltaE00, spread * maskRules.spreadMultiplier)

        // A border held by one colour (a pink jersey, a paper background) is the fabric, even when it
        // differs from the preset; the preset is only an initial guess (garment_mask.estimate_from).
        val borderIsOneColour = uniformBorderShareMin != null &&
            deltaArray.count { it <= adaptiveThreshold }.toDouble() / deltaArray.size >= uniformBorderShareMin
        if (preferredHex != null && !borderIsOneColour) {
            val guessRgb = ColorConverter.hexToRgb(preferredHex)
            val guessLab = ColorConverter.rgbToLab(guessRgb.r, guessRgb.g, guessRgb.b)
            if (hypot(medA - guessLab.a, medB - guessLab.b) >= 12.0) {
                return GarmentProfile(preferredHex, guessLab, 2.0, 5.0, false)
            }
        }

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
        val labs = labByColour(pixels)

        parallelFor(gridDim * gridDim) { cell ->
            val gy = cell / gridDim
            val gx = cell % gridDim
            val startY = gy * blockH
            val endY = min(height, startY + blockH)
            val startX = gx * blockW
            val endX = min(width, startX + blockW)

            val blockLs = ArrayList<Double>()
            for (y in startY until endY) {
                val row = y * width
                for (x in startX until endX) {
                    val lab = labs.getValue(pixels[row + x] and 0xFFFFFF)
                    if (ColorConverter.deltaE00(lab, garmentLab) <= spread * 2.5) {
                        blockLs.add(lab.l)
                    }
                }
            }
            if (blockLs.size >= 8) {
                field[gy][gx] = blockLs.sorted()[blockLs.size / 2]
            }
        }

        val result = IntArray(pixels.size)
        parallelFor(height) { y ->
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
                    val lab = labs.getValue(pixels[row + x] and 0xFFFFFF)
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
        parallelFor(height) { y ->
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
        parallelFor(height) { y ->
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

        // Distances depend only on the colour, so each one is computed once per distinct colour
        // and copied to every point of that colour. The values and their order are unchanged.
        val distinct = DistinctLab.of(points)
        val uniq = distinct.colours
        val uniqIndex = distinct.indexOfPoint

        // K-Means++ Initialization
        centers.add(points[random.nextInt(n)])

        val distances = DoubleArray(n)
        val uniqDistances = DoubleArray(uniq.size)
        for (step in 1 until actualK) {
            val currentCenters = ArrayList(centers)
            parallelFor(uniq.size) { u ->
                var minD = Double.MAX_VALUE
                val pt = uniq[u]
                for (c in currentCenters) {
                    val d = ColorConverter.deltaE00(pt, c)
                    if (d < minD) minD = d
                }
                uniqDistances[u] = minD * minD
            }
            for (i in 0 until n) distances[i] = uniqDistances[uniqIndex[i]]
            var sumDist = 0.0
            for (i in 0 until n) sumDist += distances[i] // same order as before

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

            val iterationCenters = ArrayList(centers)
            val newAssignments = IntArray(n)
            val uniqBest = IntArray(uniq.size)
            parallelFor(uniq.size) { u ->
                val pt = uniq[u]
                var bestCluster = 0
                var minDist = Double.MAX_VALUE
                for (c in 0 until actualK) {
                    val d = ColorConverter.deltaE00(pt, iterationCenters[c])
                    if (d < minDist) {
                        minDist = d
                        bestCluster = c
                    }
                }
                uniqBest[u] = bestCluster
            }
            for (i in 0 until n) newAssignments[i] = uniqBest[uniqIndex[i]]
            for (i in 0 until n) {
                if (assignments[i] != newAssignments[i]) {
                    assignments[i] = newAssignments[i]
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

        // The nearest centre depends only on the colour, so it is found once per distinct colour.
        val firstIndexOfColour = LinkedHashMap<Int, Int>(4096)
        for (i in 0 until n) firstIndexOfColour.putIfAbsent(pixelsRgb[i] and 0x00FFFFFF, i)
        val colours = firstIndexOfColour.keys.toIntArray()
        val firstIndex = firstIndexOfColour.values.toIntArray()
        val best = IntArray(colours.size)
        parallelFor(colours.size) { u ->
            val pt = pixelsLab[firstIndex[u]]
            var bestC = 0
            var minDist = Double.MAX_VALUE
            for (c in 0 until k) {
                val dist = ColorConverter.deltaE00(pt, centers[c])
                if (dist < minDist) {
                    minDist = dist
                    bestC = c
                }
            }
            best[u] = bestC
        }
        val bestByColour = HashMap<Int, Int>(colours.size * 2)
        for (u in colours.indices) bestByColour[colours[u]] = best[u]
        for (i in 0 until n) result[i] = bestByColour.getValue(pixelsRgb[i] and 0x00FFFFFF)
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
        maskRules: GarmentMaskRules = GarmentMaskRules(),
        // Part A's measured fabric: Parts B and C use the same fabric as Part A.
        garmentProfile: GarmentProfile? = null
    ): BooleanArray {
        val totalPixels = width * height
        val isPrintMask = BooleanArray(totalPixels)
        if (totalPixels == 0 || pixels.size != totalPixels) return isPrintMask

        val profile = garmentProfile
            ?: estimateGarmentProfile(pixels, width, height, inputs.userGarmentHex, inputs.garmentColorHex, maskRules)
        val garmentLab = profile.medianLab
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
                    val garmentFlag = ColorConverter.deltaE00(computedLab, garmentLab) <= effectiveThreshold
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
        printBox: NormalizedRect? = null,
        photoEdgeExcluded: BooleanArray? = null
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
                    } else if (photoEdgeExcluded?.get(index) == true) {
                        // Table, floor or wall left out of the print: shaded with diagonal stripes.
                        outputPixels[index] = if (((x + y) / 8) % 2 == 0) 0xFF5A5F6B.toInt() else 0xFF3E424C.toInt()
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
