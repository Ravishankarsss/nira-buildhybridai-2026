package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.EdgeBlendRules
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** print_rules.json "edge_blend" on synthetic striped prints. All limits come from the rules file. */
class EdgeBlendTest {

    private val root: JSONObject by lazy {
        val path = "src/main/assets/print_rules.json"
        JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText())
    }
    private val rules: EdgeBlendRules by lazy { PrintRulesRepository.parseEdgeBlendRules(root)!! }

    // ---------- rules and the small calculations ----------

    @Test
    fun rules_areReadFromTheFile() {
        val e = root.getJSONObject("edge_blend")
        assertEquals(e.getDouble("on_mixing_line_max_de00"), rules.onMixingLineMaxDeltaE00, 0.0)
        assertEquals(e.getDouble("band_max_px_from_measured_blur_factor"), rules.bandMaxPxFromMeasuredBlurFactor, 0.0)
        assertEquals(e.getDouble("band_max_px_min"), rules.bandMaxPxMin, 0.0)
        assertEquals(e.getDouble("band_max_px_cap"), rules.bandMaxPxCap, 0.0)
        assertEquals(e.getDouble("band_max_px_fallback"), rules.bandMaxPxFallback, 0.0)
        assertEquals(e.getDouble("band_max_px_artwork"), rules.bandMaxPxArtwork, 0.0)
        assertEquals(e.getDouble("sandwiched_min_fraction"), rules.sandwichedMinFraction, 0.0)
        assertEquals(e.getInt("plateau_min_px"), rules.plateauMinPx)
        assertEquals(e.getDouble("plateau_ratio_tolerance"), rules.plateauRatioTolerance, 0.0)
        assertEquals(e.getBoolean("monotonic_required"), rules.monotonicRequired)
        // The two numbers are the same as in Ravi's sentence.
        assertTrue(e.getString("plateau_keep_rule").contains("at least 2 consecutive pixels"))
        assertTrue(e.getString("plateau_keep_rule").contains("within 0.1"))
        assertNull(PrintRulesRepository.parseEdgeBlendRules(JSONObject("{}")))
    }

    @Test
    fun crossing_blurHasNoFlatMiddle_aPrintedLineHasOne() {
        assertTrue(EdgeBlend.crossingLooksLikeBlur(listOf(0.25, 0.5, 0.75), rules))
        assertTrue(EdgeBlend.crossingLooksLikeBlur(listOf(0.5), rules)) // 1 px: no room for a flat middle
        assertFalse(EdgeBlend.crossingLooksLikeBlur(listOf(0.2, 0.5, 0.52, 0.8), rules)) // flat middle
        assertFalse(EdgeBlend.crossingLooksLikeBlur(listOf(0.5, 0.5, 0.5), rules)) // a 3 px keyline
        assertFalse(EdgeBlend.crossingLooksLikeBlur(listOf(0.2, 0.7, 0.35), rules)) // turns back
    }

    @Test
    fun bandLimit_followsTheMeasuredBlur_withinMinAndCap() {
        assertEquals(rules.bandMaxPxArtwork, EdgeBlend.bandMaxPx(false, 3.0, rules), 0.0)
        assertEquals(rules.bandMaxPxFallback, EdgeBlend.bandMaxPx(true, null, rules), 0.0)
        assertEquals(3.0 * rules.bandMaxPxFromMeasuredBlurFactor, EdgeBlend.bandMaxPx(true, 3.0, rules), 1e-9)
        assertEquals(rules.bandMaxPxCap, EdgeBlend.bandMaxPx(true, 50.0, rules), 0.0)
        assertEquals(rules.bandMaxPxMin, EdgeBlend.bandMaxPx(true, 0.1, rules), 0.0)
    }

    @Test
    fun mixingLine_midpointIsOnTheLine_aDifferentColourIsNot() {
        val a = ColorConverter.Rgb(160, 200, 220)
        val b = ColorConverter.Rgb(20, 40, 90)
        val mid = mix(a, b, 0.5)
        val fit = EdgeBlend.mixFit(mid, a, b)
        assertEquals(0.5, fit.t, 0.02)
        assertTrue(fit.deltaE00 <= rules.onMixingLineMaxDeltaE00)
        val red = EdgeBlend.mixFit(ColorConverter.Rgb(200, 40, 40), a, b)
        assertTrue(red.deltaE00 > rules.onMixingLineMaxDeltaE00)
    }

    // ---------- Part A on striped prints ----------

    private val garment = ColorConverter.labToRgb(93.0, 0.0, 0.0)
    private val sky = ColorConverter.labToRgb(75.0, -8.0, -14.0)
    private val inkBlue = ColorConverter.labToRgb(18.0, 8.0, -35.0)

    private fun lin(v: Int) = ColorConverter.sRgbToLinear(v)
    private fun mix(a: ColorConverter.Rgb, b: ColorConverter.Rgb, t: Double) = ColorConverter.Rgb(
        ColorConverter.linearToSRgb(lin(a.r) + t * (lin(b.r) - lin(a.r))),
        ColorConverter.linearToSRgb(lin(a.g) + t * (lin(b.g) - lin(a.g))),
        ColorConverter.linearToSRgb(lin(a.b) + t * (lin(b.b) - lin(a.b)))
    )
    private fun argb(c: ColorConverter.Rgb) = (0xFF shl 24) or (c.r shl 16) or (c.g shl 8) or c.b

    private class Img(val px: IntArray, val w: Int, val h: Int, val printWidthPx: Int)

    /**
     * Vertical stripes of sky and ink blue (12 px each) in a 400 x 300 print on a light garment.
     * [keylinePx] puts a line of the exact midpoint colour between stripes; [antiAliasPx] puts the
     * midpoint colour as anti-aliasing.
     */
    private fun stripes(keylinePx: Int = 0, antiAliasPx: Int = 0): Img {
        val w = 600
        val h = 500
        val px = IntArray(w * h) { argb(garment) }
        val mid = mix(sky, inkBlue, 0.5)
        val period = 12 + keylinePx + antiAliasPx
        for (y in 100 until 400) for (x in 100 until 500) {
            val k = (x - 100) % (2 * period)
            val inFirst = k < period
            val j = if (inFirst) k else k - period
            px[y * w + x] = argb(
                when {
                    j < 12 -> if (inFirst) sky else inkBlue
                    else -> mid // keyline or anti-aliasing after each stripe
                }
            )
        }
        return Img(px, w, h, 400)
    }

    /** Camera blur: a lens mixes light, so a 3 x 3 box in linear light, [passes] times; then a little noise. */
    private fun asPhoto(img: Img, passes: Int = 2): Img {
        var cur = Array(3) { ch -> DoubleArray(img.px.size) { i -> lin((img.px[i] shr (16 - 8 * ch)) and 0xFF) } }
        repeat(passes) {
            val next = Array(3) { DoubleArray(img.px.size) }
            for (y in 0 until img.h) for (x in 0 until img.w) {
                val sum = DoubleArray(3)
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= img.w || yy >= img.h) continue
                    for (ch in 0..2) sum[ch] += cur[ch][yy * img.w + xx]
                    n++
                }
                for (ch in 0..2) next[ch][y * img.w + x] = sum[ch] / n
            }
            cur = next
        }
        val blurred = IntArray(img.px.size) { i ->
            (0xFF shl 24) or (ColorConverter.linearToSRgb(cur[0][i]) shl 16) or
                (ColorConverter.linearToSRgb(cur[1][i]) shl 8) or ColorConverter.linearToSRgb(cur[2][i])
        }
        return addNoise(Img(blurred, img.w, img.h, img.printWidthPx))
    }

    private fun addNoise(img: Img): Img {
        val cur = img.px
        val rng = Random(5)
        val noisy = IntArray(cur.size) { i ->
            val c = cur[i]
            fun ch(v: Int) = (v + rng.nextInt(-2, 3)).coerceIn(0, 255)
            (0xFF shl 24) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
        }
        return Img(noisy, img.w, img.h, img.printWidthPx)
    }

    /** Half size by averaging 2 x 2 blocks. */
    private fun half(img: Img): Img {
        val w = img.w / 2
        val h = img.h / 2
        val px = IntArray(w * h) { i ->
            val x = (i % w) * 2
            val y = (i / w) * 2
            var r = 0; var g = 0; var b = 0
            for ((dx, dy) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) {
                val c = img.px[(y + dy) * img.w + x + dx]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            (0xFF shl 24) or ((r / 4) shl 16) or ((g / 4) shl 8) or (b / 4)
        }
        return Img(px, w, h, img.printWidthPx / 2)
    }

    private fun partA(
        img: Img,
        kind: InputKind,
        pxPerCm: Double,
        edgeRules: EdgeBlendRules? = rules,
        keep: Boolean = false,
        answer: Boolean? = null
    ): PartAResult = PrintAreaAndPaletteEngine.computePartA(
        img.px, img.w, img.h,
        PrintJobInputs(
            inputKind = kind, cameraExif = kind == InputKind.PHOTO, printWidthCm = img.printWidthPx / pxPerCm,
            keepEdgeColours = keep, edgeBlendAnswer = answer
        ),
        generateMaskBitmap = false,
        decisionRules = PrintRulesRepository.parseSeparationDecisionRules(root),
        maskRules = PrintRulesRepository.parseGarmentMaskRules(root),
        photoRules = PrintRulesRepository.parsePhotoVsArtworkRules(root),
        shadowRules = PrintRulesRepository.parseShadowVsInkRules(root),
        edgeBlendRules = edgeRules
    )

    private fun hexes(a: PartAResult) = a.measuredPalette.map { it.hex }

    @Test
    fun blurredPhotoEdge_givesTwoColours() {
        val photo = asPhoto(stripes())
        val a = partA(photo, InputKind.PHOTO, pxPerCm = 120.0)
        assertEquals("colours ${hexes(a)}", 2, a.measuredPalette.size)
        assertTrue(a.edgeBlendPixelsMerged > 0)
        assertTrue(a.measuredPalette.none { it.possibleEdgeBlend })
    }

    @Test
    fun blurAtTwoSizes_givesTwoColoursBothTimes() {
        val full = asPhoto(stripes())
        val halfSize = half(full)
        // Same print: 120 px/cm at full size, 60 px/cm at half size (both above the minimum).
        val a = partA(full, InputKind.PHOTO, pxPerCm = 120.0)
        val b = partA(halfSize, InputKind.PHOTO, pxPerCm = 60.0)
        assertEquals("full ${hexes(a)}", 2, a.measuredPalette.size)
        assertEquals("half ${hexes(b)}", 2, b.measuredPalette.size)
    }

    @Test
    fun midToneKeylineWithAFlatMiddle_staysThreeInks() {
        val art = stripes(keylinePx = 3)
        val a = partA(art, InputKind.ARTWORK, pxPerCm = 120.0)
        assertEquals("colours ${hexes(a)}", 3, a.measuredPalette.size)
        assertEquals(0, a.edgeBlendPixelsMerged)
    }

    @Test
    fun artworkAntiAliasing_isMerged_butAThickerBandIsKept() {
        val aa = partA(stripes(antiAliasPx = 1), InputKind.ARTWORK, pxPerCm = 120.0)
        assertEquals("1 px ${hexes(aa)}", 2, aa.measuredPalette.size)
        assertTrue(aa.edgeBlendPixelsMerged > 0)
        val thick = partA(stripes(antiAliasPx = 3), InputKind.ARTWORK, pxPerCm = 120.0)
        assertEquals("3 px ${hexes(thick)}", 3, thick.measuredPalette.size)
    }

    @Test
    fun lowResolution_flagsTheCandidate_insteadOfMerging_untilTheUserAnswers() {
        val photo = asPhoto(stripes())
        val low = partA(photo, InputKind.PHOTO, pxPerCm = 40.0) // below the 59 px/cm minimum
        assertEquals(0, low.edgeBlendPixelsMerged)
        assertTrue("colours ${hexes(low)}", low.measuredPalette.any { it.possibleEdgeBlend })

        val merged = partA(photo, InputKind.PHOTO, pxPerCm = 40.0, answer = true)
        assertTrue(merged.edgeBlendPixelsMerged > 0)
        assertTrue(merged.measuredPalette.none { it.possibleEdgeBlend })

        val kept = partA(photo, InputKind.PHOTO, pxPerCm = 40.0, answer = false)
        assertEquals(0, kept.edgeBlendPixelsMerged)
        assertTrue(kept.measuredPalette.none { it.possibleEdgeBlend })
    }

    @Test
    fun undoSwitch_restoresTheOriginalColoursExactly() {
        val photo = asPhoto(stripes())
        val without = partA(photo, InputKind.PHOTO, pxPerCm = 120.0, edgeRules = null)
        val kept = partA(photo, InputKind.PHOTO, pxPerCm = 120.0, keep = true)
        assertTrue("before undo ${hexes(without)}", without.measuredPalette.size > 2)
        assertEquals(hexes(without), hexes(kept))
        assertEquals(without.measuredPalette.map { it.pixelCount }, kept.measuredPalette.map { it.pixelCount })
        assertEquals(0, kept.edgeBlendPixelsMerged)
    }
}
