package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.MeshDepositRule
import com.mos.nira.features.artwork.data.ProcessInksConfig
import com.mos.nira.features.artwork.data.SeparationDecisionRules
import com.mos.nira.features.artwork.domain.FilmPositiveChannel
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Full regression tests for NIRA Print Engineering:
 * 1. Real Garment Photo Regression:
 *    - Dark textured background with uneven lighting
 *    - White shape + fading blue gradient
 *    - Verifies: exactly 2 colours in print palette, tight print box (~11% inked area, not 84.9%),
 *      no garment fabric screens, and recommended spot halftone strategy.
 * 2. Part D Ink Consumption:
 *    - Hand-calculated deposit calculations for 110 tpi (45 g/m²) and 305 tpi (18 g/m²)
 *    - Scaling with print width squared
 *    - Min-max deposit range verification
 */
class PrintReportRegressionTest {

    @Test
    fun testRealGarmentPhotoRegression_DarkTexturedFabricWithWhiteAndBlueLogo() {
        val width = 120
        val height = 120
        val total = width * height
        val pixels = IntArray(total)

        // 1. Generate dark textured fabric background with uneven lighting across the image
        // L ~ 15 to 25, minor random noise representing weave
        val rng = Random(42)
        for (y in 0 until height) {
            val gradientShade = ((y.toDouble() / height) * 10).toInt() // Uneven lighting gradient
            for (x in 0 until width) {
                val noise = rng.nextInt(-4, 5)
                val base = (22 + gradientShade + noise).coerceIn(12, 40)
                // Dark charcoal fabric RGB
                pixels[y * width + x] = (0xFF shl 24) or (base shl 16) or (base shl 8) or base
            }
        }

        // 2. Insert White-to-Blue logo in center: x in 40 until 80, y in 40 until 80
        // Occupies 40 x 40 = 1600 pixels out of 14400 total = 11.1% of image
        for (y in 40 until 80) {
            for (x in 40 until 80) {
                val idx = y * width + x
                if (x < 60) {
                    // Solid White part
                    pixels[idx] = 0xFFFFFFFF.toInt()
                } else {
                    // Blue gradient fading
                    val t = (x - 60) / 20.0
                    val r = (0 * (1 - t) + 20 * t).toInt()
                    val g = (85 * (1 - t) + 30 * t).toInt()
                    val b = (255 * (1 - t) + 120 * t).toInt()
                    pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val inputs = PrintJobInputs(
            printWidthCm = 30.0,
            garmentColorHex = "#000000" // Dark garment
        )

        // Run Part A
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        // Assertion 1: Garment background detected as dark
        val profile = partA.garmentProfile
        assertNotNull("Garment profile must be estimated", profile)
        assertTrue("Garment must be detected as dark fabric (L < 45)", profile!!.medianLab.l < 45.0)

        // Assertion 2: Tight print box detected around logo (x: 40-80 => 0.33 to 0.67)
        assertTrue("Print box minX should be >= 0.25", partA.printBox.minX >= 0.25f)
        assertTrue("Print box maxX should be <= 0.75", partA.printBox.maxX <= 0.75f)
        assertTrue("Print box minY should be >= 0.25", partA.printBox.minY >= 0.25f)
        assertTrue("Print box maxY should be <= 0.75", partA.printBox.maxY <= 0.75f)

        // Assertion 3: Inked print area should be close to 11.1% of total image (within 3%), NOT 84.9%!
        val coverageOfImage = (partA.printPixelCount.toDouble() / total) * 100.0
        assertTrue("Inked area of photo should be between 9% and 15% (actual: $coverageOfImage%)", coverageOfImage in 9.0..15.0)

        // Assertion 4: Exactly 2 colours in measured print palette (White and Blue), NOT 6 fabric colours!
        assertEquals("Measured print palette must have exactly 2 colours (white + blue)", 2, partA.measuredPalette.size)
        val hasWhite = partA.measuredPalette.any { it.lab.l > 80.0 }
        val hasBlue = partA.measuredPalette.any { it.lab.b < -10.0 }
        assertTrue("Palette must contain white", hasWhite)
        assertTrue("Palette must contain blue", hasBlue)

        // Run Part B
        val processInks = ProcessInksConfig()
        val decisionRules = SeparationDecisionRules(
            darkGarmentThresholdL = 45.0,
            qualityToleranceDeltaE00 = 4.5
        )

        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels.filterIndexed { i, _ ->
                val pxX = i % width
                val pxY = i / width
                pxX in 40 until 80 && pxY in 40 until 80
            }.toIntArray(),
            partA = partA,
            inputs = inputs,
            processInks = processInks,
            decisionRules = decisionRules
        )

        // Assertion 5: Recommendation selects spot halftone strategy (2 screens: blue halftone over white), NOT CMYK
        val recommended = partB.strategies.find { it.isRecommended }
        assertNotNull("There must be a recommended strategy", recommended)
        assertEquals("Recommended strategy should be Best N Spot Halftone", SeparationStrategyType.BEST_N_SPOT_HALFTONE, recommended?.strategyType)
        assertTrue("Should recommend 2 screens for white and blue logo", recommended?.screenCount == 2 || recommended?.screenCount == 3)
    }

    @Test
    fun testPartDInkConsumption_HandCalculatedMesh110And305() {
        // Setup Part A mock geometry:
        // Print Box: 30 cm x 20 cm = 600 cm² = 0.06 m²
        val boxAreaCm2 = 600.0
        val boxAreaM2 = 0.06

        val partA = PartAResult(
            printWidthCm = 30.0,
            printHeightCm = 20.0,
            totalBoxAreaCm2 = boxAreaCm2,
            effectivePrintAreaCm2 = 300.0,
            printPixelCount = 5000,
            totalPixelCount = 10000,
            printMaskCoveragePercent = 50.0,
            measuredPalette = emptyList(),
            errorCurve = emptyList()
        )

        // Setup Part C channels:
        // Channel 1: 110 tpi, 50% box coverage
        // Channel 2: 305 tpi, 50% box coverage
        val channels = listOf(
            FilmPositiveChannel(
                channelIndex = 1,
                inkName = "White Base",
                inkHex = "#FFFFFF",
                inkRgb = ColorConverter.Rgb(255, 255, 255),
                averageCoveragePercent = 100.0,
                boxCoveragePercent = 50.0,
                recommendedMesh = "110 tpi"
            ),
            FilmPositiveChannel(
                channelIndex = 2,
                inkName = "Fine Detail Cyan",
                inkHex = "#00AEEF",
                inkRgb = ColorConverter.Rgb(0, 174, 239),
                averageCoveragePercent = 100.0,
                boxCoveragePercent = 50.0,
                recommendedMesh = "305 tpi"
            )
        )

        val partC = PartCResult(
            strategyType = SeparationStrategyType.BEST_N_SPOT_HALFTONE,
            strategyName = "Spot Halftone",
            channels = channels
        )

        val inputs = PrintJobInputs(
            printWidthCm = 30.0,
            strokes = 1,
            wastagePercent = 10.0, // 10% wastage => factor 1.10
            orderPieces = 100
        )

        val depositRules = mapOf(
            110 to MeshDepositRule(110, 45.0, 38.0, 63.0, "sourced"),
            305 to MeshDepositRule(305, 18.0, 13.0, 25.0, "sourced")
        )

        val partD = SeparationStrategyEngine.calculatePartD(
            partA = partA,
            partC = partC,
            inputs = inputs,
            depositRules = depositRules
        )

        // Hand calculation for 110 tpi:
        // Coverage = 0.50, Area = 0.06 m², Deposit = 45 g/m², Strokes = 1, Wastage = 1.10
        // Expected default = 0.50 * 0.06 * 45 * 1 * 1.10 = 1.485 g
        // Expected min = 0.50 * 0.06 * 38 * 1 * 1.10 = 1.254 g
        // Expected max = 0.50 * 0.06 * 63 * 1 * 1.10 = 2.079 g
        val ch1 = partD.channels[0]
        assertEquals(1.485, ch1.inkGramsPerGarment, 0.0001)
        assertEquals(1.254, ch1.inkGramsPerGarmentMin, 0.0001)
        assertEquals(2.079, ch1.inkGramsPerGarmentMax, 0.0001)
        assertEquals(148.5, ch1.inkGramsPer100Pieces, 0.01)

        // Hand calculation for 305 tpi:
        // Coverage = 0.50, Area = 0.06 m², Deposit = 18 g/m², Strokes = 1, Wastage = 1.10
        // Expected default = 0.50 * 0.06 * 18 * 1 * 1.10 = 0.594 g
        // Expected min = 0.50 * 0.06 * 13 * 1 * 1.10 = 0.429 g
        // Expected max = 0.50 * 0.06 * 25 * 1 * 1.10 = 0.825 g
        val ch2 = partD.channels[1]
        assertEquals(0.594, ch2.inkGramsPerGarment, 0.0001)
        assertEquals(0.429, ch2.inkGramsPerGarmentMin, 0.0001)
        assertEquals(0.825, ch2.inkGramsPerGarmentMax, 0.0001)
        assertEquals(59.4, ch2.inkGramsPer100Pieces, 0.01)

        // Total Job Consumption:
        // 1.485 + 0.594 = 2.079 g per garment
        assertEquals(2.079, partD.totalGramsPerGarment, 0.0001)
        assertEquals(207.9, partD.totalGramsPer100Pieces, 0.01)
        assertEquals("Screen residue and set-up loss are not included.", partD.note)
    }

    @Test
    fun testPartDInkConsumption_ScalesWithPrintWidthSquared() {
        val depositRules = mapOf(
            156 to MeshDepositRule(156, 36.0, 30.0, 48.0, "interpolated")
        )

        fun calcForWidth(w: Double): Double {
            val aspect = 2.0 / 3.0 // Height = w * (2/3)
            val h = w * aspect
            val areaCm2 = w * h
            val partA = PartAResult(
                printWidthCm = w,
                printHeightCm = h,
                totalBoxAreaCm2 = areaCm2,
                effectivePrintAreaCm2 = areaCm2 * 0.5,
                printPixelCount = 5000,
                totalPixelCount = 10000,
                printMaskCoveragePercent = 50.0,
                measuredPalette = emptyList(),
                errorCurve = emptyList()
            )

            val partC = PartCResult(
                strategyType = SeparationStrategyType.FLAT_SPOT_SOLID,
                strategyName = "Spot",
                channels = listOf(
                    FilmPositiveChannel(
                        channelIndex = 1,
                        inkName = "Black",
                        inkHex = "#000000",
                        inkRgb = ColorConverter.Rgb(0, 0, 0),
                        averageCoveragePercent = 100.0,
                        boxCoveragePercent = 50.0,
                        recommendedMesh = "156 tpi"
                    )
                )
            )

            val inputs = PrintJobInputs(printWidthCm = w, strokes = 1, wastagePercent = 10.0)
            val res = SeparationStrategyEngine.calculatePartD(partA, partC, inputs, depositRules)
            return res.totalGramsPerGarment
        }

        val ink30cm = calcForWidth(30.0)
        val ink60cm = calcForWidth(60.0)

        // When width doubles (30 -> 60 cm), area quadruples (600 -> 2400 cm²), so ink must be exactly 4x
        assertEquals(4.0, ink60cm / ink30cm, 0.0001)
    }
}
