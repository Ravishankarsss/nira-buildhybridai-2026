package com.mos.nira.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArtworkReadPromptTest {

    private fun template(): String {
        val file = listOf(
            File("src/main/assets/${ArtworkReadPrompt.ASSET_PATH}"),
            File("app/src/main/assets/${ArtworkReadPrompt.ASSET_PATH}")
        ).firstOrNull { it.exists() } ?: throw AssertionError("prompt file not found")
        return file.readText()
    }

    @Test
    fun prompt_isFilledWithEngineFacts_andHasNoPlaceholdersLeft() {
        val prompt = ArtworkReadPrompt.build(template(), listOf("#FFFFFF", "#0055FF"), "#1A1A1A")
        assertTrue(prompt.contains("print colours: 2"))
        assertTrue(prompt.contains("#FFFFFF, #0055FF"))
        assertTrue(prompt.contains("garment colour: #1A1A1A"))
        assertFalse("placeholder left in: $prompt", Regex("\\{[a-z_]+\\}").containsMatchIn(prompt))
        // Short enough for the on-device model (about 4,000 input tokens on Android).
        assertTrue(prompt.length < 2000)
    }

    @Test
    fun prompt_withoutMeasurements_saysSo() {
        val prompt = ArtworkReadPrompt.build(template(), emptyList(), null)
        assertTrue(prompt.contains("print colours: 0"))
        assertTrue(prompt.contains("colours: none measured"))
        assertTrue(prompt.contains("garment colour: not measured"))
    }

    @Test
    fun parse_readsAWellFormedAnswer() {
        val answer = """
            {"artwork_type":"TONAL","motifs":["mountain logo","script text"],
             "effects":["gradient fade"],
             "risks":[{"type":"GRADIENT","note":"blue fades into white"},{"type":"SMALL_TEXT","note":"tagline under 5 mm"}],
             "evidence":"The blue part fades smoothly."}
        """.trimIndent()
        val read = ArtworkReadPrompt.parse(answer)!!
        assertEquals(ArtworkType.TONAL, read.artworkType)
        assertEquals(listOf("mountain logo", "script text"), read.motifs)
        assertEquals(listOf("gradient fade"), read.effects)
        assertEquals(PrintRisk(PrintRiskType.GRADIENT, "blue fades into white"), read.risks[0])
        assertEquals(PrintRiskType.SMALL_TEXT, read.risks[1].type)
        assertEquals("The blue part fades smoothly.", read.evidence)
    }

    @Test
    fun parse_toleratesCodeFencesAndExtraText() {
        val answer = "Here is the result:\n```json\n{\"artwork_type\":\"flat\",\"motifs\":[\"star\"],\"risks\":[]}\n```"
        val read = ArtworkReadPrompt.parse(answer)!!
        assertEquals(ArtworkType.FLAT, read.artworkType)
        assertEquals(listOf("star"), read.motifs)
        assertTrue(read.risks.isEmpty())
        assertEquals("", read.evidence)
    }

    @Test
    fun parse_unknownValuesAreNotInvented() {
        val answer = """{"artwork_type":"MIXED","motifs":["a","b","c","d","e"],
            "risks":[{"type":"BLEED","note":"ink may spread"},{"type":"???"}]}"""
        val read = ArtworkReadPrompt.parse(answer)!!
        assertEquals(ArtworkType.UNKNOWN, read.artworkType)
        assertEquals(4, read.motifs.size) // capped
        assertEquals(listOf(PrintRisk(PrintRiskType.OTHER, "ink may spread")), read.risks)
    }

    @Test
    fun parse_returnsNull_whenThereIsNoUsableJson() {
        assertNull(ArtworkReadPrompt.parse(null))
        assertNull(ArtworkReadPrompt.parse(""))
        assertNull(ArtworkReadPrompt.parse("The artwork looks flat."))
        assertNull(ArtworkReadPrompt.parse("{not json}"))
        assertNull(ArtworkReadPrompt.parse("{\"colour\":\"blue\"}"))
    }
}
