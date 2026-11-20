package com.mos.nira

import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.GarmentPreview
import com.mos.nira.features.artwork.data.PrintRulesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentPreviewTest {

    private val rules = PrintRulesRepository.parseGarmentPreviewRules(TestRules.root)!!

    @Test
    fun rulesLoadFromTheRealFile() {
        assertEquals(listOf("white", "navy", "black"), rules.chips.map { it.first })
        assertTrue(rules.contrastMinDeltaL > 0)
        assertEquals(listOf("white", "cream"), rules.swapColours.map { it.first })
    }

    @Test
    fun namedGarmentColoursLoadAndAreValid() {
        assertEquals(24, rules.namedColours.size)
        assertTrue(rules.namedColours.all { Regex("^#[0-9A-Fa-f]{6}$").matches(it.second) })
        assertTrue(rules.namedColours.map { it.first }.containsAll(listOf("heather_grey", "charcoal", "maroon", "bottle_green")))
    }

    @Test
    fun missingSectionGivesNull() {
        assertEquals(null, PrintRulesRepository.parseGarmentPreviewRules(org.json.JSONObject("{}")))
    }

    @Test
    fun fabricBecomesGarmentAndInkUsesTheModel() {
        val red = (0xFF shl 24) or (0xFF shl 16)
        val pixels = intArrayOf(0xFFFFFFFF.toInt(), red)
        val mask = booleanArrayOf(false, true)
        val navy = ColorConverter.hexToRgb("#001F3F")

        val without = GarmentPreview.render(pixels, mask, navy, withWhiteBase = false)
        val with = GarmentPreview.render(pixels, mask, navy, withWhiteBase = true)

        // Fabric pixel is the garment colour in both.
        assertEquals(0xFF001F3F.toInt(), without[0])
        assertEquals(0xFF001F3F.toInt(), with[0])
        // With a white base the ink shows its own colour.
        assertEquals(red, with[1])
        // Without a base, red over navy: red channel = navy's red (0), green and blue zero too.
        assertEquals(0xFF000000.toInt(), without[1])
    }

    @Test
    fun whiteGarmentLeavesInkUnchangedWithoutBase() {
        val ink = 0xFF3366CC.toInt()
        val out = GarmentPreview.render(intArrayOf(ink), booleanArrayOf(true), ColorConverter.Rgb(255, 255, 255), withWhiteBase = false)
        assertEquals(ink, out[0])
    }

    @Test
    fun contrastWarnsOnlyForInksCloseToTheGarment() {
        val warnings = GarmentPreview.contrastWarnings(listOf("#000000", "#1A1A1A", "#FFFFFF"), "#000000", rules.contrastMinDeltaL)
        val hexes = warnings.map { it.inkHex }
        assertTrue("#000000" in hexes)
        assertTrue("#1A1A1A" in hexes)
        assertTrue("#FFFFFF" !in hexes)
        assertNotNull(warnings.firstOrNull { it.inkHex == "#000000" && it.deltaL == 0 })
    }

    @Test
    fun swappingToWhiteClearsTheWarningOnBlack() {
        val white = rules.swapColours.first { it.first == "white" }.second
        assertTrue(GarmentPreview.contrastWarnings(listOf(white), "#000000", rules.contrastMinDeltaL).isEmpty())
    }
}
