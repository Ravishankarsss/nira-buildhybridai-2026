package com.mos.nira

import com.mos.nira.core.ai.MeasuredFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasuredFactsTest {

    private val colours = listOf(
        MeasuredFacts.Colour("orange", 12.34, false),
        MeasuredFacts.Colour("navy", 40.0, false),
        MeasuredFacts.Colour("cream", 0.4, true)
    )

    @Test
    fun countsColoursAndSaysWhatIsNotDecided() {
        val f = MeasuredFacts.facts(isPhoto = false, colours = colours)
        assertEquals("Input: an artwork file.", f.first())
        assertTrue(f.any { it.startsWith("Print colours measured by NIRA's engine (no AI): 2, plus 1") })
        // Largest first, numbers as measured.
        val firstColour = f.indexOfFirst { it.startsWith("Colour ") }
        assertEquals("Colour navy: 40.0% of the print.", f[firstColour])
        assertTrue(f.contains("Colour orange: 12.3% of the print."))
        assertTrue(f.contains("Colour cream: 0.4% of the print (needs the user's check)."))
        assertTrue(f.last().startsWith("Not decided yet: the print method, screens"))
    }

    @Test
    fun backgroundAnswerAndResolution() {
        val f = MeasuredFacts.facts(
            isPhoto = false, colours = colours, backgroundName = "white",
            backgroundCoveragePercent = 50.0, backgroundPrinted = false, lowResolution = true
        )
        assertTrue(f.contains("Plain background white covers 50.0% of the image: the user said it is not ink; it is left out."))
        assertTrue(f.any { it.contains("below the rules file's limit") })
        val unanswered = MeasuredFacts.facts(false, colours, "white", 50.0, null)
        assertTrue(unanswered.any { it.endsWith("the user has not said yet whether it is ink.") })
    }
}
