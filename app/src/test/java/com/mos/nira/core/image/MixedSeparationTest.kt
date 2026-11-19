package com.mos.nira.core.image

import com.mos.nira.TestRules
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.data.ProcessInksConfig
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mixed_separation: CMYK for the picture plus spot screens for solid colours, and near-black
 * text from the K screen. Synthetic artwork: a rainbow picture with a solid navy block and a
 * solid near-black block (the "text") on a white garment.
 */
class MixedSeparationTest {

    private val rules = PrintRulesRepository.parseMixedSeparationRules(TestRules.root)!!
    private val decision = PrintRulesRepository.parseSeparationDecisionRules(TestRules.root)
    private val processInks = ProcessInksConfig()

    private val w = 120
    private val h = 100
    private val navy = 0xFF1F2A5C.toInt()
    private val nearBlack = 0xFF201C1E.toInt()

    /** Columns 0-79: hue (red to cyan) across, lightness down (the picture). 80-99 navy, 100-119 near-black. */
    private fun artwork(): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        when {
            x >= 100 -> nearBlack
            x >= 80 -> navy
            else -> {
                val hue = x / 80f * 190f // red to cyan: keeps the picture away from the navy text
                val v = 0.95f - y / h.toFloat() * 0.25f
                hsv(hue, 0.85f, v)
            }
        }
    }

    /** Plain HSV to ARGB (no Android classes, so the test really runs on the JVM). */
    private fun hsv(hDeg: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = hDeg / 60f
        val x = c * (1 - kotlin.math.abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }

    @Test
    fun rulesLoadFromTheRealFile() {
        assertTrue(rules.solidCoreDeltaE00 > 0)
        assertTrue(rules.solidMinShare in 0.0..1.0)
        assertTrue(rules.blackFromKMaxDeltaE00 > 0)
        assertEquals("assumption", rules.confidence)
        assertNull(PrintRulesRepository.parseMixedSeparationRules(JSONObject("{}")))
    }

    @Test
    fun solidColoursAreFoundAndPictureColoursAreNot() {
        val solidNavy = ColorConverter.rgbToLab(0x1F, 0x2A, 0x5C)
        val pictureRed = ColorConverter.rgbToLab(230, 40, 40)
        val bins = HashMap<Int, SeparationStrategyEngine.QuantizedBin>()
        // Navy: all pixels exactly navy. Red: spread widely around its centre.
        bins[1] = SeparationStrategyEngine.QuantizedBin(0x1F, 0x2A, 0x5C, 500)
        var k = 2
        for (dr in -60..60 step 20) for (dg in -40..40 step 20) {
            bins[k++] = SeparationStrategyEngine.QuantizedBin((230 + dr).coerceIn(0, 255), (40 + dg).coerceIn(0, 255), 40, 20)
        }
        fun mc(id: Int, lab: ColorConverter.Lab, hex: String) = com.mos.nira.features.artwork.domain.MeasuredColor(
            id, hex, ColorConverter.hexToRgb(hex), lab, 100, 50.0, 50.0, 1.0
        )
        val solids = SeparationStrategyEngine.solidColours(
            listOf(mc(1, solidNavy, "#1F2A5C"), mc(2, pictureRed, "#E62828")), bins, rules.solidCoreDeltaE00, rules.solidMinShare
        )
        assertEquals(listOf("#1F2A5C"), solids.map { it.hex })
    }

    @Test
    fun mixedOptionUsesSpotForNavyAndKForBlack() {
        val px = artwork()
        val inputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#FFFFFF", targetGarmentHex = "#FFFFFF")
        val partA = PrintAreaAndPaletteEngine.computePartA(px, w, h, inputs, generateMaskBitmap = false, decisionRules = decision)
        val partB = SeparationStrategyEngine.evaluateStrategies(px, partA, inputs, processInks, decision, mixedRules = rules)
        val mixed = partB.strategies.find { it.strategyType == SeparationStrategyType.MIXED_CMYK_SPOT }
        assertNotNull(mixed)
        assertEquals(1, mixed!!.spotHexes.size)
        assertEquals(1, mixed.blackFromKHexes.size)
        assertEquals(5, mixed.screenCount)
        // prefer_over_cmyk: the mixed option is recommended instead of plain CMYK.
        val recommended = partB.strategies.single { it.isRecommended }
        assertEquals(SeparationStrategyType.MIXED_CMYK_SPOT, recommended.strategyType)
        assertTrue(recommended.recommendationReason.contains("misregistration"))

        // Part C: navy on its own solid screen, near-black on K, the picture on C, M, Y, K.
        val partC = SeparationStrategyEngine.generatePartC(
            px, w, h, null, partA, partB, SeparationStrategyType.MIXED_CMYK_SPOT, inputs, processInks, decision
        )
        assertEquals(listOf("Cyan", "Magenta", "Yellow", "Black"), partC.channels.take(4).map { it.inkName })
        assertEquals(5, partC.channels.size)
        val navyCh = partC.channels[4]
        val kCh = partC.channels[3]
        fun inkAt(ch: com.mos.nira.features.artwork.domain.FilmPositiveChannel, x: Int, y: Int) =
            1.0 - (ch.rawGreyscalePixels!![y * w + x] and 0xFF) / 255.0
        // Middle of the navy block: 100% navy, no process ink.
        assertEquals(1.0, inkAt(navyCh, 90, 50), 1e-9)
        for (c in 0 until 4) assertEquals(0.0, inkAt(partC.channels[c], 90, 50), 1e-9)
        // Middle of the near-black block: 100% K, nothing on the navy screen.
        assertEquals(1.0, inkAt(kCh, 110, 50), 1e-9)
        assertEquals(0.0, inkAt(navyCh, 110, 50), 1e-9)
        // The picture: process ink, never the navy screen.
        assertEquals(0.0, inkAt(navyCh, 40, 50), 1e-9)
        assertTrue((0 until 4).sumOf { inkAt(partC.channels[it], 40, 50) } > 0.1)
    }

    @Test
    fun withoutTheRulesTheMixedOptionIsNotOffered() {
        val px = artwork()
        val inputs = PrintJobInputs(printWidthCm = 30.0, garmentColorHex = "#FFFFFF", targetGarmentHex = "#FFFFFF")
        val partA = PrintAreaAndPaletteEngine.computePartA(px, w, h, inputs, generateMaskBitmap = false, decisionRules = decision)
        val partB = SeparationStrategyEngine.evaluateStrategies(px, partA, inputs, processInks, decision, mixedRules = null)
        assertTrue(partB.strategies.none { it.strategyType == SeparationStrategyType.MIXED_CMYK_SPOT })
    }
}
