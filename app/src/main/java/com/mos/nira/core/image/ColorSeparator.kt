package com.mos.nira.core.image

import android.graphics.Bitmap
import android.graphics.Color
import com.mos.nira.features.artwork.domain.DominantColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Technical Color Separator for digital color separation previews.
 *
 * Runs separation at up to 1024px on the longest dimension for high visual fidelity
 * while remaining memory-safe. Uses CIEDE2000 for deterministic nearest-cluster assignment.
 */
object ColorSeparator {

    const val SEPARATION_MAX_DIMENSION = 1024

    data class SeparationLayer(
        val dominantColor: DominantColor,
        val maskBitmap: Bitmap,        // Monochrome/alpha mask of the layer
        val tintedBitmap: Bitmap,      // Colorized visual representation of the layer
        val pixelCoverage: Float       // Percentage coverage
    )

    data class SeparationResult(
        val layers: List<SeparationLayer>,
        val compositeBitmap: Bitmap,   // Composite re-combination of all layers
        val unassignedCoverage: Float = 0f
    )

    /**
     * Generates digital separation layers for each color in [palette].
     *
     * @param sourceBitmap Input artwork bitmap
     * @param palette Target print color palette
     * @param garmentColor Optional base fabric color; matching pixels are excluded from ink layers
     * @param maxDimension Processing resolution limit (default 1024px)
     */
    fun separateColors(
        sourceBitmap: Bitmap,
        palette: List<DominantColor>,
        garmentColor: DominantColor? = null,
        maxDimension: Int = SEPARATION_MAX_DIMENSION
    ): SeparationResult {
        if (palette.isEmpty()) {
            val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            return SeparationResult(emptyList(), emptyBitmap)
        }

        // Scale to max 1024px for sharp high-fidelity layer inspection
        val scaled = createScaled(sourceBitmap, maxDimension)
        val width = scaled.width
        val height = scaled.height
        val totalPixels = width * height

        val srcPixels = IntArray(totalPixels)
        scaled.getPixels(srcPixels, 0, width, 0, 0, width, height)

        if (scaled != sourceBitmap) {
            scaled.recycle()
        }

        val paletteLabs = palette.map { ColorConverter.Lab(it.labL, it.labA, it.labB) }
        val garmentLab = garmentColor?.let { ColorConverter.Lab(it.labL, it.labA, it.labB) }
        val layerPixelCounts = IntArray(palette.size)
        var garmentOrTransparentCount = 0

        // Maps each pixel to its closest palette color index (-1 if transparent or garment)
        val pixelAssignments = IntArray(totalPixels)

        for (i in 0 until totalPixels) {
            val p = srcPixels[i]
            val alpha = (p shr 24) and 0xFF
            if (alpha < 64) {
                pixelAssignments[i] = -1
                garmentOrTransparentCount++
                continue
            }

            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val pixelLab = ColorConverter.rgbToLab(r, g, b)

            // If closer to garment background color than any palette ink (within threshold), skip
            if (garmentLab != null) {
                val dGarment = ColorConverter.deltaE00(pixelLab, garmentLab)
                if (dGarment < 5.0) {
                    var closerToPalette = false
                    for (c in paletteLabs) {
                        if (ColorConverter.deltaE00(pixelLab, c) < dGarment) {
                            closerToPalette = true
                            break
                        }
                    }
                    if (!closerToPalette) {
                        pixelAssignments[i] = -1
                        garmentOrTransparentCount++
                        continue
                    }
                }
            }

            var bestIdx = 0
            var minDistance = Double.MAX_VALUE

            for (c in paletteLabs.indices) {
                val dist = ColorConverter.deltaE00(pixelLab, paletteLabs[c])
                if (dist < minDistance) {
                    minDistance = dist
                    bestIdx = c
                }
            }

            pixelAssignments[i] = bestIdx
            layerPixelCounts[bestIdx]++
        }

        // Generate individual layers
        val layers = ArrayList<SeparationLayer>(palette.size)
        val compositePixels = IntArray(totalPixels)

        for (c in palette.indices) {
            val color = palette[c]
            val maskPixels = IntArray(totalPixels)
            val tintedPixels = IntArray(totalPixels)
            val tintedRgb = Color.rgb(color.r, color.g, color.b)

            for (i in 0 until totalPixels) {
                if (pixelAssignments[i] == c) {
                    maskPixels[i] = -0x1 // 0xFFFFFFFF (opaque white on transparent)
                    tintedPixels[i] = tintedRgb or (0xFF shl 24)
                    compositePixels[i] = tintedRgb or (0xFF shl 24)
                } else {
                    maskPixels[i] = 0x00000000
                    tintedPixels[i] = 0x00000000
                }
            }

            val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            maskBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height)

            val tintedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            tintedBitmap.setPixels(tintedPixels, 0, width, 0, 0, width, height)

            val coverage = if (totalPixels > 0) {
                (layerPixelCounts[c].toFloat() / totalPixels.toFloat()) * 100.0f
            } else {
                0.0f
            }

            layers.add(
                SeparationLayer(
                    dominantColor = color,
                    maskBitmap = maskBitmap,
                    tintedBitmap = tintedBitmap,
                    pixelCoverage = coverage
                )
            )
        }

        val compositeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        compositeBitmap.setPixels(compositePixels, 0, width, 0, 0, width, height)

        val unassignedCoverage = if (totalPixels > 0) {
            (garmentOrTransparentCount.toFloat() / totalPixels.toFloat()) * 100.0f
        } else 0f

        return SeparationResult(layers, compositeBitmap, unassignedCoverage)
    }

    private fun createScaled(source: Bitmap, maxDim: Int): Bitmap {
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
