package com.mos.nira.features.artwork.domain

import kotlin.math.abs

/**
 * Moving the print box by hand: the user drags an edge or a corner of the box drawn over the mask
 * picture. Everything is in normalised image coordinates (0..1), like [NormalizedRect].
 */
object PrintBoxDrag {

    /** Which sides a drag moves. A corner moves two sides; the middle moves the whole box. */
    data class Grip(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean, val move: Boolean = false) {
        val none: Boolean get() = !left && !top && !right && !bottom && !move
    }

    val NONE = Grip(left = false, top = false, right = false, bottom = false)

    /** Smallest box side, as a share of the image (a box smaller than this is not useful). */
    const val MIN_SIDE = 0.05f

    /**
     * The sides near the touch point ([x], [y]) within [slopX] / [slopY]; inside the box and away
     * from the sides, the whole box moves. Outside the box and away from the sides: [NONE].
     */
    fun grip(box: NormalizedRect, x: Float, y: Float, slopX: Float, slopY: Float): Grip {
        val withinY = y in (box.minY - slopY)..(box.maxY + slopY)
        val withinX = x in (box.minX - slopX)..(box.maxX + slopX)
        val left = withinY && abs(x - box.minX) <= slopX
        val right = withinY && !left && abs(x - box.maxX) <= slopX
        val top = withinX && abs(y - box.minY) <= slopY
        val bottom = withinX && !top && abs(y - box.maxY) <= slopY
        val g = Grip(left, top, right, bottom)
        if (!g.none) return g
        return if (box.contains(x, y)) Grip(false, false, false, false, move = true) else NONE
    }

    /** The box after moving the gripped sides by ([dx], [dy]); kept inside the image and at least [MIN_SIDE]. */
    fun drag(box: NormalizedRect, grip: Grip, dx: Float, dy: Float): NormalizedRect {
        var l = box.minX; var t = box.minY; var r = box.maxX; var b = box.maxY
        if (grip.move) {
            val mx = dx.coerceIn(-l, 1f - r)
            val my = dy.coerceIn(-t, 1f - b)
            return NormalizedRect(l + mx, t + my, r + mx, b + my)
        }
        if (grip.left) l = (l + dx).coerceIn(0f, r - MIN_SIDE)
        if (grip.right) r = (r + dx).coerceIn(l + MIN_SIDE, 1f)
        if (grip.top) t = (t + dy).coerceIn(0f, b - MIN_SIDE)
        if (grip.bottom) b = (b + dy).coerceIn(t + MIN_SIDE, 1f)
        return NormalizedRect(l, t, r, b)
    }
}
