package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.MeshDepositRule
import com.mos.nira.features.artwork.data.PrintRulesRepository
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

        // Assertion 3: Inked area within 2 percentage points of the true value (1600 / 14400 = 11.11%), NOT 84.9%!
        val coverageOfImage = (partA.printPixelCount.toDouble() / total) * 100.0
        val trueCoverage = 1600.0 * 100.0 / total
        assertTrue(
            "Inked area must be within 2 points of $trueCoverage% (actual: $coverageOfImage%)",
            kotlin.math.abs(coverageOfImage - trueCoverage) <= 2.0
        )

        // Assertion 4: Exactly 2 colours in measured print palette (White and Blue), NOT 6 fabric colours!
        assertEquals("Measured print palette must have exactly 2 colours (white + blue)", 2, partA.measuredPalette.size)
        val hasWhite = partA.measuredPalette.any { it.lab.l > 80.0 }
        val hasBlue = partA.measuredPalette.any { it.lab.b < -10.0 }
        assertTrue("Palette must contain white", hasWhite)
        assertTrue("Palette must contain blue", hasBlue)

        // Run Part B
        val processInks = ProcessInksConfig()
        // Rule values as in print_rules.json (the defaults are checked against the file).
        val decisionRules = SeparationDecisionRules()

        // No print colour may be the garment colour.
        val garmentLab = profile.medianLab
        assertTrue(
            "No print colour may be the garment colour",
            partA.measuredPalette.all { ColorConverter.deltaE00(it.lab, garmentLab) > profile.adaptiveThresholdDeltaE00 }
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
        assertEquals("Should recommend 2 screens for the white and blue logo", 2, recommended?.screenCount)
        assertEquals(2, recommended?.inkHexes?.size)
        assertTrue(
            "The blue must be reported as halftone over white (was: ${recommended?.transitionNote})",
            recommended?.transitionNote?.contains("as halftone over") == true
        )
        // No screen in the garment colour, in any option.
        for (strategy in partB.strategies.filter { it.strategyType != SeparationStrategyType.CMYK_PROCESS }) {
            for (hex in strategy.inkHexes) {
                val lab = ColorConverter.rgbToLab(ColorConverter.hexToRgb(hex))
                assertTrue("${strategy.name} has a screen in the garment colour ($hex)", ColorConverter.deltaE00(lab, garmentLab) > 3.8)
            }
        }
        // CMYK + whites must not be recommended for a 2-colour logo.
        assertFalse(partB.strategies.first { it.strategyType == SeparationStrategyType.CMYK_PROCESS }.isRecommended)

        // With "separate white base" switched on: 3 screens.
        val separateBase = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = logoPixels(pixels, width),
            partA = partA,
            inputs = inputs.copy(whitePrintActsAsBase = false),
            processInks = processInks,
            decisionRules = decisionRules
        )
        val recommendedSeparate = separateBase.strategies.first { it.isRecommended }
        assertEquals(SeparationStrategyType.BEST_N_SPOT_HALFTONE, recommendedSeparate.strategyType)
        assertEquals("Separate white base gives 3 screens", 3, recommendedSeparate.screenCount)
    }

    // ---------- helpers for the regression image ----------

    private val regW = 120
    private val regH = 120

    /** The same synthetic garment photo as the test above: dark textured fabric, white + blue gradient logo. */
    private fun regressionPixels(): IntArray {
        val pixels = IntArray(regW * regH)
        val rng = Random(42)
        for (y in 0 until regH) {
            val gradientShade = ((y.toDouble() / regH) * 10).toInt()
            for (x in 0 until regW) {
                val noise = rng.nextInt(-4, 5)
                val base = (22 + gradientShade + noise).coerceIn(12, 40)
                pixels[y * regW + x] = (0xFF shl 24) or (base shl 16) or (base shl 8) or base
            }
        }
        for (y in 40 until 80) {
            for (x in 40 until 80) {
                val idx = y * regW + x
                if (x < 60) {
                    pixels[idx] = 0xFFFFFFFF.toInt()
                } else {
                    val t = (x - 60) / 20.0
                    val r = (0 * (1 - t) + 20 * t).toInt()
                    val g = (85 * (1 - t) + 30 * t).toInt()
                    val b = (255 * (1 - t) + 120 * t).toInt()
                    pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return pixels
    }

    private fun logoPixels(pixels: IntArray, width: Int): IntArray =
        pixels.filterIndexed { i, _ -> (i % width) in 40 until 80 && (i / width) in 40 until 80 }.toIntArray()

    private fun isLogo(index: Int): Boolean = (index % regW) in 40 until 80 && (index / regW) in 40 until 80

    @Test
    fun testRegressionImage_InkIsOnlyOnPrintPixels_NotOnFabric() {
        val pixels = regressionPixels()
        val inputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#000000")
        val processInks = ProcessInksConfig()
        val partA = PrintAreaAndPaletteEngine.computePartA(pixels, regW, regH, inputs, generateMaskBitmap = false)
        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = logoPixels(pixels, regW),
            partA = partA,
            inputs = inputs,
            processInks = processInks
        )
        val partC = SeparationStrategyEngine.generatePartC(
            pixels = pixels,
            width = regW,
            height = regH,
            originalBitmap = null,
            partA = partA,
            partB = partB,
            chosenStrategyType = SeparationStrategyType.BEST_N_SPOT_HALFTONE,
            inputs = inputs,
            processInks = processInks
        )

        // Two screens: the white and the blue. No base, no highlight, no garment-colour screen.
        assertEquals(2, partC.channels.size)
        assertTrue(partC.channels.none { it.inkName.contains("Base White") || it.inkName.contains("Highlight White") })

        // Every film positive is blank (white = no ink) outside the logo.
        for (channel in partC.channels) {
            val film = channel.rawGreyscalePixels!!
            for (i in film.indices) {
                if (!isLogo(i)) assertEquals("${channel.inkName} has ink on fabric at $i", 255, film[i] and 0xFF)
            }
        }
        // The white screen is solid on the white half of the logo.
        val white = partC.channels.first { ColorConverter.rgbToLab(it.inkRgb).l > 80.0 }
        // (Grey 0 = 100% ink. Colours are rounded to 6 bits, so pure white reads as about 99%.)
        assertTrue((white.rawGreyscalePixels!![50 * regW + 45] and 0xFF) <= 5)

        // Part D: ink uses the print pixels only. Each pixel carries at most 100% ink in total,
        // so the summed coverage over the box cannot exceed the logo's share of the box.
        val box = partA.printBox
        val boxW = (box.maxX * regW).toInt() - (box.minX * regW).toInt()
        val boxH = (box.maxY * regH).toInt() - (box.minY * regH).toInt()
        val logoShareOfBox = 1600.0 * 100.0 / (boxW * boxH)
        val summedCoverage = partC.channels.sumOf { it.boxCoveragePercent }
        assertTrue(
            "summed box coverage $summedCoverage% must not exceed the logo's share $logoShareOfBox%",
            summedCoverage <= logoShareOfBox + 0.01
        )
        assertTrue("there must be ink on the logo", summedCoverage > logoShareOfBox * 0.5)

        val depositRules = PrintRulesRepository.defaultDepositRules()
        val partD = SeparationStrategyEngine.calculatePartD(partA, partC, inputs, depositRules)
        val boxAreaM2 = partA.totalBoxAreaCm2 / 10000.0
        var expected = 0.0
        for (channel in partC.channels) {
            val mesh = channel.recommendedMesh.filter { it.isDigit() }.toInt()
            expected += channel.boxCoveragePercent / 100.0 * boxAreaM2 *
                depositRules[mesh]!!.depositGPerM2Default * inputs.strokes * (1 + inputs.wastagePercent / 100.0)
        }
        assertEquals(expected, partD.totalGramsPerGarment, 1e-9)
        // If the fabric were inked too, the box would be 100% covered. It must be far less.
        val fullBox = boxAreaM2 * depositRules[200]!!.depositGPerM2Default * (1 + inputs.wastagePercent / 100.0)
        assertTrue(partD.totalGramsPerGarment < fullBox)
    }

    @Test
    fun testPartD_WhiteScreensAreIncludedOnDarkGarment() {
        val size = 50
        val pixels = IntArray(size * size) { 0xFFFF0000.toInt() } // red artwork filling the image
        val inputs = PrintJobInputs(printWidthCm = 20.0, garmentColorHex = "#000000")
        val processInks = ProcessInksConfig()
        val partA = PrintAreaAndPaletteEngine.computePartA(pixels, size, size, inputs, generateMaskBitmap = false)
        val partB = SeparationStrategyEngine.evaluateStrategies(pixels, partA, inputs, processInks)
        val partC = SeparationStrategyEngine.generatePartC(
            pixels = pixels,
            width = size,
            height = size,
            originalBitmap = null,
            partA = partA,
            partB = partB,
            chosenStrategyType = SeparationStrategyType.BEST_N_SPOT_HALFTONE,
            inputs = inputs,
            processInks = processInks
        )
        val partD = SeparationStrategyEngine.calculatePartD(partA, partC, inputs, PrintRulesRepository.defaultDepositRules())

        val base = partD.channels.firstOrNull { it.inkName.contains("Base White") }
        assertNotNull("Base white must be in the ink quantity on a dark garment", base)
        assertTrue("Base white must use ink", base!!.inkGramsPerGarment > 0.0)
        assertEquals(110, base.meshTpi)
        assertNotNull(partD.channels.firstOrNull { it.inkName.contains("Highlight White") })
        assertEquals(partD.channels.sumOf { it.inkGramsPerGarment }, partD.totalGramsPerGarment, 1e-9)
    }

    /** Mean colour error added by rounding colours to 6 bits per channel, on its own. */
    private fun meanRoundingError(pixels: IntArray): Double {
        var sum = 0.0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += ColorConverter.colorDistanceE00(
                r, g, b,
                ((r shr 2) shl 2) + 2, ((g shr 2) shl 2) + 2, ((b shr 2) shl 2) + 2
            )
        }
        return sum / pixels.size
    }

    /** Photo-like test image: dark textured fabric with a large smooth multi-colour gradient print. */
    private fun photoLikePixels(size: Int): IntArray {
        val rng = Random(7)
        val pixels = IntArray(size * size)
        val lo = size / 5
        val hi = size - size / 5
        for (y in 0 until size) {
            for (x in 0 until size) {
                val inPrint = x in lo until hi && y in lo until hi
                pixels[y * size + x] = if (!inPrint) {
                    val v = (26 + (y * 8 / size) + rng.nextInt(-4, 5)).coerceIn(10, 45)
                    (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                } else {
                    val u = (x - lo).toDouble() / (hi - lo)
                    val v = (y - lo).toDouble() / (hi - lo)
                    val r = (255 * u + rng.nextInt(-2, 3)).toInt().coerceIn(0, 255)
                    val g = (255 * v + rng.nextInt(-2, 3)).toInt().coerceIn(0, 255)
                    val b = (255 * (1 - u) * (1 - v) + 80 * u * v + rng.nextInt(-2, 3)).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return pixels
    }

    @Test
    fun testQualityFigures_RoundingToSixBitsAddsLessThanHalfADeltaE() {
        val logo = meanRoundingError(logoPixels(regressionPixels(), regW))
        val photo = meanRoundingError(photoLikePixels(256))
        println("=== 6-BIT ROUNDING ERROR (mean CIEDE2000, rounding alone) ===")
        println("Regression logo pixels: " + String.format(java.util.Locale.US, "%.3f", logo))
        println("Photo-like image:       " + String.format(java.util.Locale.US, "%.3f", photo))
        assertTrue("6-bit rounding adds $logo mean dE00 on the logo", logo < 0.5)
        assertTrue("6-bit rounding adds $photo mean dE00 on a photo-like image", photo < 0.5)
    }

    @Test
    fun testPhotoLike1024Benchmark_ReportsRealTimes() {
        val size = 1024
        val pixels = photoLikePixels(size)
        val inputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#000000")
        val processInks = ProcessInksConfig()

        val t0 = System.currentTimeMillis()
        val partA = PrintAreaAndPaletteEngine.computePartA(pixels, size, size, inputs, generateMaskBitmap = false)
        val t1 = System.currentTimeMillis()

        val mask = PrintAreaAndPaletteEngine.computePrintMask(pixels, size, size, inputs)
        val masked = pixels.filterIndexed { i, _ -> mask[i] }.toIntArray()
        val t2 = System.currentTimeMillis()
        val partB = SeparationStrategyEngine.evaluateStrategies(masked, partA, inputs, processInks)
        val t3 = System.currentTimeMillis()
        val partC = SeparationStrategyEngine.generatePartC(
            pixels, size, size, null, partA, partB,
            partB.strategies.first { it.isRecommended }.strategyType, inputs, processInks
        )
        val t4 = System.currentTimeMillis()

        println("=== PHOTO-LIKE 1024 px BENCHMARK (laptop JVM, not a phone) ===")
        println("Unique colours in print: " + masked.toSet().size + " of " + masked.size + " print pixels")
        println("Part A (mask, box, palette): " + (t1 - t0) + " ms")
        println("Part B (strategy evaluation): " + (t3 - t2) + " ms")
        println("Part C (screens and proof):  " + (t4 - t3) + " ms")
        println("Palette size: " + partA.measuredPalette.size + ", recommended: " +
            partB.strategies.first { it.isRecommended }.name)

        assertTrue(partA.printPixelCount > 0)
        assertTrue(partB.strategies.isNotEmpty())
        assertTrue(partC.channels.isNotEmpty())
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
