package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.ProcessInksConfig
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for NIRA Print Report — Part B:
 * - Absorbance calculation & proof reconstruction exp(-A) -> sRGB
 * - Lawson-Hanson Non-Negative Least Squares (NNLS) solver for 3xN systems
 * - Strategy evaluation (CMYK, Best N Spot, Flat Spot, Extended Spot)
 * - Three quality figures per strategy: Mean ΔE00, 95th Percentile, % within 3.0 ΔE00
 * - Dark garment underbase white & highlight white screen rules
 * - 1024 px runtime performance benchmark
 */
class SeparationStrategyEngineTest {

    @Test
    fun testAbsorbanceConversionAndReconstruction() {
        val testColors = listOf(
            Triple(255, 0, 0),    // Pure Red
            Triple(0, 255, 0),    // Pure Green
            Triple(0, 0, 255),    // Pure Blue
            Triple(0, 174, 239),  // Process Cyan
            Triple(236, 0, 140),  // Process Magenta
            Triple(255, 242, 0),  // Process Yellow
            Triple(128, 128, 128) // Neutral Grey
        )

        for ((r, g, b) in testColors) {
            val absorbance = SeparationStrategyEngine.rgbToAbsorbance(r, g, b)
            assertTrue("Absorbance must be non-negative", absorbance.all { it >= 0.0 })

            val expectedR = if (r < 13) 13 else r
            val expectedG = if (g < 13) 13 else g
            val expectedB = if (b < 13) 13 else b

            val reconstructed = SeparationStrategyEngine.absorbanceToRgb(absorbance)
            assertTrue("R channel should match original or clip bound", abs(reconstructed.r - expectedR) <= 2)
            assertTrue("G channel should match original or clip bound", abs(reconstructed.g - expectedG) <= 2)
            assertTrue("B channel should match original or clip bound", abs(reconstructed.b - expectedB) <= 2)
        }
    }

    @Test
    fun testLawsonHansonNnlsSolverNonNegativity() {
        // Construct 3 ink absorbance vectors
        val ink1 = doubleArrayOf(1.5, 0.1, 0.0) // Cyan-ish
        val ink2 = doubleArrayOf(0.1, 1.8, 0.2) // Magenta-ish
        val ink3 = doubleArrayOf(0.0, 0.1, 2.0) // Yellow-ish
        val aCols = arrayOf(ink1, ink2, ink3)

        // Target: 0.7 * ink1 + 0.4 * ink2 + 0.0 * ink3
        val targetY = doubleArrayOf(
            0.7 * ink1[0] + 0.4 * ink2[0],
            0.7 * ink1[1] + 0.4 * ink2[1],
            0.7 * ink1[2] + 0.4 * ink2[2]
        )

        val solution = LawsonHansonNnls.solve(aCols, targetY)

        assertEquals(3, solution.size)
        assertTrue("Solution amounts must be >= 0", solution.all { it >= 0.0 })
        assertEquals(0.7, solution[0], 0.02)
        assertEquals(0.4, solution[1], 0.02)
        assertEquals(0.0, solution[2], 0.02)
    }

    @Test
    fun testStrategyComparisonTableAndQualityFigures() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total)

        // Palette of 4 distinct test colors
        val colors = intArrayOf(
            0xFFFF2200.toInt(), // Warm Red
            0xFF00AA44.toInt(), // Green
            0xFF0066CC.toInt(), // Blue
            0xFFFFCC00.toInt()  // Gold
        )

        for (i in 0 until total) {
            pixels[i] = colors[(i / (total / 4)).coerceIn(0, 3)]
        }

        val inputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#FFFFFF")
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        val processInks = ProcessInksConfig(
            cyanHex = "#00AEEF",
            magentaHex = "#EC008C",
            yellowHex = "#FFF200",
            blackHex = "#231F20"
        )

        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels,
            partA = partA,
            inputs = inputs,
            processInks = processInks
        )

        assertTrue("Should evaluate at least 3 strategies", partB.strategies.size >= 3)
        assertEquals(SeparationStrategyEngine.MODEL_DISCLAIMER, partB.disclaimer)

        for (strategy in partB.strategies) {
            assertTrue("Screen count must be >= 2", strategy.screenCount >= 2)
            assertTrue("Mean error must be non-negative", strategy.meanDeltaE00 >= 0.0)
            assertTrue("95th percentile must be >= mean error", strategy.percentile95DeltaE00 >= strategy.meanDeltaE00)
            assertTrue("Percent within 3.0 must be in [0, 100]", strategy.percentWithinDeltaE3 in 0.0..100.0)
        }

        // Verify CMYK strategy is present
        val cmyk = partB.strategies.find { it.strategyType == SeparationStrategyType.CMYK_PROCESS }
        assertNotNull(cmyk)
        assertEquals(4, cmyk?.screenCount) // On white garment, standard 4 screens
    }

    @Test
    fun testDarkGarmentRulesAndWhitesAddition() {
        val width = 50
        val height = 50
        val total = width * height
        val pixels = IntArray(total) { 0xFFFF5500.toInt() } // Bright Orange

        // Black garment base (#000000)
        val darkInputs = PrintJobInputs(garmentColorHex = "#000000")
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = darkInputs,
            generateMaskBitmap = false
        )

        val processInks = ProcessInksConfig()
        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels,
            partA = partA,
            inputs = darkInputs,
            processInks = processInks
        )

        assertTrue("Should detect dark garment", partB.isDarkGarment)

        val cmyk = partB.strategies.find { it.strategyType == SeparationStrategyType.CMYK_PROCESS }
        assertNotNull(cmyk)
        // On dark garment, CMYK has 4 inks + Base White + Highlight White = 6 screens
        assertEquals(6, cmyk?.screenCount)
        assertNotNull(cmyk?.darkGarmentWarning)
        assertTrue(cmyk?.darkGarmentWarning?.contains("CMYK alone is not suitable") == true)
    }

    @Test
    fun testPerformanceBenchmarkOn1024pxImage() {
        val size = 1024
        val totalPixels = size * size
        val pixels = IntArray(totalPixels)

        // Generate varied test image with 8 colorful stripes
        val stripeColors = intArrayOf(
            0xFFFF1744.toInt(),
            0xFF2979FF.toInt(),
            0xFF00E676.toInt(),
            0xFFFFEA00.toInt(),
            0xFFFF9100.toInt(),
            0xFFD500F9.toInt(),
            0xFF00B0FF.toInt(),
            0xFF37474F.toInt()
        )

        for (i in 0 until totalPixels) {
            val stripeIndex = ((i / size) / (size / 8)).coerceIn(0, 7)
            pixels[i] = stripeColors[stripeIndex]
        }

        val inputs = PrintJobInputs(printWidthCm = 35.0, garmentColorHex = "#FFFFFF")

        // 1. Benchmark Part A
        val startA = System.currentTimeMillis()
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = size,
            height = size,
            inputs = inputs,
            generateMaskBitmap = false
        )
        val timeA = System.currentTimeMillis() - startA

        assertEquals(8, partA.measuredPalette.size)
        assertTrue("Part A on 1024px must execute under 5000ms", timeA < 5000)

        // 2. Benchmark Part B
        val startB = System.currentTimeMillis()
        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels,
            partA = partA,
            inputs = inputs,
            processInks = ProcessInksConfig()
        )
        val timeB = System.currentTimeMillis() - startB

        assertTrue("Part B strategies must be evaluated", partB.strategies.isNotEmpty())
        assertTrue("Part B with quantization on 1024px must execute under 3000ms", timeB < 3000)

        println("=== REAL 1024px BENCHMARK RESULTS ===")
        println("Pixels: $totalPixels (1024 x 1024)")
        println("Part A Execution Time: ${timeA} ms")
        println("Part B Execution Time: ${timeB} ms")
        println("Part B Engine Reported Time: ${partB.executionTimeMs} ms")

        // 3. Benchmark Part C
        val startC = System.currentTimeMillis()
        val partC = SeparationStrategyEngine.generatePartC(
            pixels = pixels,
            width = size,
            height = size,
            originalBitmap = null,
            partA = partA,
            partB = partB,
            chosenStrategyType = SeparationStrategyType.CMYK_PROCESS,
            inputs = inputs,
            processInks = ProcessInksConfig()
        )
        val timeC = System.currentTimeMillis() - startC

        assertTrue("Part C channels must be generated", partC.channels.isNotEmpty())
        assertTrue("Part C on 1024px must execute under 3000ms", timeC < 3000)
        println("Part C Execution Time: ${timeC} ms")
        println("Part C Engine Reported Time: ${partC.executionTimeMs} ms")
    }

    @Test
    fun testPartCFilmPositiveGenerationCMYK() {
        val width = 100
        val height = 100
        val total = width * height
        val pixels = IntArray(total)

        // Pure Process Cyan `#00AEEF` = RGB(0, 174, 239) = 0xFF00AEEF
        val cyanColor = 0xFF00AEEF.toInt()
        pixels.fill(cyanColor)

        val inputs = PrintJobInputs(printWidthCm = 25.0, garmentColorHex = "#FFFFFF")
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        val processInks = ProcessInksConfig()
        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels,
            partA = partA,
            inputs = inputs,
            processInks = processInks
        )

        val partC = SeparationStrategyEngine.generatePartC(
            pixels = pixels,
            width = width,
            height = height,
            originalBitmap = null,
            partA = partA,
            partB = partB,
            chosenStrategyType = SeparationStrategyType.CMYK_PROCESS,
            inputs = inputs,
            processInks = processInks
        )

        assertEquals(4, partC.channels.size)
        assertEquals(
            "Preliminary digital separation, not print-ready films. Model has no dot gain or registration error. Needs original artwork at about 300 dpi at print size.",
            partC.disclaimer
        )

        val cyanChannel = partC.channels.find { it.inkName == "Cyan" }
        assertNotNull(cyanChannel)
        val magentaChannel = partC.channels.find { it.inkName == "Magenta" }
        assertNotNull(magentaChannel)
        val yellowChannel = partC.channels.find { it.inkName == "Yellow" }
        assertNotNull(yellowChannel)
        val blackChannel = partC.channels.find { it.inkName == "Black" }
        assertNotNull(blackChannel)

        // Cyan should have high ink coverage; other process channels should have near zero coverage
        assertTrue("Cyan coverage should be > 80%", (cyanChannel?.averageCoveragePercent ?: 0.0) > 80.0)
        assertTrue("Magenta coverage should be < 5%", (magentaChannel?.averageCoveragePercent ?: 100.0) < 5.0)
        assertTrue("Yellow coverage should be < 5%", (yellowChannel?.averageCoveragePercent ?: 100.0) < 5.0)
        assertTrue("Black coverage should be < 5%", (blackChannel?.averageCoveragePercent ?: 100.0) < 5.0)

        // Film positive pixels: Black = 100% ink (low grey value), White = 0% ink (high grey value)
        val cyanRaw = cyanChannel?.rawGreyscalePixels
        assertNotNull(cyanRaw)
        val magentaRaw = magentaChannel?.rawGreyscalePixels
        assertNotNull(magentaRaw)

        // On Cyan screen, the pixel should be dark (grey <= 50)
        val cyanSampleGrey = cyanRaw!![0] and 0xFF
        assertTrue("Cyan film positive must be dark (black = ink)", cyanSampleGrey <= 50)

        // On Magenta screen, the pixel should be white (grey >= 250)
        val magentaSampleGrey = magentaRaw!![0] and 0xFF
        assertTrue("Magenta film positive must be white (no ink)", magentaSampleGrey >= 250)
    }

    @Test
    fun testPartCDarkGarmentWhitesRule() {
        val width = 50
        val height = 50
        val total = width * height
        val pixels = IntArray(total)

        // Red logo on black garment
        pixels.fill(0xFFFF0000.toInt())

        val darkInputs = PrintJobInputs(printWidthCm = 20.0, garmentColorHex = "#000000")
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = darkInputs,
            generateMaskBitmap = false
        )

        val processInks = ProcessInksConfig()
        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels,
            partA = partA,
            inputs = darkInputs,
            processInks = processInks
        )

        val partC = SeparationStrategyEngine.generatePartC(
            pixels = pixels,
            width = width,
            height = height,
            originalBitmap = null,
            partA = partA,
            partB = partB,
            chosenStrategyType = SeparationStrategyType.CMYK_PROCESS,
            inputs = darkInputs,
            processInks = processInks,
            meshRules = com.mos.nira.TestRules.meshRules
        )

        // On dark garment, CMYK has 4 inks + Base White + Highlight White = 6 screens
        assertEquals(6, partC.channels.size)

        val baseWhite = partC.channels.find { it.inkName.contains("Base White") }
        assertNotNull("Base white underbase must be included on dark garment", baseWhite)
        // Mesh ranges come from print_rules.json (screen_type_mesh_rule -> mesh_rules).
        val types = com.mos.nira.TestRules.meshRules.byScreenType
        val ub = types.getValue(com.mos.nira.features.artwork.data.ScreenType.UNDERBASE_WHITE)
        assertEquals("${ub.tpiMin}–${ub.tpiMax} tpi", baseWhite?.recommendedMesh)
        assertEquals(ub.id, baseWhite?.meshRuleId)
        assertTrue("Base white coverage should be > 90%", (baseWhite?.averageCoveragePercent ?: 0.0) > 90.0)

        val hiWhite = partC.channels.find { it.inkName.contains("Highlight White") }
        assertNotNull("Highlight white must be included on dark garment", hiWhite)
        val hw = types.getValue(com.mos.nira.features.artwork.data.ScreenType.HIGHLIGHT_WHITE)
        assertEquals("${hw.tpiMin}–${hw.tpiMax} tpi", hiWhite?.recommendedMesh)
    }

    @Test
    fun testPartCFlatSpotSolidBinaryScreens() {
        val width = 60
        val height = 60
        val total = width * height
        val pixels = IntArray(total)

        // Half Blue, Half Yellow
        for (i in 0 until total) {
            pixels[i] = if (i < total / 2) 0xFF0000FF.toInt() else 0xFFFFFF00.toInt()
        }

        val inputs = PrintJobInputs(printWidthCm = 20.0, garmentColorHex = "#FFFFFF")
        val partA = PrintAreaAndPaletteEngine.computePartA(
            pixels = pixels,
            width = width,
            height = height,
            inputs = inputs,
            generateMaskBitmap = false
        )

        val processInks = ProcessInksConfig()
        val partB = SeparationStrategyEngine.evaluateStrategies(
            maskedPixels = pixels,
            partA = partA,
            inputs = inputs,
            processInks = processInks
        )

        val partC = SeparationStrategyEngine.generatePartC(
            pixels = pixels,
            width = width,
            height = height,
            originalBitmap = null,
            partA = partA,
            partB = partB,
            chosenStrategyType = SeparationStrategyType.FLAT_SPOT_SOLID,
            inputs = inputs,
            processInks = processInks
        )

        // Flat Spot channels are discrete solid screens
        for (channel in partC.channels) {
            val raw = channel.rawGreyscalePixels
            assertNotNull(raw)
            for (p in raw!!) {
                val grey = p and 0xFF
                // In solid screen, every pixel is either 100% ink (black = 0) or 0% ink (white = 255)
                assertTrue("Flat spot pixel must be solid black or white", grey == 0 || grey == 255)
            }
        }
    }
}
