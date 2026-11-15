package com.mos.nira.core.image

import android.graphics.Bitmap
import android.graphics.Color
import com.mos.nira.features.artwork.domain.DominantColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Technical Color Separator for preliminary digital color separation previews.
 *
 * NOTE: This produces preliminary digital separation masks for design visualization.
 * It does not produce production halftone films or mechanical print plates.
 */
object ColorSeparator {

    private const val SEPARATION_MAX_DIMENSION = 400

    data class SeparationLayer(
        val dominantColor: DominantColor,
        val maskBitmap: Bitmap,        // Monochrome/alpha mask of the layer
        val tintedBitmap: Bitmap,      // Colorized visual representation of the layer
        val pixelCoverage: Float       // Percentage coverage
    )

    data class SeparationResult(
        val layers: List<SeparationLayer>,
        val compositeBitmap: Bitmap    // Composite re-combination of all layers
    )

    /**
     * Generates digital separation layers for each dominant color in [palette].
     */
    fun separateColors(
        sourceBitmap: Bitmap,
        palette: List<DominantColor>
    ): SeparationResult {
        if (palette.isEmpty()) {
            val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            return SeparationResult(emptyList(), emptyBitmap)
        }

        // Scale to a safe dimension for separation preview
        val scaled = createScaled(sourceBitmap, SEPARATION_MAX_DIMENSION)
        val width = scaled.width
        val height = scaled.height
        val totalPixels = width * height

        val srcPixels = IntArray(totalPixels)
        scaled.getPixels(srcPixels, 0, width, 0, 0, width, height)

        if (scaled != sourceBitmap) {
            scaled.recycle()
        }

        // Precompute Lab coordinates for palette colors
        val paletteLabs = palette.map { ColorConverter.Lab(it.labL, it.labA, it.labB) }
        val layerPixelCounts = IntArray(palette.size)

        // Maps each pixel to its closest palette color index (or -1 if transparent)
        val pixelAssignments = IntArray(totalPixels)

        for (i in 0 until totalPixels) {
            val p = srcPixels[i]
            val alpha = (p shr 24) and 0xFF
            if (alpha < 64) {
                pixelAssignments[i] = -1
                continue
            }

            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val pixelLab = ColorConverter.rgbToLab(r, g, b)

            var bestIdx = 0
            var minDistanceSq = Double.MAX_VALUE

            for (c in paletteLabs.indices) {
                val center = paletteLabs[c]
                val dl = pixelLab.l - center.l
                val da = pixelLab.a - center.a
                val db = pixelLab.b - center.b
                val dSq = dl * dl + da * da + db * db

                if (dSq < minDistanceSq) {
                    minDistanceSq = dSq
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
                    // Opaque white on transparent black mask
                    maskPixels[i] = -0x1 // 0xFFFFFFFF
                    // Tinted color layer
                    tintedPixels[i] = tintedRgb or (0xFF shl 24)
                    // Composite reconstruction
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

        return SeparationResult(layers, compositeBitmap)
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
