package com.mos.nira

import com.mos.nira.core.image.KotlinImageOps
import com.mos.nira.core.image.PhotoStraighten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStraightenTest {

    @Test
    fun outputSizeIsTheAverageOfOppositeSidesAndNeverBiggerThanThePhoto() {
        // A slanted 400 x 300 region (top side 400, bottom 360, left and right 300).
        val c = floatArrayOf(0f, 0f, 400f, 0f, 380f, 300f, 20f, 300f)
        assertEquals(380 to 301, PhotoStraighten.outputSize(c, 1000))
        val (w, h) = PhotoStraighten.outputSize(c, 190)
        assertEquals(190, w)
        assertTrue(h <= 190)
    }

    @Test
    fun crossedOrFlatCornersAreRefused() {
        assertTrue(PhotoStraighten.isUsable(PhotoStraighten.startCorners(100, 80)))
        // Top-right and bottom-right swapped: the sides cross.
        assertFalse(PhotoStraighten.isUsable(floatArrayOf(0f, 0f, 100f, 80f, 100f, 0f, 0f, 80f)))
        // Three points on one line.
        assertFalse(PhotoStraighten.isUsable(floatArrayOf(0f, 0f, 50f, 0f, 100f, 0f, 0f, 80f)))
    }

    @Test
    fun nearestCornerIsFoundOnlyWhenCloseEnough() {
        val c = PhotoStraighten.startCorners(100, 100)
        assertEquals(2, PhotoStraighten.nearestCorner(c, 90f, 91f, 10f))
        assertEquals(-1, PhotoStraighten.nearestCorner(c, 50f, 50f, 10f))
    }

    @Test
    fun aSlantedRectangleBecomesStraight() {
        // A red rectangle drawn as a slanted shape on white comes out as a full red picture.
        val w = 60; val h = 50
        val red = 0xFFFF0000.toInt(); val white = 0xFFFFFFFF.toInt()
        val corners = floatArrayOf(10f, 5f, 50f, 10f, 48f, 45f, 8f, 40f)
        val px = IntArray(w * h) { i ->
            val x = (i % w).toFloat(); val y = (i / w).toFloat()
            if (inside(corners, x, y)) red else white
        }
        val ow = 30; val oh = 25
        val out = KotlinImageOps.perspectiveWarp(px, w, h, corners, ow, oh)
        // Inside the result (the outer pixel ring blends with the white edge): all red.
        val inner = (1 until oh - 1).flatMap { y -> (1 until ow - 1).map { x -> out[y * ow + x] } }
        val redShare = inner.count { ((it shr 16) and 0xFF) > 200 && ((it shr 8) and 0xFF) < 60 } / inner.size.toDouble()
        assertTrue("red share $redShare", redShare > 0.98)
        // The white outside the slanted shape is gone.
        assertTrue(out.none { it == white })
    }

    private fun inside(c: FloatArray, x: Float, y: Float): Boolean {
        for (i in 0 until 4) {
            val ax = c[2 * i]; val ay = c[2 * i + 1]; val bx = c[2 * ((i + 1) % 4)]; val by = c[2 * ((i + 1) % 4) + 1]
            if ((bx - ax) * (y - ay) - (by - ay) * (x - ax) < -1f) return false
        }
        return true
    }
}
