package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.PhotoBackgroundRules
import com.mos.nira.features.artwork.data.PrintRulesRepository
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** print_rules.json "photo_background": a table, floor or wall at the photo edge is not print. */
class PhotoBackgroundTest {

    private val root: JSONObject by lazy {
        val path = "src/main/assets/print_rules.json"
        JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText())
    }
    private val rules: PhotoBackgroundRules by lazy { PrintRulesRepository.parsePhotoBackgroundRules(root)!! }

    @Test
    fun rules_areReadFromTheFile() {
        val b = root.getJSONObject("photo_background")
        assertEquals(b.getDouble("min_border_touch_fraction"), rules.minBorderTouchFraction, 0.0)
        assertEquals(b.getDouble("min_area_percent"), rules.minAreaPercent, 0.0)
        assertEquals(b.getDouble("centre_box_fraction"), rules.centreBoxFraction, 0.0)
        assertNull(PrintRulesRepository.parsePhotoBackgroundRules(JSONObject("{}")))
    }

    private val size = 600
    private val fabric = 0xFFE8E6E2.toInt()
    private val table = 0xFF6B4A2F.toInt() // brown wood
    private val blue = 0xFF1D5CAB.toInt()

    /** A light garment with a blue print in the middle; optionally a table strip along the left edge. */
    private fun photo(tableStripPx: Int = 0, printLeft: Int = 200): IntArray = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        when {
            x < tableStripPx -> table
            x in printLeft until 400 && y in 220 until 380 -> blue
            else -> fabric
        }
    }

    private fun partA(px: IntArray, kind: InputKind = InputKind.PHOTO, include: Boolean = false): PartAResult =
        PrintAreaAndPaletteEngine.computePartA(
            px, size, size,
            PrintJobInputs(inputKind = kind, cameraExif = kind == InputKind.PHOTO, printWidthCm = 200 / 60.0, includePhotoEdgeArea = include),
            generateMaskBitmap = false,
            decisionRules = PrintRulesRepository.parseSeparationDecisionRules(root),
            maskRules = PrintRulesRepository.parseGarmentMaskRules(root),
            backgroundRules = rules
        )

    @Test
    fun tableStripAlongOneSide_givesTheSamePrintAreaAsWithoutIt() {
        val clean = partA(photo())
        val withTable = partA(photo(tableStripPx = 90))
        assertEquals(clean.printPixelCount, withTable.printPixelCount)
        assertEquals(clean.printBox, withTable.printBox)
        assertEquals(clean.measuredPalette.map { it.hex }, withTable.measuredPalette.map { it.hex })
        assertEquals(15.0, withTable.photoEdgeExcludedPercent, 0.5) // 90 of 600 columns
        assertEquals(0.0, clean.photoEdgeExcludedPercent, 0.0)
    }

    @Test
    fun includeSwitch_putsTheEdgeAreaBack() {
        val included = partA(photo(tableStripPx = 90), include = true)
        assertEquals(0.0, included.photoEdgeExcludedPercent, 0.0)
        assertTrue(included.printPixelCount > partA(photo()).printPixelCount)
    }

    @Test
    fun aLargePrintRunningOffOneEdge_isKept_becauseItReachesTheCentre() {
        val runsOff = partA(photo(printLeft = 0))
        assertEquals(0.0, runsOff.photoEdgeExcludedPercent, 0.0)
        assertEquals(400 * 160, runsOff.printPixelCount)
    }

    @Test
    fun artworkFiles_areNotChecked() {
        val artwork = partA(photo(tableStripPx = 90), kind = InputKind.ARTWORK)
        assertEquals(0.0, artwork.photoEdgeExcludedPercent, 0.0)
    }
}
