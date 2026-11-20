package com.mos.nira

import com.mos.nira.core.advice.TechniqueLibrary
import com.mos.nira.core.ai.AiActivityLog
import com.mos.nira.core.ai.AskNira
import com.mos.nira.core.ai.HomeFacts
import com.mos.nira.core.ai.NiraActions
import com.mos.nira.core.ai.NiraActions.Action
import com.mos.nira.core.ai.NiraActions.Reject
import com.mos.nira.features.advice.JobSummary
import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ask NIRA on Home: facts from the saved jobs and rules file; only Home actions, checked. */
class HomeAskTest {

    private val navy = ArtworkAnalysisRecord("r1", "logo_navy.png", "content://1", null, 10, 10, 1, 2_000, emptyList())
    private val photo = ArtworkAnalysisRecord("r2", "garment_1795155333119.jpg", "content://2", null, 10, 10, 1, 3_000, emptyList(), inputKind = "PHOTO")
    private val summary = JobSummary("Discharge + digital print", 1, 50.4, "INR", true, 0, true, true)
    private val lib = TechniqueLibrary.parse(
        listOf(File("src/main/assets/print_techniques.json"), File("app/src/main/assets/print_techniques.json")).first { it.exists() }.readText()
    )

    @Test
    fun factsCarryJobsTechniquesAndActivity() {
        val facts = HomeFacts.build(listOf(navy, photo), mapOf("r1" to summary), lib, AiActivityLog.Totals(1, 3, 1, 0, 1, 1), true, true, "INR")
        assertTrue(facts.any { it.startsWith("Saved job \"logo_navy\"") && it.contains("method Discharge + digital print") && it.contains("₹50.40 per piece") })
        assertTrue(facts.any { it.contains("method not worked out yet") })
        assertTrue(facts.count { it.startsWith("Technique ") } == lib.entries.size)
        assertTrue(facts.any { it.startsWith("AI on this phone so far: 1 answers on the phone, 3 in the cloud") })
        assertTrue(facts.none { Regex("#[0-9A-Fa-f]{6}").containsMatchIn(it) })
        // The newest job comes first.
        assertEquals("r2", HomeFacts.jobs(listOf(navy, photo)).first().first)
    }

    @Test
    fun homeActionsAreChecked() {
        val ctx = NiraActions.Context(emptyList(), emptyList(), emptyList(), emptyList(), jobs = HomeFacts.jobs(listOf(navy, photo)))
        val r = NiraActions.validate(
            JSONArray(
                """[{"type":"open_job","job":"logo_navy"},{"type":"open_job","job":"someone else's job"},
                    {"type":"open_library","search":"nylon"},{"type":"new_job","from":"photo"},{"type":"new_job","from":"email"},
                    {"type":"open_ai_activity"},{"type":"set_quantity","pieces":5}]"""
            ),
            ctx, "open logo navy"
        )
        assertEquals(Action.OpenJob("r1", "logo_navy"), r[0].action)
        assertEquals(Reject.BAD_VALUE, r[1].reject)
        assertEquals(Action.OpenLibrary("nylon"), r[2].action)
        assertEquals(Action.NewJob(photo = true), r[3].action)
        assertEquals(Reject.BAD_VALUE, r[4].reject)
        assertEquals(Action.OpenAiActivity, r[5].action)
        // A job action with a number the user never said is refused here too.
        assertEquals(Reject.QUANTITY_NOT_SAID, r[6].reject)
    }

    @Test
    fun homePromptsKeepTheGuardrails() {
        for (path in listOf(HomeFacts.TEXT_PROMPT, HomeFacts.VOICE_PROMPT)) {
            val t = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).first { it.exists() }.readText()
            val ctx = NiraActions.Context(emptyList(), emptyList(), emptyList(), emptyList(), jobs = listOf("r1" to "logo_navy"))
            val p = AskNira.build(t, listOf("Saved jobs on this phone: 1."), emptyList(), "q", NiraActions.promptLists(ctx))
            assertTrue(p.contains("Do not choose a print method for a new garment yourself"))
            assertTrue(p.contains("exactly as written: logo_navy"))
            assertTrue(p.contains("Do not calculate new numbers"))
            assertFalse(Regex("\\{(jobs|facts|conversation)\\}").containsMatchIn(p))
        }
    }
}
