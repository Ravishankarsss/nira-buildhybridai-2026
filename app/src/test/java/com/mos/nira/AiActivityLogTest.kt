package com.mos.nira

import com.mos.nira.core.ai.AiActivity
import com.mos.nira.core.ai.AiActivityLog
import com.mos.nira.core.ai.AiFeature
import com.mos.nira.core.ai.AiOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AI activity page: what the SDK reported, kept and counted honestly. */
class AiActivityLogTest {

    private val list = listOf(
        AiActivity(3, AiFeature.ASK_VOICE, "IN_CLOUD", 7337, image = false, voice = true, outcome = AiOutcome.PASSED),
        AiActivity(2, AiFeature.ASK, "IN_CLOUD", 3289, image = false, voice = false, outcome = AiOutcome.HELD),
        AiActivity(1, AiFeature.BRIEF_READER, "IN_CLOUD", 15183, image = true, voice = false, outcome = AiOutcome.PASSED),
        AiActivity(0, AiFeature.EXPLAIN_CARD, "ON_DEVICE", 23990, image = false, voice = false, outcome = AiOutcome.PASSED),
        AiActivity(4, AiFeature.SHEET_TRANSLATE, "ERROR", 120, image = false, voice = false, outcome = AiOutcome.ERROR)
    )

    @Test
    fun totalsCountWhatTheSdkReported() {
        val t = AiActivityLog.totals(list)
        assertEquals(1, t.onDevice)
        assertEquals(3, t.cloud)
        assertEquals(1, t.held)
        assertEquals(1, t.failed)
        assertEquals(1, t.images)
        assertEquals(1, t.voice)
    }

    @Test
    fun logSurvivesSavingAndKeepsNoText() {
        val json = AiActivityLog.encode(list)
        assertEquals(list, AiActivityLog.decode(json))
        assertTrue(AiActivityLog.decode("not json").isEmpty())
        // Only these fields are stored: no prompt, answer or artwork.
        assertTrue(Regex("\"[a-z]\":").findAll(json).map { it.value }.toSet().all { it in setOf("\"t\":", "\"f\":", "\"s\":", "\"d\":", "\"i\":", "\"v\":", "\"o\":") })
    }
}
