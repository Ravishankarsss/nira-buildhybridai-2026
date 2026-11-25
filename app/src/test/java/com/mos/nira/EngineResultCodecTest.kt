package com.mos.nira

import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.InputKind
import com.mos.nira.core.image.InputKindAssessment
import com.mos.nira.features.artwork.data.EngineResultCodec
import com.mos.nira.features.artwork.data.ProcessInksConfig
import com.mos.nira.features.artwork.domain.ArtworkBackground
import com.mos.nira.features.artwork.domain.ErrorCurvePoint
import com.mos.nira.features.artwork.domain.GarmentProfile
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.StrategyEvaluationResult
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineResultCodecTest {

    private val mask = BooleanArray(37) { it % 3 == 0 }

    private val partA = PartAResult(
        printWidthCm = 30.0, printHeightCm = 24.5, totalBoxAreaCm2 = 735.0, effectivePrintAreaCm2 = 210.25,
        printPixelCount = 12345, totalPixelCount = 40000, printMaskCoveragePercent = 28.6,
        measuredPalette = listOf(
            MeasuredColor(1, "#1F2A5C", ColorConverter.Rgb(31, 42, 92), ColorConverter.Lab(18.2, 9.1, -33.4), 5000, 40.5, 12.1, 85.2,
                ShadeEntry("NIRA_DEMO", "N-01", "Navy", 18.0, 9.0, -33.0), 0.8, "good",
                possibleFoldShadow = true, uncertainRegionPoints = listOf(0.25f to 0.5f), possibleSmallColour = true, smallColourPoint = 0.1f to 0.9f),
            MeasuredColor(2, "#C0392B", ColorConverter.Rgb(192, 57, 43), ColorConverter.Lab(45.0, 58.0, 41.0), 2000, 16.2, 5.0, 34.0)
        ),
        errorCurve = listOf(ErrorCurvePoint(4, 6.5), ErrorCurvePoint(8, 3.1)),
        garmentProfile = GarmentProfile("#001F3F", ColorConverter.Lab(12.0, 1.0, -20.0), 1.4, 5.2, isUserOverridden = true),
        printBox = NormalizedRect(0.1f, 0.15f, 0.9f, 0.85f),
        pixelsPerCm = 36.2, lowResolutionWarning = "low", minPxPerCm = 59.0,
        inputKindAssessment = InputKindAssessment(InputKind.ARTWORK, null, 0.78, InputKind.PHOTO, null, null, 1, false, null),
        foldShadowsRemoved = 3, shadowTestLowResolution = true, edgeBlendPixelsMerged = 120, edgeBlendBandMaxPx = 2.5,
        speckPixelsRemoved = 9, photoEdgeExcludedPercent = 1.5,
        artworkBackground = ArtworkBackground("#FFFFFF", 90.0, 41.0, printed = false),
        printMask = mask, maskWidth = 37, maskHeight = 1
    )

    private val partB = PartBResult(
        strategies = listOf(
            StrategyEvaluationResult(SeparationStrategyType.FLAT_SPOT_SOLID, "Flat spot", 2, listOf("#1F2A5C", "#C0392B"), listOf("Navy", "Red"),
                1.2, 3.4, 97.5, darkGarmentWarning = "dark", isDarkGarment = true, isRecommended = true, recommendationReason = "fewest screens",
                transitionNote = null, notes = "n"),
            StrategyEvaluationResult(SeparationStrategyType.MIXED_CMYK_SPOT, "Mixed", 6, listOf("#00AEEF"), listOf("Cyan"),
                2.0, 5.0, 90.0, spotHexes = listOf("#C0392B"), blackFromKHexes = listOf("#111111"), solidCoreDeltaE00 = 4.0)
        ),
        selectedStrategyType = SeparationStrategyType.FLAT_SPOT_SOLID,
        processInks = ProcessInksConfig(),
        isDarkGarment = true, executionTimeMs = 11444, opaqueInkModel = true
    )

    @Test
    fun partAComesBackTheSame() {
        val text = EngineResultCodec.encodePartA(partA).toString()
        val back = EngineResultCodec.decodePartA(JSONObject(text), mask, null)
        assertEquals(partA.copy(printMask = null), back.copy(printMask = null))
        assertArrayEquals(mask, back.printMask)
    }

    @Test
    fun partBComesBackTheSame() {
        val back = EngineResultCodec.decodePartB(JSONObject(EngineResultCodec.encodePartB(partB).toString()))
        assertEquals(partB, back)
    }

    @Test
    fun printMaskPacksIntoBits() {
        val big = BooleanArray(1001) { (it * 7) % 5 == 0 }
        val bytes = EngineResultCodec.packMask(big)
        assertEquals(126, bytes.size)
        assertArrayEquals(big, EngineResultCodec.unpackMask(bytes, big.size))
    }
}
