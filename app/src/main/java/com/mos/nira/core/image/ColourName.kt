package com.mos.nira.core.image

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A plain colour name (navy, deep red, light grey...) for a hex colour, so users and judges do
 * not have to read colour codes. Display only: it never changes a measurement or a decision.
 * The name comes from CIELAB lightness, chroma and hue angle. The word limits below are naming
 * conventions for the screen, not print rules.
 */
object ColourName {

    /** Base colour words. Each has a string resource "colour_<id>" in every language. */
    enum class Base { WHITE, LIGHT_GREY, GREY, DARK_GREY, BLACK, RED, PINK, MAGENTA, ORANGE, BROWN, YELLOW, OLIVE, CREAM, GREEN, TEAL, BLUE, NAVY, PURPLE }

    enum class Shade { NONE, DARK, LIGHT }

    data class Name(val base: Base, val shade: Shade) {
        /** English, for the AI facts: "dark green", "navy", "light grey". */
        val english: String
            get() {
                val word = base.name.lowercase().replace('_', ' ')
                return when (shade) {
                    Shade.NONE -> word
                    Shade.DARK -> "dark $word"
                    Shade.LIGHT -> "light $word"
                }
            }
    }

    fun of(hex: String): Name? {
        val rgb = try { ColorConverter.hexToRgb(hex) } catch (e: Exception) { return null }
        val lab = ColorConverter.rgbToLab(rgb.r, rgb.g, rgb.b)
        return of(lab.l, lab.a, lab.b)
    }

    fun of(l: Double, a: Double, b: Double): Name {
        val c = hypot(a, b)
        if (c < 8.0) {
            return Name(
                when {
                    l > 90 -> Base.WHITE
                    l > 70 -> Base.LIGHT_GREY
                    l > 40 -> Base.GREY
                    l > 18 -> Base.DARK_GREY
                    else -> Base.BLACK
                },
                Shade.NONE
            )
        }
        var h = Math.toDegrees(atan2(b, a))
        if (h < 0) h += 360.0
        val base = when {
            h >= 345 || h < 20 -> if (l > 55) Base.PINK else Base.MAGENTA
            h < 50 -> if (l > 70) Base.PINK else Base.RED
            h < 75 -> if (l < 50) Base.BROWN else Base.ORANGE
            h < 105 -> when {
                l > 85 && c < 30 -> Base.CREAM
                l < 60 -> if (c < 45) Base.BROWN else Base.OLIVE
                else -> Base.YELLOW
            }
            h < 165 -> Base.GREEN
            h < 225 -> Base.TEAL
            h < 315 -> if (l < 35) Base.NAVY else Base.BLUE
            else -> Base.PURPLE
        }
        val shade = when {
            base in setOf(Base.NAVY, Base.BROWN, Base.OLIVE, Base.CREAM, Base.PINK, Base.YELLOW) -> Shade.NONE
            l < 35 -> Shade.DARK
            l > 75 -> Shade.LIGHT
            else -> Shade.NONE
        }
        return Name(base, shade)
    }

    /** English name, or the hex when it cannot be read. */
    fun english(hex: String): String = of(hex)?.english ?: hex
}
