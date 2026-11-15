package com.mos.nira.core.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure Kotlin ImageOps on small synthetic images with known answers.
 */
class KotlinImageOpsTest {

    private val ops: ImageOps = KotlinImageOps

    private fun block(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int): BooleanArray =
        BooleanArray(width * height) { i ->
            val x = i % width
            val y = i / width
            x in x0..x1 && y in y0..y1
        }

    @Test
    fun erode_shrinksBlockByRadiusOnEachSide() {
        val mask = block(11, 11, 3, 3, 7, 7) // 5 x 5
        val eroded = ops.erode(mask, 11, 11, 1)
        assertArrayEquals(block(11, 11, 4, 4, 6, 6), eroded) // 3 x 3
        assertEquals(9, eroded.count { it })
    }

    @Test
    fun dilate_growsBlockByRadiusOnEachSide() {
        val mask = block(11, 11, 3, 3, 7, 7)
        val dilated = ops.dilate(mask, 11, 11, 1)
        assertArrayEquals(block(11, 11, 2, 2, 8, 8), dilated) // 7 x 7
    }

    @Test
    fun radiusZero_leavesMaskUnchanged() {
        val mask = block(9, 9, 2, 2, 5, 5)
        assertArrayEquals(mask, ops.erode(mask, 9, 9, 0))
        assertArrayEquals(mask, ops.dilate(mask, 9, 9, 0))
    }

    @Test
    fun open_removesSingleSpeck_keepsBlock() {
        val mask = block(15, 15, 2, 2, 8, 8)
        mask[12 * 15 + 12] = true // one stray pixel
        val opened = ops.open(mask, 15, 15, 1)
        assertFalse(opened[12 * 15 + 12])
        assertArrayEquals(block(15, 15, 2, 2, 8, 8), opened)
    }

    @Test
    fun close_fillsSinglePixelHole() {
        val mask = block(15, 15, 2, 2, 10, 10)
        mask[6 * 15 + 6] = false // pinhole
        val closed = ops.close(mask, 15, 15, 1)
        assertTrue(closed[6 * 15 + 6])
        assertArrayEquals(block(15, 15, 2, 2, 10, 10), closed)
    }

    @Test
    fun connectedComponents_countsBlobsAndAreas() {
        val w = 12
        val h = 8
        val mask = BooleanArray(w * h)
        for (y in 1..2) for (x in 1..3) mask[y * w + x] = true   // 6 px
        for (y in 4..6) for (x in 7..10) mask[y * w + x] = true  // 12 px
        mask[3 * w + 4] = true // touches the first blob diagonally (8-connected) -> 7 px

        val result = ops.connectedComponents(mask, w, h)
        assertEquals(2, result.count)
        assertEquals(listOf(7, 12), result.areas.drop(1).sorted())
        assertEquals(w * h - 19, result.areas[0])
        assertEquals(result.labels[1 * w + 1], result.labels[3 * w + 4])
        assertEquals(0, result.labels[0])
    }

    @Test
    fun distanceTransform_givesLineWidth() {
        val w = 15
        val h = 15
        // Vertical line 5 px wide: columns 5..9
        val mask = BooleanArray(w * h) { (it % w) in 5..9 }
        val dist = ops.distanceTransform(mask, w, h)

        assertEquals(0f, dist[7 * w + 4], 0f)   // background
        assertEquals(1f, dist[7 * w + 5], 1e-4f) // edge pixel
        assertEquals(3f, dist[7 * w + 7], 1e-4f) // centre line
        // Local line width = 2 * d - 1
        assertEquals(5f, 2 * dist[7 * w + 7] - 1, 1e-4f)
    }

    @Test
    fun perspectiveWarp_fullImageCorners_returnsSameImage() {
        val w = 6
        val h = 4
        val pixels = IntArray(w * h) { 0xFF000000.toInt() or (it * 9 shl 16) or (it * 5 shl 8) or it }
        val corners = floatArrayOf(0f, 0f, (w - 1).toFloat(), 0f, (w - 1).toFloat(), (h - 1).toFloat(), 0f, (h - 1).toFloat())
        assertArrayEquals(pixels, ops.perspectiveWarp(pixels, w, h, corners, w, h))
    }

    @Test
    fun perspectiveWarp_cropsTheChosenRegion() {
        val w = 10
        val h = 10
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        // Left half red, right half blue
        val pixels = IntArray(w * h) { if (it % w < 5) red else blue }
        // Select the blue region only: x 6..9, y 2..7
        val corners = floatArrayOf(6f, 2f, 9f, 2f, 9f, 7f, 6f, 7f)
        val out = ops.perspectiveWarp(pixels, w, h, corners, 4, 6)
        assertEquals(24, out.size)
        assertTrue(out.all { it == blue })
    }

    @Test
    fun smoothEdgePreserving_flatImageUnchanged() {
        val pixels = IntArray(8 * 8) { 0xFF336699.toInt() }
        assertArrayEquals(pixels, ops.smoothEdgePreserving(pixels, 8, 8, 5, 25.0, 3.0))
    }

    @Test
    fun smoothEdgePreserving_reducesTextureButKeepsEdge() {
        val w = 16
        val h = 16
        // Left half: dark fabric with +/-6 texture. Right half: bright print.
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val v = if (x < 8) 40 + (if ((x + y) % 2 == 0) 6 else -6) else 230
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
        // sigmaColor 60: the +/-6 texture (colour distance 36) is averaged, the edge (570) is not.
        val out = ops.smoothEdgePreserving(pixels, w, h, 5, 60.0, 3.0)

        fun grey(p: Int) = p and 0xFF
        // Texture inside the fabric is flattened towards 40: neighbours started 12 apart
        // (46 and 34) and must end at most half as far apart.
        val a = grey(out[8 * w + 3])
        val b = grey(out[8 * w + 4])
        assertTrue("texture should at least halve: $a vs $b", kotlin.math.abs(a - b) <= 6)
        // The edge is kept: fabric stays dark, print stays bright.
        assertTrue(grey(out[8 * w + 7]) < 60)
        assertTrue(grey(out[8 * w + 8]) > 220)
        // Alpha is untouched.
        assertTrue(out.all { (it ushr 24) == 0xFF })
    }
}
