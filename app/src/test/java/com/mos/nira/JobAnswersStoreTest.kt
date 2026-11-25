package com.mos.nira

import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.features.artwork.data.JobAnswers
import com.mos.nira.features.artwork.data.JobAnswersStore
import com.mos.nira.features.artwork.domain.RegionAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobAnswersStoreTest {

    @Test
    fun allAnswersComeBackUnchanged() {
        val a = JobAnswers(
            backgroundPrinted = false,
            garmentHex = "#001F3F",
            fibre = Fibre.values().first(),
            fabric = "single_jersey",
            quantity = 500,
            feel = Feel.SOFT,
            inkSwaps = mapOf("#0A1A40" to "#FFFFFF"),
            regionAnswers = listOf(RegionAnswer(0.25f, 0.5f, true)),
            smallColourAnswers = listOf(RegionAnswer(0.1f, 0.9f, false)),
            edgeBlendAnswer = true,
            allUncertainAreFoldShadows = true,
            keepEdgeColours = true,
            includePhotoEdgeArea = true,
            userGarmentHex = "#111111",
            useWhiteBaseOverride = false,
            printBox = com.mos.nira.features.artwork.domain.NormalizedRect(0.1f, 0.2f, 0.9f, 0.75f),
            erasedRectangles = listOf(com.mos.nira.features.artwork.domain.NormalizedRect(0.05f, 0.05f, 0.2f, 0.15f)),
            printWidthCm = 28.0,
            meshTpi = 230,
            strokes = 2,
            wastagePercent = 7.5,
            whitePrintActsAsBase = false
        )
        assertEquals(a, JobAnswersStore.decode(JobAnswersStore.encode(a)))
    }

    @Test
    fun reportSettingsNotChangedStayEmpty() {
        val back = JobAnswersStore.decode(JobAnswersStore.encode(JobAnswers(quantity = 300)))!!
        assertNull(back.printBox)
        assertTrue(back.erasedRectangles.isEmpty())
        assertNull(back.printWidthCm)
        assertNull(back.meshTpi)
        assertNull(back.whitePrintActsAsBase)
    }

    @Test
    fun unansweredStaysUnanswered() {
        val back = JobAnswersStore.decode(JobAnswersStore.encode(JobAnswers(quantity = 1000)))!!
        assertEquals(1000, back.quantity)
        assertNull(back.backgroundPrinted)
        assertNull(back.garmentHex)
        assertNull(back.fibre)
        assertNull(back.feel)
        assertTrue(JobAnswers().isEmpty)
    }

    @Test
    fun brokenTextGivesNull() {
        assertNull(JobAnswersStore.decode("not json"))
    }
}
