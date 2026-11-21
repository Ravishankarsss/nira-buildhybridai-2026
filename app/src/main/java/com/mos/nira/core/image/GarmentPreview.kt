package com.mos.nira.core.image

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "See it on your garment": the print drawn on a chosen garment colour with the ink model from
 * print_rules.json (ink_model). Everything runs on the phone; nothing goes to the cloud.
 *
 * Without a white base the ink is transparent over the garment (ink_model.light_garment: the
 * garment shows through, printed = garment x ink in linear RGB). With a white base the ink sits on
 * white, so it shows its own colour. This is a model preview, not a proof: no dot gain, no
 * registration, no ink opacity value (the rules file has none).
 */
object GarmentPreview {

    private fun lin(v: Int) = ColorConverter.sRgbToLinear(v)

    /**
     * [pixels] and [printMask] have the same size. Fabric pixels become [garment]; each print pixel
     * uses [inkFor] (its ink colour as printed on white, ARGB) through the ink model.
     */
    fun render(
        pixels: IntArray,
        printMask: BooleanArray,
        garment: ColorConverter.Rgb,
        withWhiteBase: Boolean,
        inkFor: (index: Int, pixel: Int) -> Int = { _, p -> p }
    ): IntArray {
        val gR = lin(garment.r)
        val gG = lin(garment.g)
        val gB = lin(garment.b)
        val garmentArgb = (0xFF shl 24) or (garment.r shl 16) or (garment.g shl 8) or garment.b
        return IntArray(pixels.size) { i ->
            if (!printMask[i]) garmentArgb
            else {
                val ink = inkFor(i, pixels[i])
                if (withWhiteBase) (0xFF shl 24) or (ink and 0xFFFFFF)
                else {
                    val r = ColorConverter.linearToSRgb(gR * lin((ink shr 16) and 0xFF))
                    val g = ColorConverter.linearToSRgb(gG * lin((ink shr 8) and 0xFF))
                    val b = ColorConverter.linearToSRgb(gB * lin(ink and 0xFF))
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
    }

    /**
     * An ink that will be hard to see on the garment: its lightness is too close. [sameNameHexes] are
     * all measured shades with the same colour name (one warning per name; a swap covers them all).
     */
    data class ContrastWarning(val inkHex: String, val deltaL: Int, val sameNameHexes: List<String> = listOf(inkHex))

    /** Inks whose lightness differs from the garment's by less than [minDeltaL] (L* units). */
    fun contrastWarnings(inkHexes: List<String>, garmentHex: String, minDeltaL: Double): List<ContrastWarning> {
        val gl = lab(garmentHex).l
        return inkHexes.mapNotNull { hex ->
            val d = abs(lab(hex).l - gl)
            if (d < minDeltaL) ContrastWarning(hex, d.roundToInt()) else null
        }
    }

    /**
     * One warning per colour name ("Navy" once, not once per navy shade). The kept warning is the
     * closest one (lowest lightness difference); it lists every shade so a swap changes them all.
     */
    fun mergeByName(warnings: List<ContrastWarning>, nameOf: (String) -> String = ColourName::english): List<ContrastWarning> =
        warnings.groupBy { nameOf(it.inkHex) }.values.map { group ->
            group.minBy { it.deltaL }.copy(sameNameHexes = group.map { it.inkHex })
        }

    /**
     * Swap colours that are worth offering on this garment: only those that pass the same contrast
     * limit themselves (no "Use white" on a white garment).
     */
    fun usefulSwaps(swapColours: List<Pair<String, String>>, garmentHex: String, minDeltaL: Double): List<Pair<String, String>> {
        val gl = lab(garmentHex).l
        return swapColours.filter { (_, hex) -> abs(lab(hex).l - gl) >= minDeltaL }
    }

    /**
     * True when every ink, printed without a white base (the transparent ink model above), stays
     * within [minDeltaL] of the garment's lightness: the print almost disappears without a base.
     */
    fun inksVanishWithoutBase(inkHexes: List<String>, garmentHex: String, minDeltaL: Double): Boolean {
        if (inkHexes.isEmpty()) return false
        val g = ColorConverter.hexToRgb(garmentHex)
        val gl = lab(garmentHex).l
        return inkHexes.all { hex ->
            val ink = ColorConverter.hexToRgb(hex)
            val r = ColorConverter.linearToSRgb(lin(g.r) * lin(ink.r))
            val gg = ColorConverter.linearToSRgb(lin(g.g) * lin(ink.g))
            val b = ColorConverter.linearToSRgb(lin(g.b) * lin(ink.b))
            abs(ColorConverter.rgbToLab(r, gg, b).l - gl) < minDeltaL
        }
    }

    private fun lab(hex: String) = ColorConverter.hexToRgb(hex).let { ColorConverter.rgbToLab(it.r, it.g, it.b) }
}
