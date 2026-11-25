package com.mos.nira.core.image

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Straightening a garment photo taken at an angle: the user puts four points on the corners of the
 * garment (or the print area) and that region becomes a straight rectangle ([ImageOps.perspectiveWarp]).
 * Corners are x,y pairs in image pixels: top-left, top-right, bottom-right, bottom-left.
 * Pure geometry, no colour change, nothing AI.
 */
object PhotoStraighten {

    /** Where the four points start: a little inside the photo's edges. */
    const val START_INSET = 0.08f

    fun startCorners(width: Int, height: Int): FloatArray {
        val l = width * START_INSET; val r = width * (1 - START_INSET)
        val t = height * START_INSET; val b = height * (1 - START_INSET)
        return floatArrayOf(l, t, r, t, r, b, l, b)
    }

    /**
     * Size of the straightened picture: the average length of opposite sides, scaled down so the
     * longer side is at most [maxSide] (the photo's own longer side, so no detail is invented).
     */
    fun outputSize(c: FloatArray, maxSide: Int): Pair<Int, Int> {
        val top = hypot(c[2] - c[0], c[3] - c[1]); val bottom = hypot(c[4] - c[6], c[5] - c[7])
        val left = hypot(c[6] - c[0], c[7] - c[1]); val right = hypot(c[4] - c[2], c[5] - c[3])
        var w = (top + bottom) / 2.0; var h = (left + right) / 2.0
        val scale = if (max(w, h) > maxSide) maxSide / max(w, h) else 1.0
        w *= scale; h *= scale
        return w.roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
    }

    /** True when the four points, in order, make a box whose sides do not cross (a convex shape). */
    fun isUsable(c: FloatArray): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val ax = c[2 * i]; val ay = c[2 * i + 1]
            val bx = c[2 * ((i + 1) % 4)]; val by = c[2 * ((i + 1) % 4) + 1]
            val cx = c[2 * ((i + 2) % 4)]; val cy = c[2 * ((i + 2) % 4) + 1]
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            if (kotlin.math.abs(cross) < 1e-3f) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Index (0..3) of the corner nearest to ([x], [y]) within [maxDistance], or -1. */
    fun nearestCorner(c: FloatArray, x: Float, y: Float, maxDistance: Float): Int {
        var best = -1; var bestD = maxDistance
        for (i in 0 until 4) {
            val d = hypot(c[2 * i] - x, c[2 * i + 1] - y)
            if (d <= bestD) { best = i; bestD = d }
        }
        return best
    }
}
