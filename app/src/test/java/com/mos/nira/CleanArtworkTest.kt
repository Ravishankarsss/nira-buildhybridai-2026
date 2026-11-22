package com.mos.nira

import com.mos.nira.core.image.CleanArtwork
import com.mos.nira.features.artwork.domain.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanArtworkTest {

    private fun argb(hex: String) = (0xFF shl 24) or hex.removePrefix("#").toInt(16)

    @Test
    fun printPixelsTakeTheNearestMeasuredInkAndFabricIsTransparent() {
        // 4 x 2 photo: grey fabric, a slightly noisy red and a slightly noisy navy.
        val w = 4; val h = 2
        val px = intArrayOf(
            argb("#808080"), argb("#C2392C"), argb("#C0382A"), argb("#808080"),
            argb("#808080"), argb("#1F2B5D"), argb("#202A5C"), argb("#808080")
        )
        val mask = booleanArrayOf(false, true, true, false, false, true, true, false)
        val r = CleanArtwork.make(px, w, h, mask, listOf("#C0392B", "#1F2A5C"), NormalizedRect(0f, 0f, 1f, 1f))!!
        assertEquals(4, r.width)
        assertEquals(0, r.pixels[0]) // fabric: transparent
        assertEquals(argb("#C0392B"), r.pixels[1])
        assertEquals(argb("#C0392B"), r.pixels[2])
        assertEquals(argb("#1F2A5C"), r.pixels[5])
        assertEquals(argb("#1F2A5C"), r.pixels[6])
    }

    @Test
    fun cropsToThePrintBox() {
        val w = 4; val h = 2
        val px = IntArray(w * h) { argb("#C0392B") }
        val mask = BooleanArray(w * h) { true }
        val r = CleanArtwork.make(px, w, h, mask, listOf("#C0392B"), NormalizedRect(0.25f, 0f, 0.75f, 1f))!!
        assertEquals(2, r.width)
        assertEquals(2, r.height)
    }

    @Test
    fun noPrintOrNoInkGivesNull() {
        val px = IntArray(4) { argb("#808080") }
        assertNull(CleanArtwork.make(px, 2, 2, BooleanArray(4), listOf("#C0392B"), NormalizedRect(0f, 0f, 1f, 1f)))
        assertNull(CleanArtwork.make(px, 2, 2, BooleanArray(4) { true }, emptyList(), NormalizedRect(0f, 0f, 1f, 1f)))
    }
}
