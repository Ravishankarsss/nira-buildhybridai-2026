package com.mos.nira

import com.mos.nira.core.advice.TechniqueLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Technique Library: the real techniques file, shown as written and searchable. */
class TechniqueLibraryTest {

    private val json = listOf(File("src/main/assets/print_techniques.json"), File("app/src/main/assets/print_techniques.json"))
        .first { it.exists() }.readText()
    private val lib = TechniqueLibrary.parse(json)

    @Test
    fun everyTechniqueInTheFileIsInTheLibrary() {
        val count = org.json.JSONObject(json).getJSONArray("techniques").length()
        assertEquals(count, lib.entries.size)
        assertTrue(lib.entries.all { it.rule.name.isNotBlank() && it.rule.group.isNotBlank() })
        assertTrue("screen_ink_system" in lib.groups && "digital" in lib.groups)
        assertTrue(lib.confidenceKey.containsKey("sourced_multi"))
        assertTrue(lib.artworkFitKey.containsKey("TONAL_OK"))
    }

    @Test
    fun parametersAreShownAsTheFileGivesThem() {
        val plastisol = lib.entries.first { it.rule.id == "plastisol" }
        val cure = plastisol.params.first { it.key == "cure_temp_c" }
        assertEquals("160", cure.value)
        assertEquals("sourced_multi", cure.confidence)
        assertEquals("110–160", plastisol.params.first { it.key == "mesh_tpi" }.value)
        val dd = lib.entries.first { it.rule.id == "discharge_digital" }
        assertTrue(dd.rule.verifiedByRavi)
        assertEquals(3, dd.rule.processSteps.size)
        assertTrue(dd.processSource!!.startsWith("Ravi"))
    }

    @Test
    fun searchAndGroupFilter() {
        val discharge = TechniqueLibrary.filter(lib, "discharge", null).map { it.rule.id }
        assertTrue("discharge_digital" in discharge)
        assertTrue(TechniqueLibrary.filter(lib, "", "digital").all { it.rule.group == "digital" })
        assertEquals(lib.entries.size, TechniqueLibrary.filter(lib, "  ", null).size)
        assertTrue(TechniqueLibrary.filter(lib, "zzzz-not-a-word", null).isEmpty())
    }
}
