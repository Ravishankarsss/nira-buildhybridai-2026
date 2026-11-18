package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.ShadowVsInkRules
import com.mos.nira.features.artwork.domain.NormalizedRect
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot

/** What a non-fabric region on a light garment is (print_rules.json "shadow_vs_ink"). */
enum class RegionVerdict { INK, FOLD_SHADOW, UNCERTAIN }

/** Why a region is uncertain, so the screen can explain it. */
enum class UncertainReason {
    /** Below the minimum pixels per cm: the edge cannot be measured; suggest a retake. */
    LOW_RESOLUTION,
    /** No edge could be measured (for example the region only touches the image border). */
    NO_EDGE_MEASURED,
    /** Edge width between the ink maximum and the shadow minimum. */
    EDGE_BETWEEN,
    /** Soft edge, but the region stays inside the print. */
    SOFT_EDGE_INSIDE_PRINT,
    /** Sharp edge, but the region reaches outside the print box. */
    SHARP_EDGE_OUTSIDE_PRINT,
    /** Coloured garment: soft and outside the print, but it does not keep the fabric's hue. */
    HUE_CHANGED
}

data class RegionAssessment(
    val verdict: RegionVerdict,
    /** Median 10%-90% edge width in mm at print scale; null if not measured. */
    val edgeWidthMm: Double?,
    val leavesPrintBox: Boolean,
    /** Hue difference to the garment in degrees; null when the hue test does not apply. */
    val hueDifferenceDeg: Double?,
    val reason: UncertainReason?
)

/**
 * Tells a fold shadow from a grey ink on a light garment, using edge sharpness (ink edges come
 * from a stencil; shadows fade over millimetres), location (a shadow runs into the plain fabric
 * outside the print) and, on a coloured garment only, hue (a shadow keeps the fabric's hue).
 * Every limit comes from print_rules.json. Anything that is neither clearly ink nor clearly a
 * shadow is UNCERTAIN, never dropped: missing a real ink screen under-quotes the job.
 */
object ShadowInkClassifier {

    /**
     * How far the edge profile is followed on each side of the region boundary, as a multiple of
     * the shadow minimum edge width, so a whole shadow edge fits inside the profile.
     * A measuring window, not a print rule.
     */
    private const val WALK_PER_SIDE_IN_SHADOW_EDGES = 2.0

    /** Boundary points measured per region (evenly spaced), to keep the cost bounded. */
    private const val MAX_EDGE_SAMPLES = 512

    /** Lightness steps smaller than this (L* units) are not an edge and are skipped. */
    private const val MIN_EDGE_STEP_L = 1.0

    /** Connected regions of a mask, and for every pixel the index of its region (-1 = not in the mask). */
    class LabelledRegions(val labels: IntArray, val regions: List<IntArray>)

    /** Labels the connected regions (4-neighbour) of [mask] once, for classifying many regions. */
    fun labelRegions(mask: BooleanArray, width: Int, height: Int): LabelledRegions {
        val regions = regions(mask, width, height)
        val labels = IntArray(mask.size) { -1 }
        regions.forEachIndexed { id, r -> for (p in r) labels[p] = id }
        return LabelledRegions(labels, regions)
    }

    /** True when the region touches the edge of the photo. */
    fun touchesImageEdge(region: IntArray, width: Int, height: Int): Boolean = region.any { p ->
        val x = p % width
        val y = p / width
        x == 0 || y == 0 || x == width - 1 || y == height - 1
    }

    /** Connected regions (4-neighbour) of the true pixels of [mask]; each is a list of pixel indices. */
    fun regions(mask: BooleanArray, width: Int, height: Int): List<IntArray> {
        val seen = BooleanArray(mask.size)
        val result = ArrayList<IntArray>()
        val stack = IntArray(mask.size)
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            val members = ArrayList<Int>()
            var top = 0
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val p = stack[--top]
                members.add(p)
                val x = p % width
                val y = p / width
                if (x > 0) push(p - 1, mask, seen, stack, top).also { top = it }
                if (x < width - 1) push(p + 1, mask, seen, stack, top).also { top = it }
                if (y > 0) push(p - width, mask, seen, stack, top).also { top = it }
                if (y < height - 1) push(p + width, mask, seen, stack, top).also { top = it }
            }
            result.add(members.toIntArray())
        }
        return result
    }

    private fun push(q: Int, mask: BooleanArray, seen: BooleanArray, stack: IntArray, top: Int): Int {
        if (!mask[q] || seen[q]) return top
        seen[q] = true
        stack[top] = q
        return top + 1
    }

    /**
     * Classifies one [region] (pixel indices) of [printMask] (true = not fabric).
     * [pixelsPerCm] is at print scale; [printBox] is the print graphic in normalised coordinates.
     */
    fun classify(
        pixels: IntArray,
        width: Int,
        height: Int,
        printMask: BooleanArray,
        region: IntArray,
        garmentLab: ColorConverter.Lab,
        printBox: NormalizedRect,
        pixelsPerCm: Double,
        rules: ShadowVsInkRules
    ): RegionAssessment {
        val labels = IntArray(width * height) { -1 }
        for (p in region) labels[p] = 0
        return classify(pixels, width, height, printMask, labels, 0, region, garmentLab, printBox, pixelsPerCm, rules, HashMap())
    }

    /**
     * Same as above for region [regionId] of [labels] (from [labelRegions]); [labCache] may be
     * shared between calls on the same pixels.
     */
    fun classify(
        pixels: IntArray,
        width: Int,
        height: Int,
        printMask: BooleanArray,
        labels: IntArray,
        regionId: Int,
        region: IntArray,
        garmentLab: ColorConverter.Lab,
        printBox: NormalizedRect,
        pixelsPerCm: Double,
        rules: ShadowVsInkRules,
        labCache: HashMap<Int, ColorConverter.Lab>
    ): RegionAssessment {
        val inRegion = RegionTest(labels, regionId)

        // Location: does the region leave the print box or reach the edge of the photo?
        var leavesPrintBox = false
        for (p in region) {
            val x = p % width
            val y = p / width
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1 ||
                !printBox.contains((x + 0.5f) / width, (y + 0.5f) / height)
            ) {
                leavesPrintBox = true
                break
            }
        }

        // Hue: only on a garment with enough colour.
        fun labOf(p: Int): ColorConverter.Lab {
            val c = pixels[p] and 0xFFFFFF
            return labCache.getOrPut(c) { ColorConverter.rgbToLab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) }
        }
        val garmentChroma = hypot(garmentLab.a, garmentLab.b)
        var sumA = 0.0
        var sumB = 0.0
        for (p in region) {
            val lab = labOf(p)
            sumA += lab.a
            sumB += lab.b
        }
        val regionChroma = hypot(sumA / region.size, sumB / region.size)
        val neutralLimit = rules.hueTestOnlyIfGarmentChromaAtLeast
        val hueDifference = if (garmentChroma >= neutralLimit) {
            hueDifferenceDeg(Math.toDegrees(atan2(garmentLab.b, garmentLab.a)), Math.toDegrees(atan2(sumB, sumA)))
        } else null

        // A shadow is the same fabric with less light. On a neutral garment (chroma below the
        // hue-test limit) a shadow is therefore neutral too, so a clearly coloured region is ink
        // whatever its edges look like (for example a light blue print on a white T-shirt).
        if (garmentChroma < neutralLimit && regionChroma >= neutralLimit) {
            return RegionAssessment(RegionVerdict.INK, null, leavesPrintBox, null, null)
        }

        if (pixelsPerCm < rules.minPxPerCmForEdgeTest) {
            return RegionAssessment(RegionVerdict.UNCERTAIN, null, leavesPrintBox, hueDifference, UncertainReason.LOW_RESOLUTION)
        }

        val edgeMm = medianEdgeWidthPx(width, height, printMask, inRegion, region, ::labOf, pixelsPerCm, rules)
            ?.let { it / (pixelsPerCm / 10.0) }
            ?: return RegionAssessment(RegionVerdict.UNCERTAIN, null, leavesPrintBox, hueDifference, UncertainReason.NO_EDGE_MEASURED)

        val locationSaysShadow = !rules.shadowIfTouchesPlainFabricOrLeavesPrintBox || leavesPrintBox
        val hueSaysShadow = hueDifference == null || hueDifference <= rules.shadowKeepsGarmentHueWithinDeg

        return when {
            edgeMm <= rules.inkEdgeMaxMm && !leavesPrintBox ->
                RegionAssessment(RegionVerdict.INK, edgeMm, leavesPrintBox, hueDifference, null)
            edgeMm >= rules.shadowEdgeMinMm && locationSaysShadow && hueSaysShadow ->
                RegionAssessment(RegionVerdict.FOLD_SHADOW, edgeMm, leavesPrintBox, hueDifference, null)
            else -> {
                val reason = when {
                    edgeMm > rules.inkEdgeMaxMm && edgeMm < rules.shadowEdgeMinMm -> UncertainReason.EDGE_BETWEEN
                    edgeMm <= rules.inkEdgeMaxMm -> UncertainReason.SHARP_EDGE_OUTSIDE_PRINT
                    !locationSaysShadow -> UncertainReason.SOFT_EDGE_INSIDE_PRINT
                    else -> UncertainReason.HUE_CHANGED
                }
                RegionAssessment(RegionVerdict.UNCERTAIN, edgeMm, leavesPrintBox, hueDifference, reason)
            }
        }
    }

    /** Smallest angle between two hues, 0..180 degrees. */
    private fun hueDifferenceDeg(h1: Double, h2: Double): Double {
        val d = abs(h1 - h2) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * Median 10%-90% lightness edge width in pixels, measured across the region boundary.
     * At each boundary point the profile runs from the fabric outside (stopping at the photo
     * edge or at another print region) to the region inside (stopping where the region ends).
     */
    private fun medianEdgeWidthPx(
        width: Int,
        height: Int,
        printMask: BooleanArray,
        inRegion: RegionTest,
        region: IntArray,
        labOf: (Int) -> ColorConverter.Lab,
        pixelsPerCm: Double,
        rules: ShadowVsInkRules
    ): Double? {
        val span = ceil(WALK_PER_SIDE_IN_SHADOW_EDGES * rules.shadowEdgeMinMm * pixelsPerCm / 10.0).toInt().coerceAtLeast(2)

        // Boundary points: region pixels with an in-image 4-neighbour outside the region.
        val boundary = ArrayList<Int>()
        for (p in region) {
            if (outwardStep(p, width, height, inRegion) != 0) boundary.add(p)
        }
        if (boundary.isEmpty()) return null
        val stride = maxOf(1, boundary.size / MAX_EDGE_SAMPLES)

        val widths = ArrayList<Double>()
        var i = 0
        while (i < boundary.size) {
            val b = boundary[i]
            i += stride
            val step = outwardStep(b, width, height, inRegion)
            val horizontal = step == 1 || step == -1

            val outside = ArrayList<Double>()
            var q = b
            for (k in 1..span) {
                q = next(q, step, width, height, horizontal) ?: break
                if (printMask[q] && !inRegion[q]) break // another print region
                outside.add(labOf(q).l)
            }
            val inside = ArrayList<Double>()
            q = b
            for (k in 0..span) {
                if (k > 0) q = next(q, -step, width, height, horizontal) ?: break
                if (!inRegion[q]) break
                inside.add(labOf(q).l)
            }
            if (outside.size < 2 || inside.isEmpty()) continue

            // Profile from far outside to far inside, smoothed over 3 pixels against camera noise.
            val raw = outside.asReversed() + inside
            val profile = raw.indices.map { j ->
                val lo = maxOf(0, j - 1)
                val hi = minOf(raw.size - 1, j + 1)
                (lo..hi).sumOf { raw[it] } / (hi - lo + 1)
            }
            // Fabric level: mean of the outer half of the outside samples.
            val outerHalf = maxOf(2, outside.size / 2)
            val lOut = profile.subList(0, outerHalf).average()
            val lIn = profile.subList(outside.size, profile.size).maxByOrNull { abs(it - lOut) }!!
            val delta = lIn - lOut
            if (abs(delta) < MIN_EDGE_STEP_L) continue
            var i10 = -1
            var i90 = -1
            for (j in profile.indices) {
                val f = abs(profile[j] - lOut) / abs(delta)
                if (i10 < 0 && f >= 0.1) i10 = j
                if (f >= 0.9) {
                    i90 = j
                    break
                }
            }
            if (i10 >= 0 && i90 >= i10) widths.add((i90 - i10).toDouble())
        }
        if (widths.isEmpty()) return null
        widths.sort()
        return widths[widths.size / 2]
    }

    /** +1/-1 (x) or +width/-width (y) towards an in-image neighbour outside the region; 0 if none. */
    private fun outwardStep(p: Int, width: Int, height: Int, inRegion: RegionTest): Int {
        val x = p % width
        val y = p / width
        if (x > 0 && !inRegion[p - 1]) return -1
        if (x < width - 1 && !inRegion[p + 1]) return 1
        if (y > 0 && !inRegion[p - width]) return -width
        if (y < height - 1 && !inRegion[p + width]) return width
        return 0
    }

    private fun next(p: Int, step: Int, width: Int, height: Int, horizontal: Boolean): Int? {
        val q = p + step
        if (horizontal) {
            if (q / width != p / width || q < 0 || q >= width * height) return null
        } else if (q < 0 || q >= width * height) return null
        return q
    }

    /** "Is pixel p in the region?" from shared labels, without a full-size copy per region. */
    class RegionTest(private val labels: IntArray, private val id: Int) {
        operator fun get(p: Int): Boolean = labels[p] == id
    }
}
