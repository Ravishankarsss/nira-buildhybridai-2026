package com.mos.nira.core.image

import android.graphics.Bitmap
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import com.mos.nira.features.artwork.domain.PrintColorConfig
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic color extractor using CIEDE2000 clustering, edge filtering,
 * and adaptive cluster merging.
 *
 * Stage 1 Requirements:
 * 1. Auto-detect real number of print colours: over-cluster (K = 14..18), then merge
 *    clusters closer than a CIEDE2000 threshold (default ~3.2 ΔE00).
 * 2. Drop colours below minimum coverage % (e.g. 0.5% speckle noise).
 * 3. Ignore anti-aliased edge pixels between two distinct color regions during clustering
 *    to prevent synthetic blending artifacts from being identified as independent inks.
 * 4. Exclude garment/base colour when specified.
 * 5. Respect maxScreens constraint.
 */
object ColorExtractor {

    private const val MAX_PROCESSING_DIMENSION = 300
    private const val OVERCLUSTER_K = 16
    private const val MAX_KMEANS_ITERATIONS = 20
    private const val EDGE_GRADIENT_THRESHOLD = 25 // Color distance threshold to flag an edge pixel

    data class ExtractionResult(
        val palette: List<DominantColor>,
        val colorGroups: List<ColorGroup>,
        val totalValidPixels: Int,
        val excludedGarmentPixels: Int = 0
    )

    /**
     * Extracts print colors using over-clustering and CIEDE2000 merging.
     */
    fun extractPalette(
        sourceBitmap: Bitmap,
        config: PrintColorConfig = PrintColorConfig(),
        seed: Long = 42L
    ): ExtractionResult {
        // Step 1: Scale down safely for memory & CPU efficiency (max 300px)
        val scaledBitmap = createAnalysisBitmap(sourceBitmap, MAX_PROCESSING_DIMENSION)
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val totalPixels = width * height

        val pixels = IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (scaledBitmap != sourceBitmap) {
            scaledBitmap.recycle()
        }

        // Step 2: Edge detection mask to flag anti-aliased edge transition pixels
        val isEdgePixel = detectAntiAliasedEdges(pixels, width, height)

        // Step 3: Filter transparent pixels and edge pixels for the cluster core
        val coreRgbList = ArrayList<Int>(totalPixels)
        val coreLabList = ArrayList<ColorConverter.Lab>(totalPixels)
        val allValidRgbList = ArrayList<Int>(totalPixels)
        val allValidLabList = ArrayList<ColorConverter.Lab>(totalPixels)

        val garmentLab = config.garmentColorHex?.let {
            val rgb = ColorConverter.hexToRgb(it)
            ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
        }

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val alpha = (p shr 24) and 0xFF
            if (alpha >= 128) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lab = ColorConverter.rgbToLab(r, g, b)

                allValidRgbList.add(p)
                allValidLabList.add(lab)

                // Only feed non-edge pixels to clustering initialization and core assignment
                if (!isEdgePixel[i]) {
                    coreRgbList.add(p)
                    coreLabList.add(lab)
                }
            }
        }

        val validCount = allValidRgbList.size
        if (validCount == 0) {
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
                colorGroups = listOf(ColorGroup(0, defaultColor, listOf(defaultColor), 100.0f)),
                totalValidPixels = 0
            )
        }

        // If core pixels are too few, fall back to all valid pixels
        val trainingRgbList = if (coreRgbList.size >= OVERCLUSTER_K) coreRgbList else allValidRgbList
        val trainingLabList = if (coreLabList.size >= OVERCLUSTER_K) coreLabList else allValidLabList
        val trainingCount = trainingLabList.size

        // Step 4: Over-cluster with K-Means++
        val kInitial = min(OVERCLUSTER_K, trainingCount)
        val clusterCenters = initializeKMeansPlusPlus(trainingLabList, kInitial, seed)

        val clusterAssignments = IntArray(trainingCount)
        var iterations = 0
        var changed = true

        while (changed && iterations < MAX_KMEANS_ITERATIONS) {
            changed = false
            iterations++

            for (i in 0 until trainingCount) {
                val pixelLab = trainingLabList[i]
                var bestCluster = 0
                var minDistance = Double.MAX_VALUE

                for (c in 0 until kInitial) {
                    val center = clusterCenters[c]
                    val dist = ColorConverter.deltaE00(pixelLab, center)
                    if (dist < minDistance) {
                        minDistance = dist
                        bestCluster = c
                    }
                }

                if (clusterAssignments[i] != bestCluster) {
                    clusterAssignments[i] = bestCluster
                    changed = true
                }
            }

            val countPerCluster = IntArray(kInitial)
            val sumL = DoubleArray(kInitial)
            val sumA = DoubleArray(kInitial)
            val sumB = DoubleArray(kInitial)

            for (i in 0 until trainingCount) {
                val c = clusterAssignments[i]
                val lab = trainingLabList[i]
                countPerCluster[c]++
                sumL[c] += lab.l
                sumA[c] += lab.a
                sumB[c] += lab.b
            }

            for (c in 0 until kInitial) {
                if (countPerCluster[c] > 0) {
                    clusterCenters[c] = ColorConverter.Lab(
                        sumL[c] / countPerCluster[c],
                        sumA[c] / countPerCluster[c],
                        sumB[c] / countPerCluster[c]
                    )
                }
            }
        }

        // Step 5: Gather RGB centroids for all initial clusters
        class TempCluster(
            var centerLab: ColorConverter.Lab,
            var count: Int,
            var sumR: Long,
            var sumG: Long,
            var sumB: Long
        ) {
            val r: Int get() = if (count > 0) (sumR / count).toInt().coerceIn(0, 255) else 0
            val g: Int get() = if (count > 0) (sumG / count).toInt().coerceIn(0, 255) else 0
            val b: Int get() = if (count > 0) (sumB / count).toInt().coerceIn(0, 255) else 0
        }

        val clusters = ArrayList<TempCluster>()
        for (c in 0 until kInitial) {
            var count = 0
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (i in 0 until trainingCount) {
                if (clusterAssignments[i] == c) {
                    count++
                    val p = trainingRgbList[i]
                    sumR += ((p shr 16) and 0xFF).toLong()
                    sumG += ((p shr 8) and 0xFF).toLong()
                    sumB += (p and 0xFF).toLong()
                }
            }
            if (count > 0) {
                clusters.add(TempCluster(clusterCenters[c], count, sumR, sumG, sumB))
            }
        }

        // Step 6: Iteratively merge clusters closer than CIEDE2000 threshold
        val mergeThreshold = config.mergeThresholdDeltaE00
        var mergedAny = true
        while (mergedAny && clusters.size > 1) {
            mergedAny = false
            var bestI = -1
            var bestJ = -1
            var minMergeDist = Double.MAX_VALUE

            for (i in 0 until clusters.size) {
                for (j in (i + 1) until clusters.size) {
                    val dE00 = ColorConverter.deltaE00(clusters[i].centerLab, clusters[j].centerLab)
                    if (dE00 < mergeThreshold && dE00 < minMergeDist) {
                        minMergeDist = dE00
                        bestI = i
                        bestJ = j
                    }
                }
            }

            if (bestI != -1 && bestJ != -1) {
                val c1 = clusters[bestI]
                val c2 = clusters[bestJ]
                val mergedCount = c1.count + c2.count
                val mergedSumR = c1.sumR + c2.sumR
                val mergedSumG = c1.sumG + c2.sumG
                val mergedSumB = c1.sumB + c2.sumB

                val avgR = (mergedSumR / mergedCount).toInt().coerceIn(0, 255)
                val avgG = (mergedSumG / mergedCount).toInt().coerceIn(0, 255)
                val avgB = (mergedSumB / mergedCount).toInt().coerceIn(0, 255)
                val mergedLab = ColorConverter.rgbToLab(avgR, avgG, avgB)

                c1.centerLab = mergedLab
                c1.count = mergedCount
                c1.sumR = mergedSumR
                c1.sumG = mergedSumG
                c1.sumB = mergedSumB

                clusters.removeAt(bestJ)
                mergedAny = true
            }
        }

        // Step 7: Assign ALL valid pixels (including edge pixels) to the nearest remaining cluster
        val finalCounts = IntArray(clusters.size)
        val finalSumR = LongArray(clusters.size)
        val finalSumG = LongArray(clusters.size)
        val finalSumB = LongArray(clusters.size)

        for (i in 0 until validCount) {
            val lab = allValidLabList[i]
            val p = allValidRgbList[i]
            var bestIdx = 0
            var minDist = Double.MAX_VALUE

            for (c in clusters.indices) {
                val dist = ColorConverter.deltaE00(lab, clusters[c].centerLab)
                if (dist < minDist) {
                    minDist = dist
                    bestIdx = c
                }
            }

            finalCounts[bestIdx]++
            finalSumR[bestIdx] += ((p shr 16) and 0xFF).toLong()
            finalSumG[bestIdx] += ((p shr 8) and 0xFF).toLong()
            finalSumB[bestIdx] += (p and 0xFF).toLong()
        }

        // Update cluster centroids based on full pixel assignments
        for (c in clusters.indices) {
            clusters[c].count = finalCounts[c]
            clusters[c].sumR = finalSumR[c]
            clusters[c].sumG = finalSumG[c]
            clusters[c].sumB = finalSumB[c]
            if (clusters[c].count > 0) {
                clusters[c].centerLab = ColorConverter.rgbToLab(clusters[c].r, clusters[c].g, clusters[c].b)
            }
        }

        // Step 8: Separate garment base color (if specified) and drop low coverage noise
        var excludedGarmentPixels = 0
        val remainingClusters = ArrayList<TempCluster>()

        for (c in clusters) {
            val percentage = (c.count.toFloat() / validCount.toFloat()) * 100.0f
            if (garmentLab != null && ColorConverter.deltaE00(c.centerLab, garmentLab) < 5.0) {
                excludedGarmentPixels += c.count
            } else if (percentage >= config.minCoveragePercentage) {
                remainingClusters.add(c)
            }
        }

        // If all colors were filtered out, preserve the largest one
        if (remainingClusters.isEmpty() && clusters.isNotEmpty()) {
            remainingClusters.add(clusters.maxByOrNull { it.count }!!)
        }

        // Step 9: Enforce maxScreens (merge closest clusters until <= maxScreens)
        while (remainingClusters.size > config.maxScreens) {
            var bestI = -1
            var bestJ = -1
            var minMergeDist = Double.MAX_VALUE

            for (i in 0 until remainingClusters.size) {
                for (j in (i + 1) until remainingClusters.size) {
                    val dE00 = ColorConverter.deltaE00(remainingClusters[i].centerLab, remainingClusters[j].centerLab)
                    if (dE00 < minMergeDist) {
                        minMergeDist = dE00
                        bestI = i
                        bestJ = j
                    }
                }
            }

            if (bestI != -1 && bestJ != -1) {
                val c1 = remainingClusters[bestI]
                val c2 = remainingClusters[bestJ]
                val mergedCount = c1.count + c2.count
                val mergedSumR = c1.sumR + c2.sumR
                val mergedSumG = c1.sumG + c2.sumG
                val mergedSumB = c1.sumB + c2.sumB

                val avgR = (mergedSumR / mergedCount).toInt().coerceIn(0, 255)
                val avgG = (mergedSumG / mergedCount).toInt().coerceIn(0, 255)
                val avgB = (mergedSumB / mergedCount).toInt().coerceIn(0, 255)
                val mergedLab = ColorConverter.rgbToLab(avgR, avgG, avgB)

                c1.centerLab = mergedLab
                c1.count = mergedCount
                c1.sumR = mergedSumR
                c1.sumG = mergedSumG
                c1.sumB = mergedSumB

                remainingClusters.removeAt(bestJ)
            } else {
                break
            }
        }

        // Sort by descending pixel count
        remainingClusters.sortByDescending { it.count }

        // Recalculate percentages relative to valid printing pixels
        val printPixelsCount = validCount - excludedGarmentPixels
        val totalDenom = if (printPixelsCount > 0) printPixelsCount.toFloat() else validCount.toFloat()

        val finalPalette = remainingClusters.mapIndexed { index, c ->
            val percentage = (c.count.toFloat() / totalDenom) * 100.0f
            DominantColor(
                id = index,
                hex = ColorConverter.rgbToHex(c.r, c.g, c.b),
                r = c.r,
                g = c.g,
                b = c.b,
                labL = c.centerLab.l,
                labA = c.centerLab.a,
                labB = c.centerLab.b,
                pixelCount = c.count,
                pixelPercentage = percentage,
                groupId = index
            )
        }

        val colorGroups = groupSimilarColors(finalPalette)

        return ExtractionResult(
            palette = finalPalette,
            colorGroups = colorGroups,
            totalValidPixels = validCount,
            excludedGarmentPixels = excludedGarmentPixels
        )
    }

    /**
     * Flags anti-aliased edge transition pixels between two colors using local gradient magnitude.
     */
    private fun detectAntiAliasedEdges(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val isEdge = BooleanArray(pixels.size)
        for (y in 1 until (height - 1)) {
            for (x in 1 until (width - 1)) {
                val idx = y * width + x
                val p = pixels[idx]
                if (((p shr 24) and 0xFF) < 128) continue

                val pRight = pixels[idx + 1]
                val pDown = pixels[idx + width]

                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val rR = (pRight shr 16) and 0xFF
                val gR = (pRight shr 8) and 0xFF
                val bR = pRight and 0xFF

                val rD = (pDown shr 16) and 0xFF
                val gD = (pDown shr 8) and 0xFF
                val bD = pDown and 0xFF

                val gradX = abs(r - rR) + abs(g - gR) + abs(b - bR)
                val gradY = abs(r - rD) + abs(g - gD) + abs(b - bD)

                if (gradX > EDGE_GRADIENT_THRESHOLD || gradY > EDGE_GRADIENT_THRESHOLD) {
                    isEdge[idx] = true
                }
            }
        }
        return isEdge
    }

    /**
     * Groups colors that have CIEDE2000 < 5.0 into visual families.
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
                    val distance = ColorConverter.deltaE00(primaryLab, candidateLab)

                    if (distance <= 5.0) {
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

        val firstIdx = rand.nextInt(labList.size)
        centers.add(labList[firstIdx])

        val distancesSq = DoubleArray(labList.size) { Double.MAX_VALUE }

        while (centers.size < k) {
            val lastCenter = centers.last()
            var sumDistancesSq = 0.0

            for (i in labList.indices) {
                val lab = labList[i]
                val d = ColorConverter.deltaE00(lab, lastCenter)
                val dSq = d * d

                if (dSq < distancesSq[i]) {
                    distancesSq[i] = dSq
                }
                sumDistancesSq += distancesSq[i]
            }

            if (sumDistancesSq == 0.0) {
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
