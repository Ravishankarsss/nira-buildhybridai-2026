package com.mos.nira

import com.mos.nira.core.image.ColourName
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain colour names for the colours NIRA users see most. */
class ColourNameTest {
    @Test
    fun commonGarmentAndInkColours() {
        assertEquals("white", ColourName.english("#FFFFFF"))
        assertEquals("black", ColourName.english("#000000"))
        assertEquals("navy", ColourName.english("#001F3F"))
        assertEquals("navy", ColourName.english("#1F2A5C"))
        assertEquals("red", ColourName.english("#C0392B"))
        assertEquals("light grey", ColourName.english("#CFCAC7"))
        assertEquals("cream", ColourName.english("#F2E8D5"))
        assertEquals("pink", ColourName.english("#D0859C"))
        assertEquals("magenta", ColourName.english("#EC008C"))
        assertEquals("yellow", ColourName.english("#FFF200"))
        assertEquals("blue", ColourName.english("#7CB2C7")) // the "Soft Sky" shade
        assertEquals("not-a-colour", ColourName.english("not-a-colour"))
    }
}
