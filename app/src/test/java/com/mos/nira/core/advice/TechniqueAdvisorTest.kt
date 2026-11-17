package com.mos.nira.core.advice

import com.mos.nira.features.artwork.data.SeparationDecisionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Uses the real assets/print_techniques.json, so the expectations follow the rules file. */
class TechniqueAdvisorTest {

    private val rules: List<TechniqueRule> by lazy {
        val file = listOf(
            File("src/main/assets/print_techniques.json"),
            File("app/src/main/assets/print_techniques.json")
        ).firstOrNull { it.exists() } ?: throw AssertionError("print_techniques.json not found")
        TechniqueAdvisor.parseRules(file.readText())
    }

    private fun advice(fibre: Fibre?, artwork: ArtworkKind?, garment: GarmentTone?) =
        TechniqueAdvisor.advise(rules, fibre, artwork, garment).associateBy { it.id }

    @Test
    fun rulesFile_isRead_andOnlyBaseMethodsAreOffered() {
        assertEquals(28, rules.size)
        val offered = TechniqueAdvisor.advise(rules, null, null, null)
        assertTrue(offered.isNotEmpty())
        assertTrue(offered.all { it.group in TechniqueAdvisor.METHOD_GROUPS })
        assertEquals(offered.size, offered.map { it.id }.toSet().size)
        listOf("plastisol", "water_based_pigment", "discharge", "non_pvc", "dtg", "dtf", "sublimation")
            .forEach { assertTrue("$it missing", it in offered.map { a -> a.id }) }
        assertFalse("special effects are not base methods", "puff" in offered.map { it.id })
    }

    @Test
    fun polyester_tonal_light_recommendsSublimation_andRulesOutDischarge() {
        val a = advice(Fibre.POLYESTER, ArtworkKind.TONAL, GarmentTone.LIGHT)
        assertEquals(AdviceLevel.RECOMMENDED, a.getValue("sublimation").level)
        assertEquals(AdviceLevel.NOT_SUITABLE, a.getValue("discharge").level)
        // Plastisol on polyester only with the low-cure caution from the file.
        val plastisol = a.getValue("plastisol")
        assertEquals(AdviceLevel.POSSIBLE, plastisol.level)
        assertTrue(plastisol.reasons.any { it is AdviceReason.FibreCaution && it.note.contains("low-cure") })
        // DTG lists cotton and blends, not polyester: possible, not recommended.
        assertEquals(AdviceLevel.POSSIBLE, a.getValue("dtg").level)
        assertTrue(a.getValue("dtg").reasons.contains(AdviceReason.FibreNotListed(Fibre.POLYESTER)))
    }

    @Test
    fun sublimation_isNotSuitable_onDarkGarment_orCotton() {
        val dark = advice(Fibre.POLYESTER, ArtworkKind.TONAL, GarmentTone.DARK_OR_COLOURED)
        assertEquals(AdviceLevel.NOT_SUITABLE, dark.getValue("sublimation").level)
        assertTrue(dark.getValue("sublimation").reasons.contains(AdviceReason.GarmentLightOnly))

        val cotton = advice(Fibre.COTTON, ArtworkKind.TONAL, GarmentTone.LIGHT)
        assertEquals(AdviceLevel.NOT_SUITABLE, cotton.getValue("sublimation").level)
    }

    @Test
    fun cotton_flat_dark_recommendsPlastisol_andFlagsDischargeAndPigment() {
        val a = advice(Fibre.COTTON, ArtworkKind.FLAT, GarmentTone.DARK_OR_COLOURED)
        assertEquals(AdviceLevel.RECOMMENDED, a.getValue("plastisol").level)
        // Discharge: suitable on dark cotton, but the dye must be dischargeable.
        val discharge = a.getValue("discharge")
        assertEquals(AdviceLevel.POSSIBLE, discharge.level)
        assertTrue(discharge.reasons.any { it is AdviceReason.GarmentNote && it.note.contains("dischargeable") })
        // Water-based pigment: weak on dark garments without a base (caution from the file).
        val pigment = a.getValue("water_based_pigment")
        assertEquals(AdviceLevel.POSSIBLE, pigment.level)
        assertTrue(pigment.reasons.any { it is AdviceReason.GarmentCaution })
    }

    @Test
    fun discharge_needsADarkOrMediumGarment() {
        val a = advice(Fibre.COTTON, ArtworkKind.FLAT, GarmentTone.LIGHT)
        assertEquals(AdviceLevel.NOT_SUITABLE, a.getValue("discharge").level)
        assertTrue(a.getValue("discharge").reasons.contains(AdviceReason.GarmentDarkOnly))
        // The dark-garment caution of water-based pigment does not apply on a light garment.
        assertEquals(AdviceLevel.RECOMMENDED, a.getValue("water_based_pigment").level)
    }

    @Test
    fun missingData_isNeverRecommended() {
        // Non-PVC has no fibre data in the file: possible at most.
        val a = advice(Fibre.COTTON, ArtworkKind.FLAT, GarmentTone.LIGHT)
        assertEquals(AdviceLevel.POSSIBLE, a.getValue("non_pvc").level)
        assertTrue(a.getValue("non_pvc").reasons.contains(AdviceReason.FibreNoData))
        assertEquals("article_only", a.getValue("non_pvc").confidence)

        // Nothing chosen: nothing recommended.
        val none = TechniqueAdvisor.advise(rules, null, null, null)
        assertTrue(none.none { it.level == AdviceLevel.RECOMMENDED })
    }

    @Test
    fun levels_areSorted_recommendedFirst() {
        val list = TechniqueAdvisor.advise(rules, Fibre.COTTON, ArtworkKind.FLAT, GarmentTone.DARK_OR_COLOURED)
        assertEquals(list.map { it.level }.sortedBy { it.ordinal }, list.map { it.level })
    }

    @Test
    fun floorVerification_isCarriedFromTheFile() {
        val a = advice(Fibre.COTTON, ArtworkKind.FLAT, GarmentTone.LIGHT)
        assertFalse(a.getValue("plastisol").verifiedByRavi)
    }

    @Test
    fun garmentTone_followsTheWhiteBaseRule() {
        val r = SeparationDecisionRules() // equals print_rules.json (checked in PrintRulesRepositoryTest)
        fun tone(l: Double, a: Double, b: Double) =
            TechniqueAdvisor.garmentTone(l, a, b, r.darkGarmentThresholdL, r.garmentChromaThreshold)
        assertEquals(GarmentTone.LIGHT, tone(95.0, 0.0, 0.0))        // white
        assertEquals(GarmentTone.DARK_OR_COLOURED, tone(15.0, 0.0, 0.0)) // black
        assertEquals(GarmentTone.DARK_OR_COLOURED, tone(75.0, 30.0, 10.0)) // light but strongly coloured
    }
}
