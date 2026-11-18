package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.data.SmallColourRules
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.RegionAnswer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** print_rules.json "small_colours": a colour under the minimum coverage is flagged, never dropped silently. */
class SmallColourTest {

    private val root: JSONObject by lazy {
        val path = "src/main/assets/print_rules.json"
        JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText())
    }
    private val rules: SmallColourRules by lazy { PrintRulesRepository.parseSmallColourRules(root)!! }

    @Test
    fun rules_areReadFromTheFile() {
        val s = root.getJSONObject("small_colours")
        assertEquals(s.getDouble("min_coverage_percent"), rules.minCoveragePercent, 0.0)
        assertEquals(s.getDouble("min_region_mm2"), rules.minRegionMm2, 0.0)
        assertEquals(s.getDouble("merge_threshold_de00"), rules.mergeThresholdDeltaE00, 0.0)
        assertEquals("assumption", rules.confidence)
        assertNull(PrintRulesRepository.parseSmallColourRules(JSONObject("{}")))
    }

    // A 300 x 200 blue print on a white garment; 60 px/cm, so 4 mm2 = 144 px.
    private val size = 600
    private val pxPerCm = 60.0
    private val white = 0xFFFFFFFF.toInt()
    private val blue = 0xFF1D5CAB.toInt()
    private val magenta = 0xFFC2307A.toInt()
    private val blueShade = 0xFF1F5FAE.toInt() // under the merge threshold from the blue

    private fun print(paint: (x: Int, y: Int) -> Int?): IntArray = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (x in 150 until 450 && y in 200 until 400) paint(x, y) ?: blue else white
    }

    private fun partA(px: IntArray, answers: List<RegionAnswer> = emptyList()): PartAResult =
        PrintAreaAndPaletteEngine.computePartA(
            px, size, size,
            PrintJobInputs(inputKind = InputKind.ARTWORK, printWidthCm = 300 / pxPerCm, smallColourAnswers = answers),
            generateMaskBitmap = false,
            decisionRules = PrintRulesRepository.parseSeparationDecisionRules(root),
            maskRules = PrintRulesRepository.parseGarmentMaskRules(root),
            smallColourRules = rules
        )

    // About 1% of the print (600 px), as one compact area: an eye.
    private val eye = print { x, y -> if (x in 280 until 305 && y in 280 until 304) magenta else null }

    @Test
    fun compactOnePercentColour_isFlagged_andCountedAsPlusOne() {
        val a = partA(eye)
        val small = a.measuredPalette.filter { it.possibleSmallColour }
        assertEquals("colours ${a.measuredPalette.map { it.hex }}", 1, small.size)
        assertTrue(small.single().coveragePercent < rules.minCoveragePercent)
        assertTrue(small.single().smallColourPoint != null)
        // Headline: 1 sure colour (+1 to confirm).
        assertEquals(1, a.measuredPalette.count { !it.possibleSmallColour })
        assertEquals(0, a.speckPixelsRemoved)
    }

    @Test
    fun confirmingAddsAScreen_rejectingMergesIntoTheNearestInk() {
        val point = partA(eye).measuredPalette.single { it.possibleSmallColour }.smallColourPoint!!

        val confirmed = partA(eye, listOf(RegionAnswer(point.first, point.second, isInk = true)))
        assertEquals(2, confirmed.measuredPalette.size)
        assertTrue(confirmed.measuredPalette.none { it.possibleSmallColour })

        val rejected = partA(eye, listOf(RegionAnswer(point.first, point.second, isInk = false)))
        assertEquals(1, rejected.measuredPalette.size)
        assertEquals(60_000, rejected.measuredPalette.single().pixelCount) // the eye joined the blue
    }

    @Test
    fun scatteredSpecks_areRemoved_andTheCountIsShown() {
        // About 1% of the print as 2 x 2 specks (0.1 mm2 each), spread over the blue.
        val specks = print { x, y -> if ((x - 150) % 20 in 0..1 && (y - 200) % 20 in 0..1 && (x / 20 + y / 20) % 2 == 0) magenta else null }
        val a = partA(specks)
        assertTrue("colours ${a.measuredPalette.map { it.hex }}", a.measuredPalette.none { it.possibleSmallColour })
        assertEquals(1, a.measuredPalette.size)
        assertTrue("specks ${a.speckPixelsRemoved}", a.speckPixelsRemoved > 0)
    }

    @Test
    fun aSmallShadeOfAnInk_isNotANewColour() {
        val shade = print { x, y -> if (x in 280 until 305 && y in 280 until 304) blueShade else null }
        val a = partA(shade)
        assertEquals(1, a.measuredPalette.size)
        assertFalse(a.measuredPalette.single().possibleSmallColour)
    }
}
