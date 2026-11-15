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
        val whiteLab = ColorConverter.rgbToLab(255, 255, 255)
        assertEquals(100.0, whiteLab.l, 0.5)
        assertEquals(0.0, whiteLab.a, 1.0)
        assertEquals(0.0, whiteLab.b, 1.0)

        val blackLab = ColorConverter.rgbToLab(0, 0, 0)
        assertEquals(0.0, blackLab.l, 0.1)
        assertEquals(0.0, blackLab.a, 0.1)
        assertEquals(0.0, blackLab.b, 0.1)

        val greyLab = ColorConverter.rgbToLab(128, 128, 128)
        assertTrue(greyLab.l in 50.0..56.0)
        assertEquals(0.0, greyLab.a, 1.0)
        assertEquals(0.0, greyLab.b, 1.0)
    }

    @Test
    fun deltaE76_identicalColors_returnsZero() {
        val lab1 = ColorConverter.rgbToLab(139, 124, 255)
        val lab2 = ColorConverter.rgbToLab(139, 124, 255)
        val diff = ColorConverter.deltaE76(lab1, lab2)
        assertEquals(0.0, diff, 0.0001)
    }

    @Test
    fun deltaE76_distinctColors_returnsSignificantDifference() {
        val red = ColorConverter.rgbToLab(255, 0, 0)
        val blue = ColorConverter.rgbToLab(0, 0, 255)
        val diff = ColorConverter.deltaE76(red, blue)
        assertTrue("Expected large deltaE between red and blue, got $diff", diff > 50.0)
    }

    /**
     * Verifies CIEDE2000 implementation against published standard reference pairs
     * from Sharma, Wu, Dalal (2005) "The CIEDE2000 Color-Difference Formula".
     */
    @Test
    fun deltaE00_publishedReferencePairs() {
        // Pair 1: Standard identical colors
        val p1_1 = ColorConverter.Lab(50.0, 2.6772, -79.7751)
        val p1_2 = ColorConverter.Lab(50.0, 0.0, -82.7485)
        val dE00_1 = ColorConverter.deltaE00(p1_1, p1_2)
        // Sharma et al. published benchmark: ~2.0425
        assertEquals(2.0425, dE00_1, 0.005)

        // Pair 2: Gray / near-neutral pair
        val p2_1 = ColorConverter.Lab(50.0, 3.1571, -77.5528)
        val p2_2 = ColorConverter.Lab(50.0, 0.0, -82.7485)
        val dE00_2 = ColorConverter.deltaE00(p2_1, p2_2)
        // Sharma et al. published benchmark: ~2.8615
        assertEquals(2.8615, dE00_2, 0.07)

        // Pair 3: Identical colors
        val p3 = ColorConverter.Lab(60.0, 14.0, 25.0)
        assertEquals(0.0, ColorConverter.deltaE00(p3, p3), 0.00001)
    }

    @Test
    fun matchGrade_classifications() {
        assertEquals("Excellent (< 1.0 ΔE00)", ColorConverter.matchGrade(0.5))
        assertEquals("Good (1.0–2.0 ΔE00)", ColorConverter.matchGrade(1.4))
        assertEquals("Visible (2.0–4.0 ΔE00)", ColorConverter.matchGrade(3.1))
        assertEquals("Poor (> 4.0 ΔE00)", ColorConverter.matchGrade(5.5))
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
