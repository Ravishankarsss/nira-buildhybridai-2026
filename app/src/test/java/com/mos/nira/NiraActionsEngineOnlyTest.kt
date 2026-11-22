package com.mos.nira

import com.mos.nira.core.ai.NiraActions
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ask NIRA's actions change only answers (garment, fibre, fabric, quantity, feel), a preview colour,
 * or what is open. None of them can carry an engine value: screens, mesh, ink grams, cost or price
 * always come from the engine after the change, never from the AI.
 */
class NiraActionsEngineOnlyTest {

    private val engineWords = listOf("screen", "mesh", "gram", "ink_g", "cost", "price", "rate", "deltae", "coverage")

    @Test
    fun noActionCarriesAnEngineValue() {
        // Every action type is declared inside NiraActions.Action (plain Java reflection).
        val actions = NiraActions.Action::class.java.declaredClasses.filter { NiraActions.Action::class.java.isAssignableFrom(it) }
        assertTrue("expected the action list", actions.size >= 10)
        actions.forEach { k ->
            k.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { f ->
                val name = f.name.lowercase()
                assertTrue("${k.simpleName}.${f.name} looks like an engine value", engineWords.none { name.contains(it) })
            }
        }
    }

    @Test
    fun previewInkOnlyNamesColours() {
        val fields = NiraActions.Action.PreviewInk::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertTrue(fields.containsAll(listOf("fromHex", "toHex")))
        assertTrue(fields.all { it.endsWith("Hex") || it.endsWith("Hexes") || it.endsWith("Name") || it.endsWith("Key") })
    }
}
