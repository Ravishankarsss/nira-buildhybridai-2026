package com.mos.nira.core.adjust

import com.mos.nira.core.image.KotlinImageOps
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.domain.PrintJobInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each adjustment operation on a small synthetic separation with a known pixel answer,
 * plus the preview / Accept / Cancel / Undo workflow and the "ask, do not guess" rule.
 */
class AdjustmentEngineTest {

    private val ops = KotlinImageOps
    private val black = 0xFF000000.toInt()
    private val red = 0xFFFF0000.toInt()

    private fun state(
        width: Int,
        height: Int,
        channels: List<InkChannel>,
        pixelsPerCm: Double = 10.0,
        source: IntArray = IntArray(width * height) { red },
        garmentHex: String = "#000000"
    ) = SeparationState(
        width = width,
        height = height,
        sourcePixels = source,
        printMask = BooleanArray(width * height) { i -> channels.any { it.coverage[i] > 0f } },
        channels = channels,
        garmentHex = garmentHex,
        inkModel = InkModel.OPAQUE,
        pixelsPerCm = pixelsPerCm,
        boxAreaCm2 = (width / pixelsPerCm) * (height / pixelsPerCm),
        boxPixelCount = width * height,
        garmentThresholdDeltaE00 = 5.0
    )

    private fun flat(width: Int, height: Int, value: Float) = FloatArray(width * height) { value }

    private fun block(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int, value: Float = 1f) =
        FloatArray(width * height) { i ->
            if (i % width in x0..x1 && i / width in y0..y1) value else 0f
        }

    // ---------- operations ----------

    @Test
    fun strength_scalesCoverageAndClamps() {
        val s = state(4, 4, listOf(InkChannel("Red", "#FF0000", flat(4, 4, 0.5f), 156)))

        val up = AdjustChannelStrength("Red", 20.0).apply(s, ops)
        assertTrue(up.channel("Red")!!.coverage.all { kotlin.math.abs(it - 0.6f) < 1e-6f })

        val off = AdjustChannelStrength("Red", -100.0).apply(s, ops)
        assertTrue(off.channel("Red")!!.coverage.all { it == 0f })

        val max = AdjustChannelStrength("Red", 200.0).apply(s, ops)
        assertTrue(max.channel("Red")!!.coverage.all { it == 1f })

        // The original state is untouched.
        assertTrue(s.channel("Red")!!.coverage.all { it == 0.5f })
    }

    @Test
    fun curve_changesOnlyTheChosenToneRegion() {
        val coverage = floatArrayOf(0f, 0.25f, 0.5f, 0.75f)
        val s = state(4, 1, listOf(InkChannel("Blue", "#0000FF", coverage, 200)))

        val mid = AdjustChannelCurve("Blue", ToneRegion.MIDTONES, 20.0).apply(s, ops).channel("Blue")!!.coverage
        assertEquals(0f, mid[0], 1e-6f)
        assertEquals(0.275f, mid[1], 1e-6f) // weight 0.5
        assertEquals(0.6f, mid[2], 1e-6f)   // weight 1
        assertEquals(0.825f, mid[3], 1e-6f) // weight 0.5

        val shadows = AdjustChannelCurve("Blue", ToneRegion.SHADOWS, 20.0).apply(s, ops).channel("Blue")!!.coverage
        assertEquals(0.25f, shadows[1], 1e-6f) // not a shadow: unchanged
        assertEquals(0.5f, shadows[2], 1e-6f)
        assertEquals(0.825f, shadows[3], 1e-6f)

        val highlights = AdjustChannelCurve("Blue", ToneRegion.HIGHLIGHTS, -40.0).apply(s, ops).channel("Blue")!!.coverage
        assertEquals(0.2f, highlights[1], 1e-6f) // 0.25 x (1 - 0.4 x 0.5)
        assertEquals(0.5f, highlights[2], 1e-6f)
        assertEquals(0.75f, highlights[3], 1e-6f)
    }

    @Test
    fun merge_combinesTwoScreensIntoOne() {
        val a = InkChannel("Red", "#FF0000", block(8, 4, 0, 0, 3, 3), 156)
        val b = InkChannel("Dark Red", "#CC0000", block(8, 4, 4, 0, 7, 3), 156)
        val c = InkChannel("Blue", "#0000FF", flat(8, 4, 0f), 156)
        val merged = MergeColours("Red", "Dark Red").apply(state(8, 4, listOf(a, b, c)), ops)

        assertEquals(listOf("Red", "Blue"), merged.channels.map { it.name })
        assertTrue(merged.channel("Red")!!.coverage.all { it == 1f })
    }

    @Test
    fun recolour_changesHexOnly() {
        val ch = InkChannel("Red", "#FF0000", flat(4, 4, 0.7f), 156)
        val out = RecolourInk("Red", "#00AA55").apply(state(4, 4, listOf(ch)), ops)
        assertEquals("#00AA55", out.channel("Red")!!.hex)
        assertSame(ch.coverage, out.channel("Red")!!.coverage)
    }

    @Test
    fun removeBackground_clearsInkOnGarmentColouredPixels() {
        // Left half is black garment, right half is red print, but ink was laid over everything.
        val source = IntArray(8 * 4) { if (it % 8 < 4) black else red }
        val s = state(8, 4, listOf(InkChannel("Red", "#FF0000", flat(8, 4, 1f), 156)), source = source)
        val out = RemoveBackground("#000000", "garment").apply(s, ops)

        for (i in 0 until 32) {
            val isGarment = i % 8 < 4
            assertEquals(if (isGarment) 0f else 1f, out.channel("Red")!!.coverage[i], 0f)
            assertEquals(!isGarment, out.printMask[i])
        }
    }

    @Test
    fun despeckle_removesIslandsSmallerThanTheAreaInMm2() {
        // 10 px/cm -> one pixel is 1 mm2. Block = 25 mm2, speck = 1 mm2.
        val coverage = block(20, 12, 2, 2, 6, 6)
        coverage[9 * 20 + 15] = 1f
        val s = state(20, 12, listOf(InkChannel("Red", "#FF0000", coverage, 156)), pixelsPerCm = 10.0)

        val out = Despeckle(4.0).apply(s, ops).channel("Red")!!.coverage
        assertEquals(0f, out[9 * 20 + 15], 0f)
        assertEquals(25, out.count { it > 0f })

        // A smaller limit keeps the speck.
        val kept = Despeckle(0.5).apply(s, ops).channel("Red")!!.coverage
        assertEquals(1f, kept[9 * 20 + 15], 0f)
    }

    @Test
    fun chokeMillimetres_convertCorrectlyAtTwoPrintWidths() {
        // The same 1200 px wide image printed 30 cm wide and 15 cm wide.
        assertEquals(2, AdjustmentMath.mmToPixels(0.5, 1200 / 30.0))
        assertEquals(4, AdjustmentMath.mmToPixels(0.5, 1200 / 15.0))
    }

    @Test
    fun chokeBase_shrinksOnlyTheWhiteBase_byTheConvertedPixels() {
        val w = 40
        val h = 40
        fun channels() = listOf(
            InkChannel("Base White (Underbase)", "#FFFFFF", block(w, h, 10, 10, 29, 29), 110, ChannelRole.BASE_WHITE),
            InkChannel("Red", "#FF0000", block(w, h, 10, 10, 29, 29), 156)
        )
        // 40 px across 2 cm = 20 px/cm -> 0.5 mm = 1 px. Across 1 cm = 40 px/cm -> 0.5 mm = 2 px.
        val wide = ChokeBase(0.5).apply(state(w, h, channels(), pixelsPerCm = 20.0), ops)
        val narrow = ChokeBase(0.5).apply(state(w, h, channels(), pixelsPerCm = 40.0), ops)

        assertEquals(18 * 18, wide.channels[0].coverage.count { it > 0f })
        assertEquals(16 * 16, narrow.channels[0].coverage.count { it > 0f })
        assertEquals(0f, narrow.channels[0].coverage[11 * w + 11], 0f)
        assertEquals(1f, narrow.channels[0].coverage[12 * w + 12], 0f)
        // The colour screen is not touched.
        assertEquals(20 * 20, wide.channel("Red")!!.coverage.count { it > 0f })
        assertEquals(20 * 20, narrow.channel("Red")!!.coverage.count { it > 0f })
    }

    @Test
    fun thickenLines_widensThinLineToMeshMinimum_leavesThickShapeAlone() {
        val w = 40
        val h = 24
        val coverage = block(w, h, 25, 10, 33, 18) // 9 x 9 solid shape
        for (x in 5..20) coverage[4 * w + x] = 1f  // 1 px line
        // 100 px/cm: 0.25 mm = 2.5 px -> minimum 3 px.
        val s = state(w, h, listOf(InkChannel("Black", "#231F20", coverage, 305)), pixelsPerCm = 100.0)
        val out = ThickenLines(null, mapOf(305 to 0.25)).apply(s, ops).channel("Black")!!.coverage

        for (x in 5..20) for (y in 3..5) assertEquals("x=$x y=$y", 1f, out[y * w + x], 0f)
        assertEquals(0f, out[2 * w + 10], 0f)
        assertEquals(0f, out[6 * w + 10], 0f)
        // The solid shape keeps its exact 81 pixels.
        var shape = 0
        for (y in 8 until h) for (x in 0 until w) if (out[y * w + x] > 0f) shape++
        assertEquals(81, shape)
    }

    @Test
    fun thickenLines_meshWithoutRule_changesNothingAndSaysNotAvailable() {
        val coverage = FloatArray(20 * 10)
        for (x in 2..15) coverage[4 * 20 + x] = 1f
        val s = state(20, 10, listOf(InkChannel("Black", "#231F20", coverage, 137)), pixelsPerCm = 100.0)
        val op = ThickenLines(null, mapOf(305 to 0.25))

        assertTrue(op.apply(s, ops).contentEquals(s))
        assertTrue(op.notes(s).single().contains("not available"))
    }

    @Test
    fun colourway_recoloursInksAndSavesTheName() {
        val s = state(
            4, 4,
            listOf(
                InkChannel("Red", "#FF0000", flat(4, 4, 1f), 156),
                InkChannel("Blue", "#0000FF", flat(4, 4, 0.5f), 156),
                InkChannel("White", "#FFFFFF", flat(4, 4, 0.2f), 156)
            )
        )
        val out = ApplyColourway("Autumn", mapOf("Red" to "#AA5500", "Blue" to "#334411")).apply(s, ops)

        assertEquals("#AA5500", out.channel("Red")!!.hex)
        assertEquals("#334411", out.channel("Blue")!!.hex)
        assertEquals("#FFFFFF", out.channel("White")!!.hex)
        assertEquals(mapOf("Red" to "#AA5500", "Blue" to "#334411"), out.colourways["Autumn"])
    }

    // ---------- preview / accept / cancel / undo ----------

    private fun session(initial: SeparationState) = AdjustmentSession(
        initial = initial,
        imageOps = ops,
        inputs = PrintJobInputs(printWidthCm = initial.width / initial.pixelsPerCm),
        depositRules = PrintRulesRepository.defaultDepositRules()
    )

    // 8 x 4 px at 0.5 px/cm = a 16 cm x 8 cm print, so ink quantities are realistic.
    // One pixel is 400 mm2.
    private fun threeScreenState() = state(
        8, 4,
        listOf(
            InkChannel("Red", "#FF0000", block(8, 4, 0, 0, 3, 3), 156),
            InkChannel("Dark Red", "#CC0000", block(8, 4, 4, 0, 7, 3), 156),
            InkChannel("Blue", "#0000FF", block(8, 4, 0, 0, 1, 1, 0.5f), 200)
        ),
        pixelsPerCm = 0.5
    )

    @Test
    fun preview_changesNothingUntilAccepted_andCancelDropsIt() {
        val initial = threeScreenState()
        val session = session(initial)

        val preview = session.preview(MergeColours("Red", "Dark Red"))
        assertSame(initial, session.current)
        assertEquals(2, preview.after.channels.size)
        assertEquals(8 * 4, preview.beforeProof.size)
        assertEquals(8 * 4, preview.afterProof.size)

        session.cancel()
        assertNull(session.pending)
        assertSame(initial, session.current)
        assertNull(session.accept()) // nothing to accept
        assertFalse(session.canUndo)
    }

    @Test
    fun accept_recalculatesAndReportsWhatChanged() {
        val session = session(threeScreenState())
        session.preview(MergeColours("Red", "Dark Red"))
        val summary = session.accept()!!

        assertEquals(3, summary.before.screens)
        assertEquals(2, summary.after.screens)
        assertTrue(summary.lines.contains("Screens: 3 -> 2"))
        assertTrue(summary.lines.contains("Removed screen: Dark Red"))
        assertTrue(summary.lines.any { it.startsWith("Red coverage: 50.0% -> 100.0%") })
        assertEquals(2, session.current.channels.size)

        // Ink quantity is recalculated: removing all red ink lowers the estimate.
        session.preview(AdjustChannelStrength("Red", -100.0))
        val second = session.accept()!!
        assertTrue(second.after.inkGramsPerGarment < second.before.inkGramsPerGarment)
        assertTrue(second.lines.any { it.startsWith("Ink per garment") })
        assertTrue(second.lines.any { it.startsWith("Mean colour error") })
    }

    @Test
    fun undo_restoresTheExactPreviousState_oneLevelOnly() {
        val initial = threeScreenState()
        // Independent deep copy to compare against.
        val snapshot = initial.copy(channels = initial.channels.map { it.with(coverage = it.coverage.copyOf()) })
        val session = session(initial)

        session.preview(AdjustChannelStrength("Blue", 50.0))
        session.accept()
        val afterFirst = session.current
        session.preview(Despeckle(2000.0)) // removes the 4 px (1600 mm2) blue patch
        session.accept()
        assertFalse(session.current.contentEquals(afterFirst))

        assertTrue(session.undo())
        assertSame(afterFirst, session.current)
        assertFalse("only one level of undo", session.undo())
        assertSame(afterFirst, session.current)

        // And a single change undone gives back exactly the starting state.
        val single = session(initial)
        single.preview(MergeColours("Red", "Dark Red"))
        single.accept()
        assertTrue(single.undo())
        assertSame(initial, single.current)
        assertTrue(single.current.contentEquals(snapshot))
    }

    @Test
    fun compareBeforeAfter_showsTheLastAcceptedChange() {
        val session = session(threeScreenState())
        assertNull(session.compareBeforeAfter())

        session.preview(RecolourInk("Blue", "#00FF00"))
        session.accept()
        val compare = session.compareBeforeAfter()
        assertNotNull(compare)
        assertTrue(compare!!.summary.lines.contains("Blue colour: #0000FF -> #00FF00"))
        assertFalse(compare.beforeProof.contentEquals(compare.afterProof))
    }

    // ---------- unclear requests ask a question ----------

    private fun question(request: AdjustmentRequest, state: SeparationState, context: ResolverContext = ResolverContext()): String {
        val r = AdjustmentResolver.resolve(request, state, context)
        assertTrue("expected a question for $request but got $r", r is Resolution.Question)
        return (r as Resolution.Question).text
    }

    @Test
    fun unclearRequests_produceAQuestion_notAChange() {
        val s = state(
            4, 4,
            listOf(
                InkChannel("Blue 1", "#0000FF", flat(4, 4, 1f), 137),
                InkChannel("Blue 2", "#3355FF", flat(4, 4, 1f), 137),
                InkChannel("Red", "#FF0000", flat(4, 4, 1f), 137)
            )
        )
        val snapshot = s.copy(channels = s.channels.map { it.with(coverage = it.coverage.copyOf()) })
        val session = session(s)

        // Which ink?
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.ADJUST_CHANNEL_STRENGTH, percentChange = 10.0), s).startsWith("Which ink?"))
        // "Blue" fits two inks.
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.ADJUST_CHANNEL_STRENGTH, ink = "Blue", percentChange = 10.0), s).startsWith("Which ink?"))
        // How much?
        assertEquals("By what percent?", question(AdjustmentRequest(AdjustmentIntent.ADJUST_CHANNEL_STRENGTH, ink = "Red"), s))
        assertEquals("Shadows, midtones or highlights?", question(AdjustmentRequest(AdjustmentIntent.ADJUST_CHANNEL_CURVE, ink = "Red", percentChange = 5.0), s))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.MERGE_COLOURS, ink = "Red"), s).startsWith("Which two inks"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.RECOLOUR_INK, ink = "Red"), s).startsWith("Which new colour"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.RECOLOUR_INK, ink = "Red", shadeCode = "XX-999"), s).contains("not in your shade library"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.REMOVE_BACKGROUND), s).startsWith("Which background colour"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.DESPECKLE), s).contains("mm2"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.CHOKE_BASE, millimetres = 0.5), s).contains("no white base"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.THICKEN_LINES), s, ResolverContext(minLineWidthMmByMesh = mapOf(305 to 0.25))).contains("not available"))
        assertTrue(question(AdjustmentRequest(AdjustmentIntent.APPLY_COLOURWAY, colourway = mapOf("Red" to "#112233")), s).contains("name"))

        // Nothing was previewed or changed by any of the above.
        assertNull(session.pending)
        assertSame(s, session.current)
        assertTrue(s.contentEquals(snapshot))
    }

    @Test
    fun clearRequests_resolveToTheRightOperation() {
        val s = state(
            4, 4,
            listOf(
                InkChannel("Base White (Underbase)", "#FFFFFF", flat(4, 4, 1f), 110, ChannelRole.BASE_WHITE),
                InkChannel("Royal Blue", "#0033AA", flat(4, 4, 1f), 305),
                InkChannel("Red", "#FF0000", flat(4, 4, 1f), 305)
            )
        )
        val context = ResolverContext(
            shadeHexByCode = { if (it == "NR-001") "#f0d0d8" else null },
            minLineWidthMmByMesh = mapOf(305 to 0.25)
        )
        fun ready(request: AdjustmentRequest): AdjustmentOp {
            val r = AdjustmentResolver.resolve(request, s, context)
            assertTrue("expected an operation for $request", r is Resolution.Ready)
            return (r as Resolution.Ready).op
        }

        val strength = ready(AdjustmentRequest(AdjustmentIntent.ADJUST_CHANNEL_STRENGTH, ink = "blue", percentChange = -15.0)) as AdjustChannelStrength
        assertEquals("Royal Blue", strength.ink) // partial name that fits one ink only
        assertEquals(-15.0, strength.percentChange, 0.0)

        val curve = ready(AdjustmentRequest(AdjustmentIntent.ADJUST_CHANNEL_CURVE, ink = "#FF0000", region = "Midtones", percentChange = 10.0)) as AdjustChannelCurve
        assertEquals("Red", curve.ink) // matched by HEX
        assertEquals(ToneRegion.MIDTONES, curve.region)

        assertTrue(ready(AdjustmentRequest(AdjustmentIntent.MERGE_COLOURS, ink = "Red", inkB = "Royal Blue")) is MergeColours)
        assertEquals("#F0D0D8", (ready(AdjustmentRequest(AdjustmentIntent.RECOLOUR_INK, ink = "Red", shadeCode = "NR-001")) as RecolourInk).newHex)
        assertEquals("#12AB34", (ready(AdjustmentRequest(AdjustmentIntent.RECOLOUR_INK, ink = "Red", hex = "12ab34")) as RecolourInk).newHex)
        assertEquals("#000000", (ready(AdjustmentRequest(AdjustmentIntent.REMOVE_BACKGROUND, colour = "garment")) as RemoveBackground).colourHex)
        assertEquals(2.0, (ready(AdjustmentRequest(AdjustmentIntent.DESPECKLE, minAreaMm2 = 2.0)) as Despeckle).minAreaMm2, 0.0)
        assertEquals(0.5, (ready(AdjustmentRequest(AdjustmentIntent.CHOKE_BASE, millimetres = 0.5)) as ChokeBase).millimetres, 0.0)
        assertTrue(ready(AdjustmentRequest(AdjustmentIntent.THICKEN_LINES)) is ThickenLines)
        val colourway = ready(
            AdjustmentRequest(AdjustmentIntent.APPLY_COLOURWAY, colourwayName = "Night", colourway = mapOf("red" to "#101010"))
        ) as ApplyColourway
        assertEquals(mapOf("Red" to "#101010"), colourway.inkToHex)

        assertSame(Resolution.Compare, AdjustmentResolver.resolve(AdjustmentRequest(AdjustmentIntent.COMPARE_BEFORE_AFTER), s, context))
    }
}
