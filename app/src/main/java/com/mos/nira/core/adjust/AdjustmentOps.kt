package com.mos.nira.core.adjust

import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.ImageOps
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The adjustment intents Ask NIRA can choose. The AI picks one and its parameters; the engine does the work. */
enum class AdjustmentIntent {
    ADJUST_CHANNEL_STRENGTH,
    ADJUST_CHANNEL_CURVE,
    MERGE_COLOURS,
    RECOLOUR_INK,
    REMOVE_BACKGROUND,
    DESPECKLE,
    CHOKE_BASE,
    THICKEN_LINES,
    APPLY_COLOURWAY,
    COMPARE_BEFORE_AFTER
}

/**
 * Tone regions of an ink channel, by ink coverage:
 * HIGHLIGHTS = light ink coverage, MIDTONES = around 50%, SHADOWS = heavy ink coverage.
 */
enum class ToneRegion { SHADOWS, MIDTONES, HIGHLIGHTS }

/**
 * A deterministic engine operation on a [SeparationState]. It never changes the state it is
 * given; it returns a new one. No operation uses AI or generates image content.
 */
sealed interface AdjustmentOp {
    val intent: AdjustmentIntent

    /** One plain line saying what will be done. */
    val description: String

    fun apply(state: SeparationState, imageOps: ImageOps): SeparationState

    /** Extra plain-language remarks for this state, for example "less than one pixel". */
    fun notes(state: SeparationState): List<String> = emptyList()
}

object AdjustmentMath {
    /** Converts millimetres at print size to whole pixels. pixelsPerCm = image pixels / print width in cm. */
    fun mmToPixels(mm: Double, pixelsPerCm: Double): Int = (mm / 10.0 * pixelsPerCm).roundToInt()

    /** Area of one pixel in mm2 at print size. */
    fun pixelAreaMm2(pixelsPerCm: Double): Double = (10.0 / pixelsPerCm) * (10.0 / pixelsPerCm)

    fun requireChannel(state: SeparationState, ink: String): InkChannel =
        state.channel(ink) ?: throw IllegalArgumentException("No ink named '$ink'")
}

/** Multiplies one ink's coverage by (1 + percent / 100), limited to 0..100% ink. */
class AdjustChannelStrength(val ink: String, val percentChange: Double) : AdjustmentOp {
    override val intent = AdjustmentIntent.ADJUST_CHANNEL_STRENGTH
    override val description = "Change $ink strength by ${formatSigned(percentChange)}%"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val ch = AdjustmentMath.requireChannel(state, ink)
        val factor = (1.0 + percentChange / 100.0).toFloat()
        val coverage = FloatArray(ch.coverage.size) { (ch.coverage[it] * factor).coerceIn(0f, 1f) }
        return state.withChannel(ch.with(coverage = coverage))
    }
}

/**
 * Changes one ink's coverage in one tone region only. The change is strongest in the middle of
 * the region and fades to nothing outside it. Pixels with no ink stay at no ink.
 */
class AdjustChannelCurve(val ink: String, val region: ToneRegion, val percentChange: Double) : AdjustmentOp {
    override val intent = AdjustmentIntent.ADJUST_CHANNEL_CURVE
    override val description =
        "Change $ink ${region.name.lowercase()} by ${formatSigned(percentChange)}%"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val ch = AdjustmentMath.requireChannel(state, ink)
        val coverage = FloatArray(ch.coverage.size) { i ->
            val c = ch.coverage[i]
            (c * (1f + (percentChange / 100.0).toFloat() * weight(region, c))).coerceIn(0f, 1f)
        }
        return state.withChannel(ch.with(coverage = coverage))
    }

    companion object {
        /** 0..1 weight of a coverage value inside a tone region. */
        fun weight(region: ToneRegion, coverage: Float): Float = when (region) {
            ToneRegion.HIGHLIGHTS -> (1f - coverage / 0.5f).coerceIn(0f, 1f)
            ToneRegion.MIDTONES -> (1f - abs(coverage - 0.5f) / 0.5f).coerceIn(0f, 1f)
            ToneRegion.SHADOWS -> ((coverage - 0.5f) / 0.5f).coerceIn(0f, 1f)
        }
    }
}

/** Puts ink B's coverage onto ink A's screen and removes B. One screen fewer. */
class MergeColours(val inkA: String, val inkB: String) : AdjustmentOp {
    override val intent = AdjustmentIntent.MERGE_COLOURS
    override val description = "Merge $inkB into $inkA"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val a = AdjustmentMath.requireChannel(state, inkA)
        val b = AdjustmentMath.requireChannel(state, inkB)
        require(inkA != inkB) { "Cannot merge an ink with itself" }
        val coverage = FloatArray(a.coverage.size) { (a.coverage[it] + b.coverage[it]).coerceAtMost(1f) }
        val merged = a.with(coverage = coverage)
        return state.copy(channels = state.channels.filter { it.name != inkB }.map { if (it.name == inkA) merged else it })
    }
}

/** Changes the colour of one ink. Coverage is not touched. */
class RecolourInk(val ink: String, val newHex: String) : AdjustmentOp {
    override val intent = AdjustmentIntent.RECOLOUR_INK
    override val description = "Recolour $ink to $newHex"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val ch = AdjustmentMath.requireChannel(state, ink)
        return state.withChannel(ch.with(hex = newHex))
    }
}

/**
 * Removes ink from every pixel whose source colour is within the garment threshold (CIEDE2000)
 * of the given colour, and takes those pixels out of the print mask.
 */
class RemoveBackground(val colourHex: String, val label: String = colourHex) : AdjustmentOp {
    override val intent = AdjustmentIntent.REMOVE_BACKGROUND
    override val description = "Remove background colour $label"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val target = ColorConverter.rgbToLab(ColorConverter.hexToRgb(colourHex))
        val cache = HashMap<Int, Boolean>()
        val remove = BooleanArray(state.sourcePixels.size) { i ->
            val p = state.sourcePixels[i] and 0xFFFFFF
            cache.getOrPut(p) {
                val lab = ColorConverter.rgbToLab((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                ColorConverter.deltaE00(lab, target) <= state.garmentThresholdDeltaE00
            }
        }
        if (remove.none { it }) return state
        return state.copy(
            printMask = BooleanArray(remove.size) { state.printMask[it] && !remove[it] },
            channels = state.channels.map { ch ->
                ch.with(coverage = FloatArray(ch.coverage.size) { if (remove[it]) 0f else ch.coverage[it] })
            }
        )
    }
}

/** Removes ink islands smaller than minAreaMm2 (at print size) from every screen. */
class Despeckle(val minAreaMm2: Double) : AdjustmentOp {
    override val intent = AdjustmentIntent.DESPECKLE
    override val description = "Remove specks smaller than ${formatNumber(minAreaMm2)} mm2"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val pixelArea = AdjustmentMath.pixelAreaMm2(state.pixelsPerCm)
        var changed = false
        val channels = state.channels.map { ch ->
            val mask = BooleanArray(ch.coverage.size) { ch.coverage[it] > 0f }
            val components = imageOps.connectedComponents(mask, state.width, state.height)
            val drop = BooleanArray(components.count + 1) { label ->
                label > 0 && components.areas[label] * pixelArea < minAreaMm2
            }
            if (drop.none { it }) ch else {
                changed = true
                ch.with(coverage = FloatArray(ch.coverage.size) { if (drop[components.labels[it]]) 0f else ch.coverage[it] })
            }
        }
        return if (changed) state.copy(channels = channels) else state
    }

    override fun notes(state: SeparationState): List<String> = listOf(
        "One pixel is ${formatNumber(AdjustmentMath.pixelAreaMm2(state.pixelsPerCm))} mm2 at this print size."
    )
}

/**
 * Pulls the white base in from the print edge by the given distance, so it does not peek out
 * from under the top colours. The distance is measured from the edge of the whole print.
 */
class ChokeBase(val millimetres: Double) : AdjustmentOp {
    override val intent = AdjustmentIntent.CHOKE_BASE
    override val description = "Choke the white base by ${formatNumber(millimetres)} mm"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        val base = state.channels.firstOrNull { it.role == ChannelRole.BASE_WHITE } ?: return state
        val radius = AdjustmentMath.mmToPixels(millimetres, state.pixelsPerCm)
        if (radius <= 0) return state
        val printEdge = BooleanArray(state.sourcePixels.size) { i -> state.channels.any { it.coverage[i] > 0f } }
        val keep = imageOps.erode(printEdge, state.width, state.height, radius)
        return state.withChannel(
            base.with(coverage = FloatArray(base.coverage.size) { if (keep[it]) base.coverage[it] else 0f })
        )
    }

    override fun notes(state: SeparationState): List<String> {
        if (state.channels.none { it.role == ChannelRole.BASE_WHITE }) return listOf("There is no white base screen.")
        val radius = AdjustmentMath.mmToPixels(millimetres, state.pixelsPerCm)
        return if (radius <= 0) {
            listOf("${formatNumber(millimetres)} mm is less than one pixel at this resolution, so nothing changes.")
        } else {
            listOf("${formatNumber(millimetres)} mm = $radius px at ${formatNumber(state.pixelsPerCm)} px/cm.")
        }
    }
}

/**
 * Widens lines that are thinner than the minimum line width for the screen's mesh.
 * minLineWidthMmByMesh comes from print_rules.json (detail_rules). A screen whose mesh has no
 * rule is left alone and reported as "not available".
 *
 * Line width is estimated from the distance transform (about 2 x d - 1 pixels on the centre
 * line), which reads even widths one pixel low, so a line may end up slightly wider than the
 * minimum but never narrower. Line ends grow by the same radius.
 */
class ThickenLines(val ink: String?, val minLineWidthMmByMesh: Map<Int, Double>) : AdjustmentOp {
    override val intent = AdjustmentIntent.THICKEN_LINES
    override val description = "Thicken thin lines in ${ink ?: "all colour screens"} to the mesh minimum"

    private fun targets(state: SeparationState): List<InkChannel> =
        if (ink != null) listOf(AdjustmentMath.requireChannel(state, ink))
        else state.channels.filter { it.role == ChannelRole.COLOUR }

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        var result = state
        for (ch in targets(state)) {
            val minMm = minLineWidthMmByMesh[ch.meshTpi] ?: continue
            val minPx = ceil(minMm / 10.0 * state.pixelsPerCm).toInt()
            if (minPx <= 1) continue
            val w = state.width
            val h = state.height
            val mask = BooleanArray(ch.coverage.size) { ch.coverage[it] > 0f }
            val dist = imageOps.distanceTransform(mask, w, h)

            // Centre-line pixels (local maximum of the distance) of lines thinner than the minimum.
            val thinCentre = BooleanArray(mask.size)
            var any = false
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (!mask[i]) continue
                    val d = dist[i]
                    if (2 * d - 1 >= minPx) continue
                    var isRidge = true
                    loop@ for (dy in -1..1) {
                        val ny = y + dy
                        if (ny < 0 || ny >= h) continue
                        for (dx in -1..1) {
                            val nx = x + dx
                            if (nx < 0 || nx >= w) continue
                            if (dist[ny * w + nx] > d) {
                                isRidge = false
                                break@loop
                            }
                        }
                    }
                    if (isRidge) {
                        thinCentre[i] = true
                        any = true
                    }
                }
            }
            if (!any) continue

            val radius = ceil((minPx - 1) / 2.0).toInt()
            val grown = imageOps.dilate(thinCentre, w, h, radius)
            val coverage = ch.coverage.copyOf()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (!grown[i] || mask[i]) continue
                    // New pixels take the strongest ink value of the line they grew from.
                    var best = 0f
                    for (ny in maxOf(0, y - radius)..minOf(h - 1, y + radius)) {
                        for (nx in maxOf(0, x - radius)..minOf(w - 1, x + radius)) {
                            val c = ch.coverage[ny * w + nx]
                            if (c > best) best = c
                        }
                    }
                    coverage[i] = best
                }
            }
            result = result.withChannel(ch.with(coverage = coverage))
        }
        return result
    }

    override fun notes(state: SeparationState): List<String> = targets(state).map { ch ->
        val minMm = minLineWidthMmByMesh[ch.meshTpi]
        if (minMm == null) "${ch.name}: minimum line width for ${ch.meshTpi} tpi is not available in the rules file."
        else "${ch.name}: minimum ${formatNumber(minMm)} mm at ${ch.meshTpi} tpi = " +
            "${ceil(minMm / 10.0 * state.pixelsPerCm).toInt()} px."
    }
}

/** Recolours several inks at once and saves the set under a name. */
class ApplyColourway(val name: String, val inkToHex: Map<String, String>) : AdjustmentOp {
    override val intent = AdjustmentIntent.APPLY_COLOURWAY
    override val description = "Apply colourway \"$name\" (${inkToHex.size} inks)"

    override fun apply(state: SeparationState, imageOps: ImageOps): SeparationState {
        inkToHex.keys.forEach { AdjustmentMath.requireChannel(state, it) }
        return state.copy(
            channels = state.channels.map { ch -> inkToHex[ch.name]?.let { ch.with(hex = it) } ?: ch },
            colourways = state.colourways + (name to inkToHex.toMap())
        )
    }
}

internal fun formatSigned(value: Double): String =
    (if (value > 0) "+" else "") + formatNumber(value)

internal fun formatNumber(value: Double): String =
    if (value == Math.rint(value) && abs(value) < 1e9) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)
