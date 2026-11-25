package com.mos.nira

import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PrintBoxDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintBoxDragTest {
    private val box = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
    private val e = 1e-5f

    @Test
    fun touchesNearASideOrCornerGripThatSide() {
        val left = PrintBoxDrag.grip(box, 0.21f, 0.5f, 0.03f, 0.03f)
        assertTrue(left.left && !left.right && !left.top && !left.bottom)
        val corner = PrintBoxDrag.grip(box, 0.79f, 0.81f, 0.03f, 0.03f)
        assertTrue(corner.right && corner.bottom && !corner.left && !corner.top)
        assertTrue(PrintBoxDrag.grip(box, 0.5f, 0.5f, 0.03f, 0.03f).move)
        assertTrue(PrintBoxDrag.grip(box, 0.05f, 0.05f, 0.03f, 0.03f).none)
    }

    @Test
    fun draggingASideMovesOnlyThatSideAndStaysInsideTheImage() {
        val g = PrintBoxDrag.grip(box, 0.2f, 0.5f, 0.03f, 0.03f)
        val moved = PrintBoxDrag.drag(box, g, -0.5f, 0.3f)
        assertEquals(0f, moved.minX, e)
        assertEquals(0.2f, moved.minY, e)
        assertEquals(0.8f, moved.maxX, e)
        assertEquals(0.8f, moved.maxY, e)
    }

    @Test
    fun theBoxNeverCollapsesAndMovingKeepsItsSize() {
        val right = PrintBoxDrag.grip(box, 0.8f, 0.5f, 0.03f, 0.03f)
        val small = PrintBoxDrag.drag(box, right, -0.9f, 0f)
        assertEquals(0.2f + PrintBoxDrag.MIN_SIDE, small.maxX, e)
        val moved = PrintBoxDrag.drag(box, PrintBoxDrag.grip(box, 0.5f, 0.5f, 0.03f, 0.03f), 0.5f, -0.5f)
        assertEquals(0.4f, moved.minX, e); assertEquals(1f, moved.maxX, e)
        assertEquals(0f, moved.minY, e); assertEquals(0.6f, moved.maxY, e)
    }
}
