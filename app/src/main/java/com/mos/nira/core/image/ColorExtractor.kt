package com.mos.nira.core.image

import android.graphics.Bitmap
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic color extractor using K-Means++ clustering in CIE L*a*b* color space.
 *
 * Steps:
 * 1. Downscales bitmap to a memory-safe resolution for analysis (e.g. max 300x300).
 * 2. Filters transparent and semi-transparent pixels (alpha < 128).
 * 3. Initializes cluster centers with deterministic K-Means++ in Lab space.
 * 4. Runs iterative K-Means clustering until convergence or max iterations reached.
 * 5. Computes cluster percentages and removes low-presence noise clusters.
 * 6. Groups perceptually similar colors using CIE Delta E.
 */
object ColorExtractor {

    private const val MAX_PROCESSING_DIMENSION = 300
    private const val MAX_KMEANS_ITERATIONS = 20
    private const val DELTA_E_SIMILARITY_THRESHOLD = 9.0 // Perceptually close colors for grouping

    data class ExtractionResult(
        val palette: List<DominantColor>,
        val colorGroups: List<ColorGroup>,
        val totalValidPixels: Int
    )

    /**
     * Extracts dominant colors from [sourceBitmap].
     *
     * @param sourceBitmap The input image bitmap.
     * @param targetPaletteSize Desired number of dominant clusters (clamped to 3..16, default 8).
     * @param seed Random seed for deterministic reproducibility.
     */
    fun extractPalette(
        sourceBitmap: Bitmap,
        targetPaletteSize: Int = 8,
        seed: Long = 42L
    ): ExtractionResult {
        val clampedK = targetPaletteSize.coerceIn(3, 16)

        // Step 1: Scale down safely for memory & CPU efficiency
        val scaledBitmap = createAnalysisBitmap(sourceBitmap, MAX_PROCESSING_DIMENSION)
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val totalPixels = width * height

        val pixels = IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (scaledBitmap != sourceBitmap) {
            scaledBitmap.recycle()
        }

        // Step 2: Filter transparent pixels and prepare RGB/Lab buffers
        val validRgbList = ArrayList<Int>(totalPixels)
        val validLabList = ArrayList<ColorConverter.Lab>(totalPixels)

        for (p in pixels) {
            val alpha = (p shr 24) and 0xFF
            if (alpha >= 128) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                validRgbList.add(p)
                validLabList.add(ColorConverter.rgbToLab(r, g, b))
            }
        }

        val validCount = validRgbList.size
        if (validCount == 0) {
            // Fallback for completely transparent image
            val defaultColor = DominantColor(
                id = 0,
                hex = "#FFFFFF",
                r = 255,
                g = 255,
                b = 255,
                labL = 100.0,
                labA = 0.0,
                labB = 0.0,
                pixelCount = 0,
                pixelPercentage = 100.0f
            )
            return ExtractionResult(
                palette = listOf(defaultColor),
                colorGroups = listOf(
                    ColorGroup(0, defaultColor, listOf(defaultColor), 100.0f)
                ),
                totalValidPixels = 0
            )
        }

        // If unique colors are fewer than K, adapt K
        val k = min(clampedK, validCount)

        // Step 3: Deterministic K-Means++ initialization
        val clusterCenters = initializeKMeansPlusPlus(validLabList, k, seed)

        // Step 4: Iterative K-Means in Lab space
        val clusterAssignments = IntArray(validCount)
        var iterations = 0
        var changed = true

        while (changed && iterations < MAX_KMEANS_ITERATIONS) {
            changed = false
            iterations++

            // Assign each pixel to nearest cluster center
            for (i in 0 until validCount) {
                val pixelLab = validLabList[i]
                var bestCluster = 0
                var minDistanceSq = Double.MAX_VALUE

                for (c in 0 until k) {
                    val center = clusterCenters[c]
                    val dl = pixelLab.l - center.l
                    val da = pixelLab.a - center.a
                    val db = pixelLab.b - center.b
                    val distSq = dl * dl + da * da + db * db

                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        bestCluster = c
                    }
                }

                if (clusterAssignments[i] != bestCluster) {
                    clusterAssignments[i] = bestCluster
                    changed = true
                }
            }

            // Recompute cluster centers
            val countPerCluster = IntArray(k)
            val sumL = DoubleArray(k)
            val sumA = DoubleArray(k)
            val sumB = DoubleArray(k)

            for (i in 0 until validCount) {
                val c = clusterAssignments[i]
                val lab = validLabList[i]
                countPerCluster[c]++
                sumL[c] += lab.l
                sumA[c] += lab.a
                sumB[c] += lab.b
            }

            for (c in 0 until k) {
                if (countPerCluster[c] > 0) {
                    clusterCenters[c] = ColorConverter.Lab(
                        sumL[c] / countPerCluster[c],
                        sumA[c] / countPerCluster[c],
                        sumB[c] / countPerCluster[c]
                    )
                }
            }
        }

        // Step 5: Gather cluster RGB representatives & counts
        val countPerCluster = IntArray(k)
        val sumR = LongArray(k)
        val sumG = LongArray(k)
        val sumB = LongArray(k)

        for (i in 0 until validCount) {
            val c = clusterAssignments[i]
            val pixel = validRgbList[i]
            countPerCluster[c]++
            sumR[c] += ((pixel shr 16) and 0xFF).toLong()
            sumG[c] += ((pixel shr 8) and 0xFF).toLong()
            sumB[c] += (pixel and 0xFF).toLong()
        }

        // Build DominantColor list, sorted by descending pixel count
        val rawPalette = ArrayList<DominantColor>()
        for (c in 0 until k) {
            val count = countPerCluster[c]
            if (count > 0) {
                val avgR = (sumR[c] / count).toInt().coerceIn(0, 255)
                val avgG = (sumG[c] / count).toInt().coerceIn(0, 255)
                val avgB = (sumB[c] / count).toInt().coerceIn(0, 255)
                val lab = ColorConverter.rgbToLab(avgR, avgG, avgB)
                val percentage = (count.toFloat() / validCount.toFloat()) * 100.0f

                rawPalette.add(
                    DominantColor(
                        id = c,
                        hex = ColorConverter.rgbToHex(avgR, avgG, avgB),
                        r = avgR,
                        g = avgG,
                        b = avgB,
                        labL = lab.l,
                        labA = lab.a,
                        labB = lab.b,
                        pixelCount = count,
                        pixelPercentage = percentage
                    )
                )
            }
        }

        rawPalette.sortByDescending { it.pixelCount }

        // Re-index IDs sequentially
        val sortedPalette = rawPalette.mapIndexed { index, color ->
            color.copy(id = index, groupId = index)
        }

        // Step 6: Identify and group perceptually similar colors using Delta E
        val colorGroups = groupSimilarColors(sortedPalette)

        return ExtractionResult(
            palette = sortedPalette,
            colorGroups = colorGroups,
            totalValidPixels = validCount
        )
    }

    /**
     * Groups colors that have Delta E < DELTA_E_SIMILARITY_THRESHOLD.
     */
    private fun groupSimilarColors(palette: List<DominantColor>): List<ColorGroup> {
        val assigned = BooleanArray(palette.size)
        val groups = ArrayList<ColorGroup>()

        for (i in palette.indices) {
            if (assigned[i]) continue

            val primary = palette[i]
            val members = ArrayList<DominantColor>()
            members.add(primary)
            assigned[i] = true

            val primaryLab = ColorConverter.Lab(primary.labL, primary.labA, primary.labB)
            var totalPercentage = primary.pixelPercentage

            for (j in (i + 1) until palette.size) {
                if (!assigned[j]) {
                    val candidate = palette[j]
                    val candidateLab = ColorConverter.Lab(candidate.labL, candidate.labA, candidate.labB)
                    val distance = ColorConverter.deltaE(primaryLab, candidateLab)

                    if (distance <= DELTA_E_SIMILARITY_THRESHOLD) {
                        members.add(candidate)
                        assigned[j] = true
                        totalPercentage += candidate.pixelPercentage
                    }
                }
            }

            groups.add(
                ColorGroup(
                    groupId = groups.size,
                    primaryColor = primary,
                    members = members,
                    totalPercentage = totalPercentage
                )
            )
        }

        return groups
    }

    private fun initializeKMeansPlusPlus(
        labList: List<ColorConverter.Lab>,
        k: Int,
        seed: Long
    ): Array<ColorConverter.Lab> {
        val rand = Random(seed)
        val centers = ArrayList<ColorConverter.Lab>(k)

        // 1. Pick first center randomly
        val firstIdx = rand.nextInt(labList.size)
        centers.add(labList[firstIdx])

        val distancesSq = DoubleArray(labList.size) { Double.MAX_VALUE }

        // 2. Pick remaining centers proportional to D(x)^2
        while (centers.size < k) {
            val lastCenter = centers.last()
            var sumDistancesSq = 0.0

            for (i in labList.indices) {
                val lab = labList[i]
                val dl = lab.l - lastCenter.l
                val da = lab.a - lastCenter.a
                val db = lab.b - lastCenter.b
                val dSq = dl * dl + da * da + db * db

                if (dSq < distancesSq[i]) {
                    distancesSq[i] = dSq
                }
                sumDistancesSq += distancesSq[i]
            }

            if (sumDistancesSq == 0.0) {
                // If all distances are 0, pick a random point
                centers.add(labList[rand.nextInt(labList.size)])
            } else {
                val targetThreshold = rand.nextDouble() * sumDistancesSq
                var cumulative = 0.0
                var selectedIdx = labList.lastIndex

                for (i in labList.indices) {
                    cumulative += distancesSq[i]
                    if (cumulative >= targetThreshold) {
                        selectedIdx = i
                        break
                    }
                }
                centers.add(labList[selectedIdx])
            }
        }

        return centers.toTypedArray()
    }

    private fun createAnalysisBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height

        if (width <= maxDim && height <= maxDim) {
            return source
        }

        val scale = min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = max(1, (width * scale).roundToInt())
        val targetHeight = max(1, (height * scale).roundToInt())

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
