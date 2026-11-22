package com.mos.nira

import com.mos.nira.core.ai.AskNira
import com.mos.nira.core.ai.NiraActions
import com.mos.nira.core.ai.NiraActions.Action
import com.mos.nira.core.ai.NiraActions.Reject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ask NIRA actions: the AI proposes from a fixed list; code checks each one before the app runs it. */
class NiraActionsTest {

    private val ctx = NiraActions.Context(
        inkHexes = listOf("#1F2A5C", "#C0392B"), // navy, red
        colours = listOf("white" to "#FFFFFF", "navy" to "#1F2A5C", "green" to "#2E7D32", "black" to "#111111"),
        fabrics = listOf("single_jersey", "pique", "interlock"),
        languages = listOf("en", "ta", "es")
    )

    private fun run(json: String, question: String) = NiraActions.validate(JSONArray(json), ctx, question)

    @Test
    fun ravisRequestNavyToGreenAndRedToNavy() {
        val r = run("""[{"type":"preview_ink","from":"navy","to":"green"},{"type":"preview_ink","from":"red","to":"navy"}]""",
            "Make the navy colour green and red to navy")
        assertTrue(r.all { it.reject == null })
        assertEquals(Action.PreviewInk("#1F2A5C", "navy", "green", "#2E7D32"), r[0].action)
        assertEquals("#C0392B", (r[1].action as Action.PreviewInk).fromHex)
    }

    @Test
    fun everyShadeOfTheColourChangesInThePreview() {
        // Two navy shades (as in the lion artwork): both must change, not only the first.
        val c = ctx.copy(inkHexes = listOf("#1F2A5C", "#1C2858", "#C0392B"))
        val r = NiraActions.validate(JSONArray("""[{"type":"preview_ink","from":"navy","to":"green"}]"""), c, "Make the navy ink green")
        val a = r[0].action as Action.PreviewInk
        assertEquals(listOf("#1F2A5C", "#1C2858"), a.allHexes)
    }

    @Test
    fun madeUpValuesAreRejected() {
        val r = run(
            """[{"type":"preview_ink","from":"yellow","to":"green"},{"type":"preview_ink","from":"navy","to":"teal"},
                {"type":"set_quantity","pieces":3000},{"type":"set_fabric","fabric":"denim"},
                {"type":"open_sheet","language":"jp"},{"type":"delete_job"}]""",
            "make it 1000 pieces"
        )
        assertEquals(
            listOf(Reject.UNKNOWN_INK, Reject.UNKNOWN_COLOUR, Reject.QUANTITY_NOT_SAID, Reject.BAD_VALUE, Reject.BAD_VALUE, Reject.UNKNOWN_TYPE),
            r.map { it.reject }
        )
        assertTrue(r.all { it.action == null })
    }

    @Test
    fun allowedChangesPass() {
        val r = run(
            """[{"type":"set_quantity","pieces":"1,000"},{"type":"set_garment","colour":"Black"},{"type":"set_fibre","fibre":"cotton"},
                {"type":"set_fabric","fabric":"single jersey"},
                {"type":"set_feel","feel":"soft"},{"type":"open_sheet","language":"es"}]""",
            "1000 pieces, black t-shirt, cotton, soft, sheet in Spanish"
        )
        assertEquals(
            listOf(Action.SetQuantity(1000), Action.SetGarment("black", "#111111"), Action.SetFibre("cotton"), Action.SetFabric("single_jersey"), Action.SetFeel("soft"), Action.OpenSheet("es")),
            r.map { it.action }
        )
    }

    @Test
    fun repliesAreReadWithOrWithoutJson() {
        val j = NiraActions.parseReply("""```json
            {"question":"make navy green","answer":"Done: the preview shows navy as green.","actions":[{"type":"preview_ink","from":"navy","to":"green"}]}
            ```""")!!
        assertEquals("make navy green", j.question)
        assertEquals(1, j.actions!!.length())
        // An on-device model may answer in plain text: used as the answer, no actions.
        val plain = NiraActions.parseReply("Discharge gives a soft feel.")!!
        assertNull(plain.actions)
        assertEquals("Discharge gives a soft feel.", plain.answer)
        assertNull(NiraActions.parseReply("""{"question":"","answer":""}"""))
    }

    @Test
    fun promptsCarryTheActionLists() {
        for (path in listOf(AskNira.ASSET_PATH, AskNira.VOICE_ASSET_PATH)) {
            val t = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).first { it.exists() }.readText()
            val p = AskNira.build(t, listOf("Method: Discharge + digital print."), emptyList(), "q", NiraActions.promptLists(ctx))
            assertTrue(p.contains("one of the job's inks: navy, red"))
            assertTrue(p.contains("white, navy, green, black"))
            assertTrue(p.contains("single jersey, pique, interlock"))
            assertFalse(p.contains("cotton_poly_blend"))
            assertTrue(p.contains("Do not state the new cost"))
            assertFalse(Regex("\\{(inks|colours|fibres|fabrics|languages|facts|conversation)\\}").containsMatchIn(p))
        }
    }
}
