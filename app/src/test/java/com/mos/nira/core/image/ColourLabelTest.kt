package com.mos.nira.core.image

import com.mos.nira.features.artwork.domain.ShadeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ColourLabelTest {

    private fun shade(name: String, hex: String): ShadeEntry {
        val rgb = ColorConverter.hexToRgb(hex)
        val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
        return ShadeEntry(type = "NIRA_DEMO", code = "T-$name", name = name, labL = lab.l, labA = lab.a, labB = lab.b)
    }

    private val library = listOf(
        shade("Dark Grey", "#666666"),
        shade("Off White", "#F2F0EB"),
        shade("Ink Blue", "#0E1D48")
    )

    @Test
    fun garmentColour_getsTheNearestShadeName_thenTheHex() {
        assertEquals("Dark Grey (#6A6969)", ColourLabel.withNearestShadeName("#6A6969", library))
        assertEquals("Off White (#CECAC7)", ColourLabel.withNearestShadeName("#CECAC7", library))
        assertEquals("Ink Blue (#0F1E49)", ColourLabel.withNearestShadeName("#0F1E49", library))
    }

    @Test
    fun noLibraryOrBadHex_showsTheHexOnly() {
        assertEquals("#6A6969", ColourLabel.withNearestShadeName("#6A6969", emptyList()))
        assertEquals("not-a-colour", ColourLabel.withNearestShadeName("not-a-colour", library))
    }
}
