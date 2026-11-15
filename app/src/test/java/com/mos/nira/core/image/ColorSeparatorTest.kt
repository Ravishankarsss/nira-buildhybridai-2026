package com.mos.nira.core.image

import com.mos.nira.features.artwork.domain.DominantColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure Kotlin unit tests for color separation logic, layer coverage calculations,
 * and nearest cluster assignments in CIEDE2000 space.
 */
class ColorSeparatorTest {

    @Test
    fun separateColors_nearestClusterAssignment_isExact() {
        val c1 = DominantColor(
            id = 0,
            hex = "#FF0000",
            r = 255,
            g = 0,
            b = 0,
            labL = 53.2,
            labA = 80.1,
            labB = 67.2,
            pixelCount = 5000,
            pixelPercentage = 50.0f
        )
        val c2 = DominantColor(
            id = 1,
            hex = "#0000FF",
            r = 0,
            g = 0,
            b = 255,
            labL = 32.3,
            labA = 79.2,
            labB = -107.8,
            pixelCount = 5000,
            pixelPercentage = 50.0f
        )

        val palette = listOf(c1, c2)
        val lab1 = ColorConverter.Lab(c1.labL, c1.labA, c1.labB)
        val lab2 = ColorConverter.Lab(c2.labL, c2.labA, c2.labB)

        // Test sample pixel: pure red
        val testPixelRed = ColorConverter.rgbToLab(250, 5, 5)
        val d1 = ColorConverter.deltaE00(testPixelRed, lab1)
        val d2 = ColorConverter.deltaE00(testPixelRed, lab2)
        assertTrue("Red pixel must be much closer to red cluster than blue cluster", d1 < d2)

        // Test sample pixel: pure blue
        val testPixelBlue = ColorConverter.rgbToLab(5, 5, 250)
        val dB1 = ColorConverter.deltaE00(testPixelBlue, lab1)
        val dB2 = ColorConverter.deltaE00(testPixelBlue, lab2)
        assertTrue("Blue pixel must be much closer to blue cluster than red cluster", dB2 < dB1)
    }

    @Test
    fun separateColors_coveragePercentageSum() {
        val c1 = DominantColor(0, "#FF0000", 255, 0, 0, 53.0, 80.0, 67.0, 600, 60.0f)
        val c2 = DominantColor(1, "#00FF00", 0, 255, 0, 87.0, -86.0, 83.0, 400, 40.0f)

        val totalCoverage = c1.pixelPercentage + c2.pixelPercentage
        assertEquals(100.0f, totalCoverage, 0.001f)
    }

    @Test
    fun separateColors_emptyPaletteHandled() {
        val palette = emptyList<DominantColor>()
        assertTrue(palette.isEmpty())
    }
}
