package com.mos.nira.core.image

import com.mos.nira.features.artwork.domain.NormalizedRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * "Save as artwork" for a garment photo: the print, cut out of the photo, redrawn in the ink colours
 * the engine measured. Every print pixel becomes its nearest measured ink (CIEDE2000); fabric and
 * everything outside the print box become transparent. No AI and no new colours: only the measured
 * inks. It is a clean starting artwork for planning, not a replacement for the buyer's original file.
 */
object CleanArtwork {

    class Result(val pixels: IntArray, val width: Int, val height: Int)

    /**
     * [pixels] and [printMask] have the same size ([width] x [height]); [inkHexes] are the measured
     * print colours; [box] is the print box (normalised). Null when there is no print or no ink.
     */
    fun make(pixels: IntArray, width: Int, height: Int, printMask: BooleanArray, inkHexes: List<String>, box: NormalizedRect): Result? {
        if (width <= 0 || height <= 0 || pixels.size != width * height || printMask.size != pixels.size || inkHexes.isEmpty()) return null
        val inks = inkHexes.map { ColorConverter.hexToRgb(it) }
        val inkLabs = inks.map { ColorConverter.rgbToLab(it.r, it.g, it.b) }
        val inkArgb = inks.map { (0xFF shl 24) or (it.r shl 16) or (it.g shl 8) or it.b }

        val x0 = floor(box.minX * width).toInt().coerceIn(0, width - 1)
        val y0 = floor(box.minY * height).toInt().coerceIn(0, height - 1)
        val x1 = ceil(box.maxX * width).toInt().coerceIn(x0 + 1, width)
        val y1 = ceil(box.maxY * height).toInt().coerceIn(y0 + 1, height)
        val w = x1 - x0
        val h = y1 - y0

        // Nearest ink once per distinct colour.
        val nearest = HashMap<Int, Int>(4096)
        var any = false
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y0 + y) * width + (x0 + x)
            if (!printMask[i]) continue // transparent (0)
            val rgb = pixels[i] and 0xFFFFFF
            val k = nearest.getOrPut(rgb) {
                val lab = ColorConverter.rgbToLab((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                var best = 0
                var bestD = Double.MAX_VALUE
                for (c in inkLabs.indices) {
                    val d = ColorConverter.deltaE00(lab, inkLabs[c])
                    if (d < bestD) { bestD = d; best = c }
                }
                best
            }
            out[y * w + x] = inkArgb[k]
            any = true
        }
        return if (any) Result(out, w, h) else null
    }
}
