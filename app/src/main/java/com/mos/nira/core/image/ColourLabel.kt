package com.mos.nira.core.image

import com.mos.nira.features.artwork.domain.ShadeEntry

/** Readable labels for colours, using the names in the user's own shade library. */
object ColourLabel {

    /**
     * "Neutral 56 (#6A6969)": the name of the nearest shade in the library (CIEDE2000), then the
     * hex. Just the hex when the library is empty or the hex cannot be read.
     */
    fun withNearestShadeName(hex: String, shades: List<ShadeEntry>): String {
        if (shades.isEmpty()) return hex
        val lab = try {
            val rgb = ColorConverter.hexToRgb(hex)
            ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
        } catch (e: IllegalArgumentException) {
            return hex
        }
        val nearest = shades.minByOrNull {
            ColorConverter.deltaE00(lab, ColorConverter.Lab(it.labL, it.labA, it.labB))
        } ?: return hex
        return "${nearest.name} ($hex)"
    }
}
