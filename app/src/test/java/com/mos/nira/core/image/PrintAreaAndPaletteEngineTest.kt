package com.mos.nira.core.image

import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.ShadeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for NIRA Print Report — Part A:
 * - Print Area & Dimension Scaling from Aspect Ratio
 * - Garment Exclusion Print Mask & Interactive Canvas Erasing
 * - 16-Cluster K-Means Palette Extraction with CIEDE2000 Merging & 1.5% Coverage Pruning
 * - Shade Library Nearest Match Assignment
 * - Flat-Separation Posterisation Error Curve (K = 4, 6, 8, 10, 12, 16)
 */
class PrintAreaAndPaletteEngineTest {

    @Test
    fun testDimensionAndAspectRatioCalculation() {
        // Image width 600, height 400 (aspect ratio 3:2)
        val width = 600
        val height = 400
        val pixels = IntArray(width * height) { 0xFFFF0000.toInt() }

        val inputs = PrintJobInputs(
            printWidthCm = 30.0,
            garmentColorHex = "#FFFFFF"
        )

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        assertEquals(30.0, result.printWidthCm, 0.001)
        assertEquals(20.0, result.printHeightCm, 0.001) // 30 * (400 / 600)
        assertEquals(600.0, result.totalBoxAreaCm2, 0.001) // 30 * 20
        assertEquals(600.0, result.effectivePrintAreaCm2, 0.001) // All red pixels masked
        assertEquals(100.0, result.printMaskCoveragePercent, 0.001)
    }

    @Test
    fun testWhiteGarmentMaskExclusion() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total)

        // 60% White garment background (#FFFFFF: L > 80, chroma < 9)
        // 40% Navy print color (#002244)
        for (i in 0 until total) {
            pixels[i] = if (i < 6000) 0xFFFFFFFF.toInt() else 0xFF002244.toInt()
        }

        val inputs = PrintJobInputs(
            printWidthCm = 20.0,
            garmentColorHex = "#FFFFFF"
        )

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        // Fabric is excluded: only the 4000 navy pixels (40% of the image) are print.
        assertEquals(4000, result.printPixelCount)
        assertEquals(40.0, result.printPixelCount * 100.0 / total, 0.01)
        // Coverage is the share of the tight print box (40 print rows + 2 rows margin = 4200 px),
        // not the share of the whole image.
        assertEquals(4200, result.totalPixelCount)
        assertEquals(4000 * 100.0 / 4200, result.printMaskCoveragePercent, 0.01)
        assertEquals(1, result.measuredPalette.size)
        assertEquals("#002244", result.measuredPalette[0].hex)
        assertEquals(100.0, result.measuredPalette[0].coveragePercent, 0.01)
    }

    @Test
    fun testErasedRectangleExclusion() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total) { 0xFFFF0000.toInt() } // All Red

        // Erase top-left quadrant [0.0..0.5, 0.0..0.5] -> 25% of image
        val erasedRect = NormalizedRect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val inputs = PrintJobInputs(
            printWidthCm = 20.0,
            garmentColorHex = "#FFFFFF",
            erasedRectangles = listOf(erasedRect)
        )

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        // 25% erased -> 75% retained
        assertEquals(7500, result.printPixelCount)
        assertEquals(75.0, result.printMaskCoveragePercent, 0.01)
    }

    @Test
    fun testClusterMergingUnderCIEDE2000Threshold() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total)

        // 50% pure red (#FF0000), 50% nearly identical red (#FE0000)
        // Delta E00 between them is ~0.15, well under merge threshold of 3.2
        for (i in 0 until total) {
            pixels[i] = if (i < 5000) 0xFFFF0000.toInt() else 0xFFFE0000.toInt()
        }

        val inputs = PrintJobInputs(printWidthCm = 25.0)

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        // They must be merged into 1 dominant print screen
        assertEquals(1, result.measuredPalette.size)
        assertEquals(100.0, result.measuredPalette[0].coveragePercent, 0.01)
    }

    @Test
    fun testPruningClustersUnderOnePointFivePercent() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total)

        // 99% Royal Blue (#0033AA)
        // 1% Yellow speckle noise (#FFEE00) -> 1% is strictly < 1.5% minimum coverage threshold
        for (i in 0 until total) {
            pixels[i] = if (i < 9900) 0xFF0033AA.toInt() else 0xFFFFEE00.toInt()
        }

        val inputs = PrintJobInputs(printWidthCm = 30.0)

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        // Yellow speckle (<1.5%) must be dropped, only 1 screen returned
        assertEquals(1, result.measuredPalette.size)
        val color = result.measuredPalette[0]
        assertTrue("Red channel should be near 0 but was ${color.rgb.r}", color.rgb.r <= 5)
        assertTrue("Blue channel should be dominant (> 150) but was ${color.rgb.b}", color.rgb.b >= 150)
    }

    @Test
    fun testErrorCurveCalculationAcrossKValues() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total)

        // Create an image with 6 distinct colors: Red, Green, Blue, Cyan, Magenta, Yellow
        val colors = intArrayOf(
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFF00FFFF.toInt(),
            0xFFFF00FF.toInt(),
            0xFFFFFF00.toInt()
        )
        for (i in 0 until total) {
            pixels[i] = colors[(i / (total / 6)).coerceIn(0, 5)]
        }

        val inputs = PrintJobInputs(printWidthCm = 28.0)

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        val errorCurve = result.errorCurve
        assertEquals(6, errorCurve.size)
        val kValues = errorCurve.map { it.k }
        assertEquals(listOf(4, 6, 8, 10, 12, 16), kValues)

        // Mean error at K=4 should be greater than or equal to error at K=6 (where all 6 colors can be represented)
        val err4 = errorCurve.first { it.k == 4 }.meanDeltaE00
        val err6 = errorCurve.first { it.k == 6 }.meanDeltaE00
        val err16 = errorCurve.first { it.k == 16 }.meanDeltaE00

        assertTrue("Error at K=4 ($err4) should be >= error at K=6 ($err6)", err4 >= err6)
        assertTrue("Error at K=6 ($err6) should be >= error at K=16 ($err16)", err6 >= err16)
        assertTrue("Error must be non-negative", err16 >= 0.0)
    }

    @Test
    fun testShadeLibraryMatchingInPartA() {
        val width = 50
        val height = 50
        val pixels = IntArray(width * height) { 0xFFFF5500.toInt() } // Vibrant Orange

        val orangeShade = ShadeEntry(
            type = "TCX",
            code = "16-1364",
            name = "Vibrant Orange",
            labL = 60.0,
            labA = 55.0,
            labB = 65.0,
            libraryName = "Demo",
            isUserCreated = false
        )

        val navyShade = ShadeEntry(
            type = "TCX",
            code = "19-4010",
            name = "Total Eclipse",
            labL = 15.0,
            labA = 2.0,
            labB = -10.0,
            libraryName = "Demo",
            isUserCreated = false
        )

        val inputs = PrintJobInputs(printWidthCm = 20.0)

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            shadeCandidates = listOf(orangeShade, navyShade),
            generateMaskBitmap = false
        )

        assertEquals(1, result.measuredPalette.size)
        val matchedColor = result.measuredPalette[0]
        assertNotNull(matchedColor.nearestShade)
        assertEquals("Vibrant Orange", matchedColor.nearestShade?.name)
        assertNotNull(matchedColor.nearestShadeDeltaE00)
        assertNotNull(matchedColor.matchGrade)
    }

    @Test
    fun testEmptyImageHandling() {
        val inputs = PrintJobInputs(printWidthCm = 25.0)

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = intArrayOf(),
            width = 0,
            height = 0,
            inputs = inputs,
            generateMaskBitmap = false
        )

        assertEquals(0, result.printPixelCount)
        assertEquals(0, result.totalPixelCount)
        assertEquals(0.0, result.effectivePrintAreaCm2, 0.001)
        assertEquals(0.0, result.printMaskCoveragePercent, 0.001)
        assertTrue(result.measuredPalette.isEmpty())
    }

    @Test
    fun testOneByOneImageInked() {
        val inputs = PrintJobInputs(printWidthCm = 10.0, garmentColorHex = "#FFFFFF")

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = intArrayOf(0xFFFF0000.toInt()), // Inked Red
            width = 1,
            height = 1,
            inputs = inputs,
            generateMaskBitmap = false
        )

        assertEquals(1, result.printPixelCount)
        assertEquals(1, result.totalPixelCount)
        assertEquals(100.0, result.printMaskCoveragePercent, 0.001)
        assertEquals(1, result.measuredPalette.size)
        assertEquals("#FF0000", result.measuredPalette[0].hex)
    }

    @Test
    fun testOneByOneImageGarment() {
        val inputs = PrintJobInputs(printWidthCm = 10.0, garmentColorHex = "#FFFFFF")

        val result = PrintAreaAndPaletteEngine.computePartA(
            pixels = intArrayOf(0xFFFFFFFF.toInt()), // Garment White
            width = 1,
            height = 1,
            inputs = inputs,
            generateMaskBitmap = false
        )

        assertEquals(0, result.printPixelCount)
        assertEquals(1, result.totalPixelCount)
        assertEquals(0.0, result.effectivePrintAreaCm2, 0.001)
        assertEquals(0.0, result.printMaskCoveragePercent, 0.001)
        assertTrue(result.measuredPalette.isEmpty())
    }

    @Test
    fun testAllGarmentColorImage() {
        val width = 100
        val height = 100
        val total = width * height
        val whitePixels = IntArray(total) { 0xFFFFFFFF.toInt() }

        val whiteInputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#FFFFFF")
        val whiteResult = PrintAreaAndPaletteEngine.computePartA(
            pixels = whitePixels,
            width = width,
            height = height,
            inputs = whiteInputs,
            generateMaskBitmap = false
        )

        assertEquals(0, whiteResult.printPixelCount)
        assertEquals(0.0, whiteResult.effectivePrintAreaCm2, 0.001)
        assertTrue("Palette must be empty for 100% garment color", whiteResult.measuredPalette.isEmpty())

        // Test Black garment with all black pixels
        val blackPixels = IntArray(total) { 0xFF000000.toInt() }
        val blackInputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#000000")
        val blackResult = PrintAreaAndPaletteEngine.computePartA(
            pixels = blackPixels,
            width = width,
            height = height,
            inputs = blackInputs,
            generateMaskBitmap = false
        )

        assertEquals(0, blackResult.printPixelCount)
        assertEquals(0.0, blackResult.effectivePrintAreaCm2, 0.001)
        assertTrue("Palette must be empty for 100% black garment image", blackResult.measuredPalette.isEmpty())
    }

    @Test
    fun testUserAdjustableNonWhiteGarmentThreshold() {
        val width = 50
        val height = 50
        val total = width * height

        // Base garment #111111 (L ~ 7.0)
        // Artwork color #222222 (L ~ 14.0)
        // Perceptual DeltaE00 between #111111 and #222222 is ~6.1
        val pixels = IntArray(total) { 0xFF222222.toInt() }

        // The fabric colour is given as "tap the fabric" (userGarmentHex), which is trusted exactly.
        // A preset alone is only a first guess and the fabric would be measured from the border.
        // With default threshold 5.0: deltaE (6.1) > 5.0 -> Inked print pixel!
        val defaultInputs = PrintJobInputs(
            garmentColorHex = "#111111",
            userGarmentHex = "#111111",
            garmentThresholdDeltaE00 = 5.0
        )
        val resultDefault = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = defaultInputs,
            generateMaskBitmap = false
        )
        assertEquals(total, resultDefault.printPixelCount)

        // With raised threshold 8.0: deltaE (6.1) <= 8.0 -> Excluded as garment!
        val customInputs = PrintJobInputs(
            garmentColorHex = "#111111",
            userGarmentHex = "#111111",
            garmentThresholdDeltaE00 = 8.0
        )
        val resultCustom = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = customInputs,
            generateMaskBitmap = false
        )
        assertEquals(0, resultCustom.printPixelCount)
    }
}
