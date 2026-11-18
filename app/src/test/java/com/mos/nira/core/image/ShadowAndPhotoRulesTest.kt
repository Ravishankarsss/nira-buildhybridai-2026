package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.PhotoVsArtworkRules
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.data.ShadowVsInkRules
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PrintJobInputs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * print_rules.json "shadow_vs_ink" and "photo_vs_artwork" on synthetic images.
 * All limits are read from the real rules file, never from Kotlin constants.
 */
class ShadowAndPhotoRulesTest {

    private val root: JSONObject by lazy {
        val path = "src/main/assets/print_rules.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        JSONObject(file.readText())
    }
    private val shadowRules: ShadowVsInkRules by lazy { PrintRulesRepository.parseShadowVsInkRules(root)!! }
    private val photoRules: PhotoVsArtworkRules by lazy { PrintRulesRepository.parsePhotoVsArtworkRules(root)!! }

    // ---------- loading ----------

    @Test
    fun rules_areReadFromTheFile_includingTheResolutionReference() {
        val s = root.getJSONObject("shadow_vs_ink")
        assertEquals(s.getDouble("ink_edge_max_mm"), shadowRules.inkEdgeMaxMm, 0.0)
        assertEquals(s.getDouble("shadow_edge_min_mm"), shadowRules.shadowEdgeMinMm, 0.0)
        assertEquals(s.getBoolean("shadow_if_touches_plain_fabric_or_leaves_print_box"), shadowRules.shadowIfTouchesPlainFabricOrLeavesPrintBox)
        assertEquals(s.getDouble("hue_test_only_if_garment_chroma_at_least"), shadowRules.hueTestOnlyIfGarmentChromaAtLeast, 0.0)
        assertEquals(s.getDouble("shadow_keeps_garment_hue_within_deg"), shadowRules.shadowKeepsGarmentHueWithinDeg, 0.0)
        assertEquals("resolution.px_per_cm_min_for_fine_detail", s.getString("min_px_per_cm_for_edge_test"))
        assertEquals(root.getJSONObject("resolution").getDouble("px_per_cm_min_for_fine_detail"), shadowRules.minPxPerCmForEdgeTest, 0.0)
        assertEquals("assumption", shadowRules.confidence)

        val p = root.getJSONObject("photo_vs_artwork")
        assertEquals(p.getDouble("artwork_flat_noise_L_std_max"), photoRules.artworkFlatNoiseLStdMax, 0.0)
        assertEquals(p.getDouble("photo_flat_noise_L_std_min"), photoRules.photoFlatNoiseLStdMin, 0.0)
        assertEquals(p.getDouble("photo_background_L_range_min"), photoRules.photoBackgroundLRangeMin, 0.0)
        assertEquals(p.getInt("ask_when_signals_disagree_with_mode"), photoRules.askWhenSignalsDisagreeWithMode)
        assertEquals("assumption", photoRules.confidence)
    }

    @Test
    fun missingSections_giveNoRules_insteadOfInventedNumbers() {
        assertNull(PrintRulesRepository.parseShadowVsInkRules(JSONObject("{}")))
        assertNull(PrintRulesRepository.parsePhotoVsArtworkRules(JSONObject("{}")))
        assertNull(PrintRulesRepository.parseShadowVsInkRules(JSONObject("""{"shadow_vs_ink":{"ink_edge_max_mm":0.5}}""")))
    }

    // ---------- shadow vs ink: synthetic light garment ----------

    private val size = 600
    private val pxPerCm = 60.0 // just above the 59 px/cm minimum in the file
    private fun mmToPx(mm: Double) = mm * pxPerCm / 10.0
    private val printBox = NormalizedRect(0.3f, 0.4f, 0.7f, 0.8f)

    private fun rgb(c: ColorConverter.Rgb) = (0xFF shl 24) or (c.r shl 16) or (c.g shl 8) or c.b

    /** 0 inside the core, rising linearly to 1 over [rampPx] outside it (distance to a rectangle). */
    private fun ramp(x: Int, y: Int, x0: Int, y0: Int, x1: Int, y1: Int, rampPx: Double): Double {
        val dx = maxOf(x0 - x, 0, x - x1).toDouble()
        val dy = maxOf(y0 - y, 0, y - y1).toDouble()
        val d = kotlin.math.hypot(dx, dy)
        return if (rampPx <= 0.0) (if (d > 0) 1.0 else 0.0) else (d / rampPx).coerceIn(0.0, 1.0)
    }

    private data class Shape(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val rampMm: Double, val lab: ColorConverter.Lab)

    /** A light garment with shapes painted over it; each shape blends from its core colour into the fabric. */
    private fun garmentImage(garment: ColorConverter.Lab, shapes: List<Shape>): IntArray {
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            var l = garment.l
            var a = garment.a
            var b = garment.b
            for (s in shapes) {
                val t = ramp(x, y, s.x0, s.y0, s.x1, s.y1, mmToPx(s.rampMm)) // 0 = shape, 1 = fabric
                if (t < 1.0) {
                    l = s.lab.l + (l - s.lab.l) * t
                    a = s.lab.a + (a - s.lab.a) * t
                    b = s.lab.b + (b - s.lab.b) * t
                }
            }
            px[y * size + x] = rgb(ColorConverter.labToRgb(l, a, b))
        }
        return px
    }

    private fun classifyAt(px: IntArray, garment: ColorConverter.Lab, vararg points: Pair<Int, Int>, pixelsPerCm: Double = pxPerCm): List<RegionAssessment> {
        val threshold = PrintJobInputs().garmentThresholdDeltaE00
        val mask = BooleanArray(px.size) { i ->
            val c = px[i]
            ColorConverter.deltaE00(ColorConverter.rgbToLab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF), garment) > threshold
        }
        val regions = ShadowInkClassifier.regions(mask, size, size)
        return points.map { (x, y) ->
            val region = regions.first { r -> r.contains(y * size + x) }
            ShadowInkClassifier.classify(px, size, size, mask, region, garment, printBox, pixelsPerCm, shadowRules)
        }
    }

    private val lightGarment = ColorConverter.Lab(93.0, 0.0, 0.0)
    private val greyInk = ColorConverter.Lab(60.0, 0.0, 0.0)

    // A soft shadow band right across the photo (4 mm ramps), above the print.
    private val shadowBand = Shape(0, 60, size - 1, 140, rampMm = 4.0, lab = ColorConverter.Lab(70.0, 0.0, 0.0))

    @Test
    fun softShadowAcrossTheBorder_isShadow_andSharpGreyShapeInThePrint_isInk() {
        val sharpGrey = Shape(220, 300, 380, 420, rampMm = 0.0, lab = greyInk)
        val px = garmentImage(lightGarment, listOf(shadowBand, sharpGrey))
        val (shadow, ink) = classifyAt(px, lightGarment, 300 to 100, 300 to 360)

        assertEquals(RegionVerdict.FOLD_SHADOW, shadow.verdict)
        assertTrue(shadow.leavesPrintBox)
        assertTrue("shadow edge ${shadow.edgeWidthMm} mm", shadow.edgeWidthMm!! >= shadowRules.shadowEdgeMinMm)
        assertNull(shadow.hueDifferenceDeg) // neutral garment: no hue test

        assertEquals(RegionVerdict.INK, ink.verdict)
        assertFalse(ink.leavesPrintBox)
        assertTrue("ink edge ${ink.edgeWidthMm} mm", ink.edgeWidthMm!! <= shadowRules.inkEdgeMaxMm)
    }

    @Test
    fun softGreyShapeInsideThePrintOnly_isUncertain() {
        val softGrey = Shape(220, 300, 380, 420, rampMm = 3.0, lab = greyInk)
        val px = garmentImage(lightGarment, listOf(softGrey))
        val (grey) = classifyAt(px, lightGarment, 300 to 360)

        assertEquals(RegionVerdict.UNCERTAIN, grey.verdict)
        assertEquals(UncertainReason.SOFT_EDGE_INSIDE_PRINT, grey.reason)
        assertFalse(grey.leavesPrintBox)
    }

    @Test
    fun belowTheMinimumResolution_everythingIsUncertain() {
        val sharpGrey = Shape(220, 300, 380, 420, rampMm = 0.0, lab = greyInk)
        val px = garmentImage(lightGarment, listOf(shadowBand, sharpGrey))
        val low = shadowRules.minPxPerCmForEdgeTest - 1.0
        classifyAt(px, lightGarment, 300 to 100, 300 to 360, pixelsPerCm = low).forEach {
            assertEquals(RegionVerdict.UNCERTAIN, it.verdict)
            assertEquals(UncertainReason.LOW_RESOLUTION, it.reason)
        }
    }

    @Test
    fun colouredGarment_shadowMustKeepTheFabricHue() {
        val pink = ColorConverter.Lab(85.0, 12.0, 4.0)
        // Same hue, less light: a = 12 and b = 4 scaled by the same factor.
        val pinkShadow = shadowBand.copy(lab = ColorConverter.Lab(65.0, 9.0, 3.0))
        val (sameHue) = classifyAt(garmentImage(pink, listOf(pinkShadow)), pink, 300 to 100)
        assertEquals(RegionVerdict.FOLD_SHADOW, sameHue.verdict)
        assertTrue(sameHue.hueDifferenceDeg!! <= shadowRules.shadowKeepsGarmentHueWithinDeg)

        // A cool grey band of the same shape does not keep the pink hue.
        val coolGrey = shadowBand.copy(lab = ColorConverter.Lab(65.0, -3.0, -8.0))
        val (otherHue) = classifyAt(garmentImage(pink, listOf(coolGrey)), pink, 300 to 100)
        assertEquals(RegionVerdict.UNCERTAIN, otherHue.verdict)
        assertEquals(UncertainReason.HUE_CHANGED, otherHue.reason)
    }

    // ---------- photo vs artwork ----------

    private val artSize = 400

    /** Flat artwork: white background, a blue square and a red disc. Returns pixels and the background mask. */
    private fun flatArtwork(): Pair<IntArray, BooleanArray> {
        val px = IntArray(artSize * artSize)
        val bg = BooleanArray(artSize * artSize)
        for (y in 0 until artSize) for (x in 0 until artSize) {
            val i = y * artSize + x
            px[i] = when {
                x in 60..180 && y in 80..200 -> 0xFF1F3FA0.toInt()
                (x - 280) * (x - 280) + (y - 260) * (y - 260) < 70 * 70 -> 0xFFD02030.toInt()
                else -> { bg[i] = true; 0xFFFFFFFF.toInt() }
            }
        }
        return px to bg
    }

    /** The same artwork as a photo: uneven light (darker to one side) and sensor noise. */
    private fun asPhoto(art: IntArray): IntArray {
        val rng = Random(7)
        return IntArray(art.size) { i ->
            val x = i % artSize
            val light = 1.0 - 0.18 * x / (artSize - 1)
            val c = art[i]
            fun ch(v: Int) = (v * light + rng.nextDouble(-1.0, 1.0) * 14.0).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
        }
    }

    @Test
    fun flatArtwork_isArtwork_andNobodyIsAsked() {
        val (px, bg) = flatArtwork()
        val a = InputKindClassifier.assess(InputKind.ARTWORK, px, artSize, artSize, bg, cameraExif = false, hasTransparency = false, rules = photoRules)
        assertEquals(InputKind.ARTWORK, a.noiseVote)
        assertEquals(InputKind.ARTWORK, a.rangeVote)
        assertEquals(InputKind.ARTWORK, a.fileInfoVote)
        assertEquals(InputKind.ARTWORK, a.signalsSuggest)
        assertEquals(0, a.signalsDisagreeing)
        assertFalse(a.askUser)
        assertTrue(a.flatNoiseLStd!! <= photoRules.artworkFlatNoiseLStdMax)
    }

    @Test
    fun noisyUnevenlyLitVersion_isPhoto_andArtworkModeAsksTheUser() {
        val (art, bg) = flatArtwork()
        val photo = asPhoto(art)
        val a = InputKindClassifier.assess(InputKind.ARTWORK, photo, artSize, artSize, bg, cameraExif = null, hasTransparency = false, rules = photoRules)
        assertTrue("noise ${a.flatNoiseLStd}", a.flatNoiseLStd!! >= photoRules.photoFlatNoiseLStdMin)
        assertTrue("range ${a.backgroundLRange}", a.backgroundLRange!! >= photoRules.photoBackgroundLRangeMin)
        assertEquals(InputKind.PHOTO, a.signalsSuggest)
        assertEquals(2, a.signalsDisagreeing)
        assertTrue(a.askUser)
        assertEquals(InputKind.ARTWORK, a.chosen) // the user's choice stays until they answer

        // Chosen as a photo, with camera information: everything agrees, no question.
        val b = InputKindClassifier.assess(InputKind.PHOTO, photo, artSize, artSize, bg, cameraExif = true, hasTransparency = false, rules = photoRules)
        assertEquals(0, b.signalsDisagreeing)
        assertFalse(b.askUser)
    }

    @Test
    fun transparentPng_votesArtwork_evenWithoutFileInformation() {
        val (px, bg) = flatArtwork()
        val a = InputKindClassifier.assess(InputKind.PHOTO, px, artSize, artSize, bg, cameraExif = null, hasTransparency = true, rules = photoRules)
        assertEquals(InputKind.ARTWORK, a.fileInfoVote)
        assertEquals(3, a.signalsDisagreeing)
        assertTrue(a.askUser)
        assertNotNull(a.signalsSuggest)
    }
}
