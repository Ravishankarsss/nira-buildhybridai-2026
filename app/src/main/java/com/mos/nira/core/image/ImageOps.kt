package com.mos.nira.core.image

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Result of connected-component labelling.
 * labels[i] is 0 for background, or 1..count for the component the pixel belongs to.
 * areas[label] is the pixel count of that component (areas[0] is the background).
 */
class ComponentLabels(
    val labels: IntArray,
    val count: Int,
    val areas: IntArray
)

/**
 * The only geometry / morphology operations NIRA takes from an image library.
 *
 * The app uses [OpenCvImageOps]. JVM unit tests and the fallback path use [KotlinImageOps],
 * so the engine stays testable without OpenCV. No colour maths lives here: the Kotlin colour
 * engine (ColorConverter, palette, separation) is separate and unchanged.
 *
 * Pixels are ARGB ints, row by row. Masks are BooleanArray (true = print / foreground).
 * All operations are deterministic. Nothing here is AI.
 */
interface ImageOps {

    /** Short name shown in diagnostics ("OpenCV" or "Kotlin"). */
    val name: String

    /**
     * Straightens a four-corner region into an outWidth x outHeight image.
     * corners = x,y pairs in source pixels: top-left, top-right, bottom-right, bottom-left.
     */
    fun perspectiveWarp(
        pixels: IntArray,
        width: Int,
        height: Int,
        corners: FloatArray,
        outWidth: Int,
        outHeight: Int
    ): IntArray

    /** Bilateral smoothing: removes fabric texture, keeps print edges. Alpha is kept as is. */
    fun smoothEdgePreserving(
        pixels: IntArray,
        width: Int,
        height: Int,
        diameterPx: Int,
        sigmaColor: Double,
        sigmaSpace: Double
    ): IntArray

    /** Shrinks the mask by radiusPx (square element of side 2 * radiusPx + 1). */
    fun erode(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray

    /** Grows the mask by radiusPx (square element of side 2 * radiusPx + 1). */
    fun dilate(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray

    /** Erode then dilate: removes specks smaller than the element. */
    fun open(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        dilate(erode(mask, width, height, radiusPx), width, height, radiusPx)

    /** Dilate then erode: fills holes smaller than the element. */
    fun close(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        erode(dilate(mask, width, height, radiusPx), width, height, radiusPx)

    /** Labels 8-connected groups of true pixels. */
    fun connectedComponents(mask: BooleanArray, width: Int, height: Int): ComponentLabels

    /**
     * For every true pixel: distance in pixels to the nearest false pixel. False pixels are 0.
     * A line of width w pixels has a largest value of about (w + 1) / 2 on its centre line,
     * so local line width is about 2 * d - 1.
     */
    fun distanceTransform(mask: BooleanArray, width: Int, height: Int): FloatArray
}

/**
 * Pure Kotlin implementation of [ImageOps]. Used in JVM unit tests and as a fallback when the
 * OpenCV native library cannot be loaded.
 */
object KotlinImageOps : ImageOps {

    override val name: String = "Kotlin"

    override fun perspectiveWarp(
        pixels: IntArray,
        width: Int,
        height: Int,
        corners: FloatArray,
        outWidth: Int,
        outHeight: Int
    ): IntArray {
        require(corners.size == 8) { "corners must hold 4 x,y pairs" }
        require(pixels.size == width * height) { "pixel buffer does not match width x height" }
        if (outWidth <= 0 || outHeight <= 0) return IntArray(0)

        // Homography that maps output pixels to source pixels.
        val dst = doubleArrayOf(
            0.0, 0.0,
            (outWidth - 1).toDouble(), 0.0,
            (outWidth - 1).toDouble(), (outHeight - 1).toDouble(),
            0.0, (outHeight - 1).toDouble()
        )
        val h = solveHomography(dst, DoubleArray(8) { corners[it].toDouble() })
        val out = IntArray(outWidth * outHeight)

        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                val w = h[6] * x + h[7] * y + 1.0
                val sx = (h[0] * x + h[1] * y + h[2]) / w
                val sy = (h[3] * x + h[4] * y + h[5]) / w
                out[y * outWidth + x] = sampleBilinear(pixels, width, height, sx, sy)
            }
        }
        return out
    }

    /** Solves the 8 unknowns of a homography from 4 point pairs (from -> to). */
    private fun solveHomography(from: DoubleArray, to: DoubleArray): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[2 * i]
            val y = from[2 * i + 1]
            val u = to[2 * i]
            val v = to[2 * i + 1]
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gaussian elimination with partial pivoting.
        for (col in 0 until 8) {
            var pivot = col
            for (row in col + 1 until 8) {
                if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val p = a[col][col]
            require(abs(p) > 1e-12) { "corners do not form a usable quadrilateral" }
            for (k in col until 9) a[col][k] /= p
            for (row in 0 until 8) {
                if (row != col) {
                    val f = a[row][col]
                    if (f != 0.0) for (k in col until 9) a[row][k] -= f * a[col][k]
                }
            }
        }
        return DoubleArray(8) { a[it][8] }
    }

    private fun sampleBilinear(pixels: IntArray, width: Int, height: Int, sx: Double, sy: Double): Int {
        val cx = sx.coerceIn(0.0, (width - 1).toDouble())
        val cy = sy.coerceIn(0.0, (height - 1).toDouble())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val fx = cx - x0
        val fy = cy - y0

        val p00 = pixels[y0 * width + x0]
        val p10 = pixels[y0 * width + x1]
        val p01 = pixels[y1 * width + x0]
        val p11 = pixels[y1 * width + x1]

        var result = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val top = ((p00 ushr shift) and 0xFF) * (1 - fx) + ((p10 ushr shift) and 0xFF) * fx
            val bottom = ((p01 ushr shift) and 0xFF) * (1 - fx) + ((p11 ushr shift) and 0xFF) * fx
            val v = Math.round(top * (1 - fy) + bottom * fy).toInt().coerceIn(0, 255)
            result = result or (v shl shift)
        }
        return result
    }

    override fun smoothEdgePreserving(
        pixels: IntArray,
        width: Int,
        height: Int,
        diameterPx: Int,
        sigmaColor: Double,
        sigmaSpace: Double
    ): IntArray {
        require(pixels.size == width * height) { "pixel buffer does not match width x height" }
        val radius = diameterPx / 2
        if (radius <= 0 || sigmaColor <= 0.0 || sigmaSpace <= 0.0) return pixels.copyOf()

        val spaceCoeff = -0.5 / (sigmaSpace * sigmaSpace)
        val colorCoeff = -0.5 / (sigmaColor * sigmaColor)
        // Colour distance is |dR| + |dG| + |dB|, range 0..765.
        val colorWeight = DoubleArray(766) { exp(it.toDouble() * it * colorCoeff) }
        val out = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0
                var sumW = 0.0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        if (dx * dx + dy * dy > radius * radius) continue
                        val p = pixels[ny * width + nx]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        val w = exp((dx * dx + dy * dy) * spaceCoeff) *
                            colorWeight[abs(r - cr) + abs(g - cg) + abs(b - cb)]
                        sumR += r * w
                        sumG += g * w
                        sumB += b * w
                        sumW += w
                    }
                }
                val r = Math.round(sumR / sumW).toInt().coerceIn(0, 255)
                val g = Math.round(sumG / sumW).toInt().coerceIn(0, 255)
                val b = Math.round(sumB / sumW).toInt().coerceIn(0, 255)
                out[y * width + x] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    override fun erode(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        squareFilter(mask, width, height, radiusPx, keepIfAll = true)

    override fun dilate(mask: BooleanArray, width: Int, height: Int, radiusPx: Int): BooleanArray =
        squareFilter(mask, width, height, radiusPx, keepIfAll = false)

    /**
     * Square min (erode) or max (dilate) filter, done as a row pass then a column pass.
     * Pixels outside the image are ignored, the same as OpenCV's default border.
     */
    private fun squareFilter(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radiusPx: Int,
        keepIfAll: Boolean
    ): BooleanArray {
        require(mask.size == width * height) { "mask does not match width x height" }
        if (radiusPx <= 0) return mask.copyOf()

        val rowPass = BooleanArray(mask.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var value = keepIfAll
                val from = maxOf(0, x - radiusPx)
                val to = minOf(width - 1, x + radiusPx)
                for (nx in from..to) {
                    if (mask[row + nx] != keepIfAll) {
                        value = !keepIfAll
                        break
                    }
                }
                rowPass[row + x] = value
            }
        }

        val out = BooleanArray(mask.size)
        for (x in 0 until width) {
            for (y in 0 until height) {
                var value = keepIfAll
                val from = maxOf(0, y - radiusPx)
                val to = minOf(height - 1, y + radiusPx)
                for (ny in from..to) {
                    if (rowPass[ny * width + x] != keepIfAll) {
                        value = !keepIfAll
                        break
                    }
                }
                out[y * width + x] = value
            }
        }
        return out
    }

    override fun connectedComponents(mask: BooleanArray, width: Int, height: Int): ComponentLabels {
        require(mask.size == width * height) { "mask does not match width x height" }
        val labels = IntArray(mask.size)
        val areas = ArrayList<Int>()
        areas.add(0) // background, filled in at the end
        val stack = IntArray(mask.size)
        var count = 0

        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue
            count++
            var area = 0
            var top = 0
            stack[top++] = start
            labels[start] = count
            while (top > 0) {
                val idx = stack[--top]
                area++
                val x = idx % width
                val y = idx / width
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val n = ny * width + nx
                        if (mask[n] && labels[n] == 0) {
                            labels[n] = count
                            stack[top++] = n
                        }
                    }
                }
            }
            areas.add(area)
        }

        val areaArray = areas.toIntArray()
        areaArray[0] = mask.size - areaArray.drop(1).sum()
        return ComponentLabels(labels, count, areaArray)
    }

    override fun distanceTransform(mask: BooleanArray, width: Int, height: Int): FloatArray {
        require(mask.size == width * height) { "mask does not match width x height" }
        // Exact Euclidean distance transform (Felzenszwalb and Huttenlocher), columns then rows.
        val inf = 1e20
        val grid = DoubleArray(mask.size) { if (mask[it]) inf else 0.0 }
        val maxDim = maxOf(width, height)
        val f = DoubleArray(maxDim)
        val d = DoubleArray(maxDim)
        val v = IntArray(maxDim)
        val z = DoubleArray(maxDim + 1)

        for (x in 0 until width) {
            for (y in 0 until height) f[y] = grid[y * width + x]
            transform1d(f, d, v, z, height)
            for (y in 0 until height) grid[y * width + x] = d[y]
        }
        for (y in 0 until height) {
            for (x in 0 until width) f[x] = grid[y * width + x]
            transform1d(f, d, v, z, width)
            for (x in 0 until width) grid[y * width + x] = d[x]
        }
        return FloatArray(mask.size) { sqrt(grid[it]).toFloat() }
    }

    private fun transform1d(f: DoubleArray, d: DoubleArray, v: IntArray, z: DoubleArray, n: Int) {
        var k = 0
        v[0] = 0
        z[0] = Double.NEGATIVE_INFINITY
        z[1] = Double.POSITIVE_INFINITY
        for (q in 1 until n) {
            var s: Double
            while (true) {
                val p = v[k]
                s = ((f[q] + q.toDouble() * q) - (f[p] + p.toDouble() * p)) / (2.0 * q - 2.0 * p)
                if (s <= z[k] && k > 0) k-- else break
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Double.POSITIVE_INFINITY
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val p = v[k]
            d[q] = (q - p).toDouble() * (q - p) + f[p]
        }
    }
}
