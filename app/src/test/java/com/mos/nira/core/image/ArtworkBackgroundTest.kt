package com.mos.nira.core.image

import com.mos.nira.TestRules
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** print_rules.json "artwork_background", and the bunny all-over-print regression case (18 Nov 2026). */
class ArtworkBackgroundTest {

    private val rules = PrintRulesRepository.parseArtworkBackgroundRules(TestRules.root)!!
    private val size = 600
    private val pxPerCm = 60.0

    private fun rgb(hex: String): Int = (0xFF shl 24) or ColorConverter.hexToRgb(hex).let { (it.r shl 16) or (it.g shl 8) or it.b }
    private fun lab(hex: String) = ColorConverter.hexToRgb(hex).let { ColorConverter.rgbToLab(it.r, it.g, it.b) }
    private fun lab(r: ColorConverter.Rgb) = ColorConverter.rgbToLab(r.r, r.g, r.b)

    private fun partA(px: IntArray, kind: InputKind, answer: Boolean? = null, printWidthPx: Int = 400): PartAResult =
        PrintAreaAndPaletteEngine.computePartA(
            px, size, size,
            PrintJobInputs(inputKind = kind, cameraExif = kind == InputKind.PHOTO, printWidthCm = printWidthPx / pxPerCm, artworkBackgroundAnswer = answer),
            generateMaskBitmap = false,
            decisionRules = PrintRulesRepository.parseSeparationDecisionRules(TestRules.root),
            maskRules = PrintRulesRepository.parseGarmentMaskRules(TestRules.root),
            smallColourRules = PrintRulesRepository.parseSmallColourRules(TestRules.root),
            artworkBackgroundRules = rules
        )

    private val beige = "#E8DCC4"
    private val navy = "#1F2A5C"
    private val red = "#C0392B"

    /** Beige paper with navy text blocks and a red block in the middle. */
    private val plainBackgroundArtwork = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        when {
            x in 120 until 480 && y in 200 until 260 -> rgb(navy)
            x in 120 until 480 && y in 300 until 330 -> rgb(navy)
            x in 260 until 340 && y in 360 until 420 -> rgb(red)
            else -> rgb(beige)
        }
    }

    @Test
    fun rules_areReadFromTheFile() {
        val a = TestRules.root.getJSONObject("artwork_background")
        assertEquals(a.getDouble("border_share_min"), rules.borderShareMin, 0.0)
        assertEquals(a.getDouble("flat_spread_max_de00"), rules.flatSpreadMaxDeltaE00, 0.0)
    }

    @Test
    fun plainBackground_isFound_andLeftOutUntilTheUserSaysPrintIt() {
        val a = partA(plainBackgroundArtwork, InputKind.ARTWORK)
        val bg = assertNotNull(a.artworkBackground).let { a.artworkBackground!! }
        assertTrue(ColorConverter.deltaE00(lab(bg.hex), lab(beige)) < 3.0)
        assertFalse(bg.printed)
        assertTrue("border share ${bg.borderSharePercent}", bg.borderSharePercent >= rules.borderShareMin * 100)
        assertTrue("image share ${bg.imageCoveragePercent}", bg.imageCoveragePercent > 80.0)
        // Left out: only navy and red are print colours.
        assertEquals("colours ${a.measuredPalette.map { it.hex }}", 2, a.measuredPalette.size)
        assertTrue(a.measuredPalette.none { ColorConverter.deltaE00(lab(it.rgb), lab(beige)) < 5.0 })

        // "Print it": the background is a measured print colour too.
        val printed = partA(plainBackgroundArtwork, InputKind.ARTWORK, answer = true)
        assertTrue(printed.artworkBackground!!.printed)
        assertEquals("colours ${printed.measuredPalette.map { it.hex }}", 3, printed.measuredPalette.size)
        assertTrue(printed.measuredPalette.any { ColorConverter.deltaE00(lab(it.rgb), lab(beige)) < 5.0 })
    }

    @Test
    fun aGarmentPhoto_isNeverAskedAboutBackground() {
        assertNull(partA(plainBackgroundArtwork, InputKind.PHOTO).artworkBackground)
    }

    @Test
    fun artworkWithNoPlainBorder_isNotAsked() {
        // Stripes of four colours to the edge: no one colour holds the border.
        val stripes = IntArray(size * size) { i -> rgb(listOf(navy, red, beige, "#2E8B57")[(i % size) / 150]) }
        assertNull(partA(stripes, InputKind.ARTWORK).artworkBackground)
    }

    // ---------- regression: bunny all-over print on pink single jersey (Ravi, 18 Nov 2026) ----------
    // Measured from Ravi's photo (kept outside the repository): fabric pink #D0859C about 91%,
    // white #E4E1DE 6.4%, light pink #DCB8C8 1.8%, dark red #9B3C3C 0.5%, black-brown #3D353A 0.25%.

    @Test
    fun bunnyAop_pinkGroundIsTheFabric_notAPrintColour() {
        val ground = "#D0859C"
        val rng = Random(11)
        val shapes = listOf(
            Triple("#E4E1DE", intArrayOf(100, 100, 250, 250), "white"),     // 22,500 px = 6.25%
            Triple("#DCB8C8", intArrayOf(300, 100, 380, 180), "light pink"), // 6,400 px = 1.8%
            Triple("#9B3C3C", intArrayOf(420, 400, 462, 442), "dark red"),   // 1,764 px = 0.5%
            Triple("#3D353A", intArrayOf(150, 420, 180, 450), "black-brown") // 900 px = 0.25%
        )
        val px = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            val hex = shapes.firstOrNull { (_, b, _) -> x in b[0] until b[2] && y in b[1] until b[3] }?.first ?: ground
            val c = ColorConverter.hexToRgb(hex)
            fun ch(v: Int) = (v + rng.nextInt(-3, 4)).coerceIn(0, 255) // knit texture and sensor noise
            (0xFF shl 24) or (ch(c.r) shl 16) or (ch(c.g) shl 8) or ch(c.b)
        }
        val a = partA(px, InputKind.PHOTO, printWidthPx = 600)
        // The fabric is the pink ground...
        assertTrue("garment ${a.garmentProfile?.estimatedHex}", ColorConverter.deltaE00(a.garmentProfile!!.medianLab, lab(ground)) < 3.0)
        // ...so it is not a print colour, and the white bunny is.
        assertTrue("colours ${a.measuredPalette.map { it.hex }}", a.measuredPalette.none { ColorConverter.deltaE00(lab(it.rgb), lab(ground)) < 5.0 })
        assertTrue(a.measuredPalette.any { ColorConverter.deltaE00(lab(it.rgb), lab("#E4E1DE")) < 5.0 })
        // Print coverage is about 9% of the fabric, not 100%.
        val printedShare = a.printPixelCount * 100.0 / (size * size)
        assertTrue("print share $printedShare", printedShare in 6.0..12.0)
    }
}
