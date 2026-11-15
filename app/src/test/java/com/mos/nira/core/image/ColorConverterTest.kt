package com.mos.nira.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorConverterTest {

    @Test
    fun rgbToHex_standardColors() {
        assertEquals("#FFFFFF", ColorConverter.rgbToHex(255, 255, 255))
        assertEquals("#000000", ColorConverter.rgbToHex(0, 0, 0))
        assertEquals("#FF0000", ColorConverter.rgbToHex(255, 0, 0))
        assertEquals("#00FF00", ColorConverter.rgbToHex(0, 255, 0))
        assertEquals("#0000FF", ColorConverter.rgbToHex(0, 0, 255))
        assertEquals("#8B7CFF", ColorConverter.rgbToHex(139, 124, 255))
    }

    @Test
    fun hexToRgb_standardColors() {
        val white = ColorConverter.hexToRgb("#FFFFFF")
        assertEquals(255, white.r)
        assertEquals(255, white.g)
        assertEquals(255, white.b)

        val black = ColorConverter.hexToRgb("000000")
        assertEquals(0, black.r)
        assertEquals(0, black.g)
        assertEquals(0, black.b)

        val custom = ColorConverter.hexToRgb("#55D6E8")
        assertEquals(85, custom.r)
        assertEquals(214, custom.g)
        assertEquals(232, custom.b)
    }

    @Test
    fun rgbToLab_referenceValues() {
        // Pure White: L* should be ~100.0, a* ~0.0, b* ~0.0
        val whiteLab = ColorConverter.rgbToLab(255, 255, 255)
        assertEquals(100.0, whiteLab.l, 0.5)
        assertEquals(0.0, whiteLab.a, 1.0)
        assertEquals(0.0, whiteLab.b, 1.0)

        // Pure Black: L* should be 0.0, a* 0.0, b* 0.0
        val blackLab = ColorConverter.rgbToLab(0, 0, 0)
        assertEquals(0.0, blackLab.l, 0.1)
        assertEquals(0.0, blackLab.a, 0.1)
        assertEquals(0.0, blackLab.b, 0.1)

        // Mid-grey (128, 128, 128): L* should be around 53.6
        val greyLab = ColorConverter.rgbToLab(128, 128, 128)
        assertTrue(greyLab.l in 50.0..56.0)
        assertEquals(0.0, greyLab.a, 1.0)
        assertEquals(0.0, greyLab.b, 1.0)
    }

    @Test
    fun deltaE_identicalColors_returnsZero() {
        val lab1 = ColorConverter.rgbToLab(139, 124, 255)
        val lab2 = ColorConverter.rgbToLab(139, 124, 255)
        val diff = ColorConverter.deltaE(lab1, lab2)
        assertEquals(0.0, diff, 0.0001)
    }

    @Test
    fun deltaE_distinctColors_returnsSignificantDifference() {
        val red = ColorConverter.rgbToLab(255, 0, 0)
        val blue = ColorConverter.rgbToLab(0, 0, 255)
        val diff = ColorConverter.deltaE(red, blue)
        assertTrue("Expected large deltaE between red and blue, got $diff", diff > 50.0)
    }

    @Test
    fun sRgbToLinear_and_back_isConsistent() {
        for (v in listOf(0, 10, 50, 128, 200, 255)) {
            val linear = ColorConverter.sRgbToLinear(v)
            val roundTrip = ColorConverter.linearToSRgb(linear)
            assertTrue(abs(v - roundTrip) <= 1)
        }
    }
}
