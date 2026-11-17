package com.mos.nira.core.ai

import com.mos.nira.core.advice.AdviceLevel
import com.mos.nira.core.advice.AdviceReason
import com.mos.nira.core.advice.ArtworkKind
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.advice.GarmentTone
import com.mos.nira.core.advice.TechniqueAdvice
import com.mos.nira.core.image.ColorConverter
import com.mos.nira.features.artwork.domain.GarmentProfile
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartDResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import com.mos.nira.features.artwork.domain.StrategyEvaluationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeepReportPromptTest {

    private fun template(): String = listOf(
        File("src/main/assets/${DeepReportPrompt.ASSET_PATH}"),
        File("app/src/main/assets/${DeepReportPrompt.ASSET_PATH}")
    ).firstOrNull { it.exists() }?.readText() ?: throw AssertionError("prompt file not found")

    private val partA = PartAResult(
        printWidthCm = 30.0,
        printHeightCm = 20.0,
        totalBoxAreaCm2 = 600.0,
        effectivePrintAreaCm2 = 120.0,
        printPixelCount = 2000,
        totalPixelCount = 10000,
        printMaskCoveragePercent = 20.0,
        measuredPalette = listOf(
            MeasuredColor(1, "#FFFFFF", ColorConverter.Rgb(255, 255, 255), ColorConverter.Lab(100.0, 0.0, 0.0), 1200, 60.0, 12.0, 72.0),
            MeasuredColor(2, "#0055FF", ColorConverter.Rgb(0, 85, 255), ColorConverter.Lab(40.0, 30.0, -80.0), 800, 40.0, 8.0, 48.0)
        ),
        errorCurve = emptyList(),
        garmentProfile = GarmentProfile("#1A1A1A", ColorConverter.Lab(10.0, 0.0, 0.0), 2.0, 6.0),
        pixelsPerCm = 33.3
    )

    private val partB = PartBResult(
        strategies = listOf(
            StrategyEvaluationResult(
                SeparationStrategyType.BEST_N_SPOT_HALFTONE, "Best 2 Spot Inks (Halftone)", 2,
                listOf("#FFFFFF", "#0055FF"), listOf("White", "Blue"), 1.25, 3.5, 92.0,
                isRecommended = true, recommendationReason = "Fewest screens within tolerance.",
                transitionNote = "Blue as halftone over White (2 screens)"
            ),
            StrategyEvaluationResult(
                SeparationStrategyType.CMYK_PROCESS, "CMYK Process (+ Whites)", 6,
                emptyList(), emptyList(), 2.1, 5.0, 80.0
            )
        ),
        isDarkGarment = true
    )

    private val partD = PartDResult(
        channels = emptyList(),
        totalGramsPerGarment = 2.08, totalGramsPerGarmentMin = 1.68, totalGramsPerGarmentMax = 2.90,
        totalGramsPer100Pieces = 207.9, totalGramsPer100PiecesMin = 168.3, totalGramsPer100PiecesMax = 290.4,
        printBoxAreaM2 = 0.06
    )

    private val aiRead = ArtworkRead(
        ArtworkType.TONAL, listOf("mountain logo"), listOf("gradient fade"),
        listOf(PrintRisk(PrintRiskType.GRADIENT, "blue fades into white")), "Smooth fade at the top."
    )

    private val methods = MethodAdviceFacts(
        Fibre.COTTON, ArtworkKind.TONAL, GarmentTone.DARK_OR_COLOURED,
        listOf(
            TechniqueAdvice("plastisol", "Plastisol print", "screen_ink_system", "", AdviceLevel.RECOMMENDED,
                listOf(AdviceReason.FibreSuitable("cotton"), AdviceReason.ArtworkFits), emptyList(), null, false),
            TechniqueAdvice("sublimation", "Dye sublimation print", "digital", "", AdviceLevel.NOT_SUITABLE,
                listOf(AdviceReason.FibreNotSuitable("cotton"), AdviceReason.GarmentLightOnly), emptyList(), null, false)
        )
    )

    @Test
    fun facts_containTheEngineNumbers_AiRead_andAdvice() {
        val facts = DeepReportPrompt.facts(partA, partB, null, partD, PrintJobInputs(), aiRead, "ON_DEVICE", methods)
        listOf(
            "30.0 cm wide x 20.0 cm high", "#1A1A1A (L* 10.0)", "Print colours (2)", "#0055FF, 40.0%",
            "White base needed (garment rule): yes", "Best 2 Spot Inks (Halftone): 2 screens, mean dE00 1.25",
            "RECOMMENDED", "Blue as halftone over White", "2.08 g per garment (range 1.68 to 2.90 g)",
            "source ON_DEVICE", "Type: TONAL", "Risk GRADIENT: blue fades into white",
            "fabric cotton", "Plastisol print (not floor-verified): fabric suitable (cotton); suits the artwork",
            "Dye sublimation print (not floor-verified): fabric not suitable (cotton); white or light garments only"
        ).forEach { assertTrue("missing: $it\n$facts", facts.contains(it)) }
    }

    @Test
    fun facts_sayWhatIsMissing_insteadOfInventing() {
        val facts = DeepReportPrompt.facts(null, null, null, null, PrintJobInputs(), null, null, null)
        assertTrue(facts.contains("not available (report not calculated)"))
        assertTrue(facts.contains("AI read of the artwork: not done."))
        assertTrue(facts.contains("Print method advice: fabric not chosen."))
        assertFalse(Regex("\\d+\\.\\d+ g per garment").containsMatchIn(facts))
    }

    @Test
    fun prompt_isFilled_inTheChosenLanguage_withNoPlaceholdersLeft() {
        val facts = DeepReportPrompt.facts(partA, partB, null, partD, PrintJobInputs(), aiRead, "IN_CLOUD", methods)
        val english = DeepReportPrompt.build(template(), facts, ReportLanguage.ENGLISH)
        val tamil = DeepReportPrompt.build(template(), facts, ReportLanguage.TAMIL)
        assertTrue(english.contains("Write in English."))
        assertTrue(tamil.contains("Write in Tamil."))
        assertTrue(english.contains("A. Measured data") && english.contains("D. Needs physical validation"))
        assertTrue(english.contains("buyer's Pantone code"))
        assertTrue(english.contains("#0055FF"))
        assertFalse(Regex("\\{[a-z_]+\\}").containsMatchIn(english))
    }
}
