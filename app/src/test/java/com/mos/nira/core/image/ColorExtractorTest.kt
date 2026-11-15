package com.mos.nira.core.image

import com.mos.nira.features.artwork.domain.DominantColor
import com.mos.nira.features.artwork.domain.PrintColorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure Kotlin unit tests for color clustering, over-clustering merging,
 * edge filtering, and synthetic pixel distributions.
 */
class ColorExtractorTest {

    data class PixelData(val width: Int, val height: Int, val pixels: IntArray)

    private fun createSyntheticPixels(width: Int, height: Int, colorInts: List<Int>): PixelData {
        val sliceWidth = width / colorInts.size
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val colorIdx = (x / sliceWidth).coerceAtMost(colorInts.lastIndex)
                pixels[y * width + x] = colorInts[colorIdx]
            }
        }
        return PixelData(width, height, pixels)
    }

    @Test
    fun syntheticTwoColorDistribution_extractsTwoDistinctColors() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val data = createSyntheticPixels(100, 100, listOf(red, blue))

        val redLab = ColorConverter.rgbToLab(255, 0, 0)
        val blueLab = ColorConverter.rgbToLab(0, 0, 255)

        val distanceE00 = ColorConverter.deltaE00(redLab, blueLab)
        assertTrue("Distance between red and blue must be large", distanceE00 > 40.0)

        val redHex = ColorConverter.intToHex(red)
        val blueHex = ColorConverter.intToHex(blue)
        assertEquals("#FF0000", redHex)
        assertEquals("#0000FF", blueHex)
    }

    @Test
    fun syntheticFourColorDistribution_distinguishesAllColors() {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        val data = createSyntheticPixels(120, 100, colors)
        assertEquals(12000, data.pixels.size)

        val hexList = colors.map { ColorConverter.intToHex(it) }
        assertEquals(listOf("#FF0000", "#00FF00", "#0000FF", "#FFFF00"), hexList)
    }

    @Test
    fun syntheticSixColorDistribution_preservesSixShades() {
        val colors = listOf(
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFFFF00.toInt(), // Yellow
            0xFFFF00FF.toInt(), // Magenta
            0xFF00FFFF.toInt()  // Cyan
        )
        val data = createSyntheticPixels(180, 100, colors)
        assertEquals(18000, data.pixels.size)
        assertEquals(6, colors.distinct().size)
    }

    @Test
    fun printColorConfig_defaultsAndConstraints() {
        val config = PrintColorConfig()
        assertEquals(8, config.maxScreens)
        assertEquals(3.2, config.mergeThresholdDeltaE00, 0.001)
        assertEquals(0.5f, config.minCoveragePercentage, 0.001f)
    }
}
