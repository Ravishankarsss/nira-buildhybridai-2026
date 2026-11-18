package com.mos.nira.features.artwork.data

import org.junit.Assert.assertNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loads the real app/src/main/assets/print_rules.json and checks that PrintRulesRepository
 * reads the version 2 key names. Expected values are read from the file itself, so this test
 * holds no engineering numbers of its own.
 */
class PrintRulesRepositoryTest {

    private fun loadRules(): JSONObject {
        val file = listOf(
            File("src/main/assets/print_rules.json"),
            File("app/src/main/assets/print_rules.json")
        ).firstOrNull { it.exists() }
            ?: throw AssertionError("print_rules.json not found from ${File(".").absolutePath}")
        return JSONObject(file.readText())
    }

    @Test
    fun realJsonParserIsUsed_notAndroidStub() {
        // Guards against isReturnDefaultValues: a stubbed JSONObject would return null here.
        assertEquals("v", JSONObject("{\"k\":\"v\"}").optString("k"))
        assertTrue(loadRules().has("rules_version"))
    }

    @Test
    fun processInks_readFromV2File() {
        val root = loadRules()
        val expected = root.getJSONObject("process_inks")
        val inks = PrintRulesRepository.parseProcessInks(root)

        assertEquals(expected.getString("cyan"), inks.cyanHex)
        assertEquals(expected.getString("magenta"), inks.magentaHex)
        assertEquals(expected.getString("yellow"), inks.yellowHex)
        assertEquals(expected.getString("black"), inks.blackHex)
        assertEquals(expected.getString("confidence"), inks.confidence)
        assertEquals(expected.getString("note"), inks.note)
    }

    @Test
    fun separationDecisionRules_readFromV2Sections() {
        val root = loadRules()
        val rules = PrintRulesRepository.parseSeparationDecisionRules(root)

        assertEquals(
            root.getJSONObject("white_base").getDouble("garment_L_threshold"),
            rules.darkGarmentThresholdL, 0.0
        )
        assertEquals(
            root.getJSONObject("recommendation").getDouble("quality_tolerance_mean_de00"),
            rules.qualityToleranceDeltaE00, 0.0
        )
        assertEquals(
            root.getJSONObject("resolution").getDouble("px_per_cm_min_for_fine_detail"),
            rules.minResolutionPxPerCm, 0.0
        )
        assertEquals(
            root.getJSONObject("underbase_rules").getDouble("choke_mm_default"),
            rules.chokeMmDefault, 0.0
        )
        assertEquals(root.getJSONObject("white_base").getString("confidence"), rules.confidence)
        assertEquals(
            root.getJSONObject("white_base").getDouble("garment_chroma_threshold"),
            rules.garmentChromaThreshold, 0.0
        )
        assertEquals(
            root.getJSONObject("recommendation").getDouble("quality_tolerance_p95_de00"),
            rules.qualityToleranceP95DeltaE00, 0.0
        )
        assertEquals(
            root.getJSONObject("recommendation").getInt("cmyk_min_hue_families"),
            rules.cmykMinHueFamilies
        )
    }

    @Test
    fun garmentMaskRules_readFromV2File() {
        val root = loadRules()
        val g = root.getJSONObject("garment_mask")
        val rules = PrintRulesRepository.parseGarmentMaskRules(root)

        assertEquals(g.getDouble("border_fraction"), rules.borderFraction, 0.0)
        assertEquals(g.getDouble("spread_multiplier"), rules.spreadMultiplier, 0.0)
        assertEquals(g.getDouble("min_threshold_de00"), rules.minThresholdDeltaE00, 0.0)
        assertEquals(g.getDouble("speck_min_area_fraction"), rules.speckMinAreaFraction, 0.0)
        assertEquals(g.getString("confidence"), rules.confidence)
        // Fallback defaults must not drift from the file.
        assertEquals(rules, GarmentMaskRules())
    }

    @Test
    fun kotlinFallbackDefaults_matchTheRulesFile() {
        // The defaults are used only if the file cannot be read. They must not drift from it.
        val root = loadRules()
        assertEquals(PrintRulesRepository.parseSeparationDecisionRules(root), SeparationDecisionRules())
        assertEquals(
            PrintRulesRepository.parseProcessInks(root).copy(note = ""),
            ProcessInksConfig().copy(note = "")
        )
        // No hidden table in the code: without the file there are no ink deposits at all.
        assertTrue(PrintRulesRepository.parseMeshDepositRules(JSONObject("{}")).isEmpty())
    }

    @Test
    fun meshDepositRules_entryWithAMissingValue_isLeftOut_notFilledIn() {
        val json = JSONObject("""{"ink_consumption":{"plastisol_deposit_g_m2_by_mesh":[
            {"tpi":110,"deposit_g_m2_min":38,"deposit_g_m2_max":63,"deposit_g_m2_default":45},
            {"tpi":160,"deposit_g_m2_min":30}]}}""")
        val map = PrintRulesRepository.parseMeshDepositRules(json)
        assertEquals(setOf(110), map.keys)
    }

    @Test
    fun screenMeshRules_comeFromTheFile() {
        val root = loadRules()
        val rules = PrintRulesRepository.parseScreenMeshRules(root)!!
        val mapping = root.getJSONObject("screen_type_mesh_rule")
        val list = root.getJSONArray("mesh_rules")
        val byId = (0 until list.length()).map { list.getJSONObject(it) }.associateBy { it.getString("id") }
        assertEquals(ScreenType.values().size, rules.byScreenType.size)
        for ((type, rule) in rules.byScreenType) {
            val entry = byId.getValue(mapping.getString(type.key))
            assertEquals(entry.getInt("tpi_min"), rule.tpiMin)
            assertEquals(entry.getInt("tpi_max"), rule.tpiMax)
        }
        assertEquals("assumption", rules.mappingConfidence)
        assertNull(PrintRulesRepository.parseScreenMeshRules(JSONObject("{}")))
    }

    @Test
    fun meshDepositRules_readEveryMeshInTheFile() {
        val root = loadRules()
        val arr = root.getJSONObject("ink_consumption").getJSONArray("plastisol_deposit_g_m2_by_mesh")
        val deposits = PrintRulesRepository.parseMeshDepositRules(root)

        assertEquals(arr.length(), deposits.size)
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val rule = deposits[item.getInt("tpi")] ?: throw AssertionError("Missing mesh ${item.getInt("tpi")}")
            assertEquals(item.getDouble("deposit_g_m2_default"), rule.depositGPerM2Default, 0.0)
            assertEquals(item.getDouble("deposit_g_m2_min"), rule.depositGPerM2Min, 0.0)
            assertEquals(item.getDouble("deposit_g_m2_max"), rule.depositGPerM2Max, 0.0)
            assertEquals(item.getString("confidence"), rule.confidence)
        }
    }

    @Test
    fun minLineWidthByMesh_readFromDetailRules() {
        val root = loadRules()
        val arr = root.getJSONObject("detail_rules").getJSONArray("min_line_width_mm_by_mesh")
        val widths = PrintRulesRepository.parseMinLineWidthMmByMesh(root)

        assertEquals(arr.length(), widths.size)
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            assertEquals(item.getDouble("min_line_mm"), widths[item.getInt("tpi")]!!, 0.0)
        }
        assertTrue(PrintRulesRepository.parseMinLineWidthMmByMesh(JSONObject("{}")).isEmpty())
    }

    @Test
    fun missingSections_fallBackWithoutCrashing() {
        val empty = JSONObject("{}")
        assertEquals(SeparationDecisionRules(), PrintRulesRepository.parseSeparationDecisionRules(empty))
        assertEquals(ProcessInksConfig(), PrintRulesRepository.parseProcessInks(empty))
        assertTrue(PrintRulesRepository.parseMeshDepositRules(empty).isEmpty())
    }
}
