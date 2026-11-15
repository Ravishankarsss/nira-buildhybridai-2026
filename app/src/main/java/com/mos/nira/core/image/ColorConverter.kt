package com.mos.nira.core.image

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Technical color space conversions.
 *
 * Implements conversion between:
 * - RGB and 24-bit HEX
 * - sRGB (gamma-corrected) and Linear RGB
 * - Linear RGB and CIE XYZ (under CIE Standard Illuminant D65 with 2° standard observer)
 * - CIE XYZ and CIE L*a*b* (CIELAB 1976)
 * - CIE Delta E (1976 color difference formula)
 *
 * DISCLAIMER:
 * Calculated CIE L*a*b* values are mathematical estimations derived from digital sRGB pixel
 * inputs under standard D65 assumptions, not direct physical spectrophotometer reflectance measurements.
 */
object ColorConverter {

    // CIE D65 Standard Illuminant Tristimulus Values (normalized to Y = 1.0)
    // Reference: CIE 15:2004 / ASTM E308-01
    private const val XN = 0.95047
    private const val YN = 1.00000
    private const val ZN = 1.08883

    // CIE standard parameters
    private const val EPSILON = 216.0 / 24389.0 // 0.008856
    private const val KAPPA = 24389.0 / 27.0    // 903.3

    data class Rgb(val r: Int, val g: Int, val b: Int) {
        init {
            require(r in 0..255 && g in 0..255 && b in 0..255) {
                "RGB values must be in [0, 255]. Got ($r, $g, $b)"
            }
        }
    }

    data class Lab(val l: Double, val a: Double, val b: Double)

    /**
     * Converts RGB values to standard 6-character hexadecimal string with leading hash (e.g., #8B7CFF).
     */
    fun rgbToHex(r: Int, g: Int, b: Int): String {
        val clampedR = r.coerceIn(0, 255)
        val clampedG = g.coerceIn(0, 255)
        val clampedB = b.coerceIn(0, 255)
        return String.format("#%02X%02X%02X", clampedR, clampedG, clampedB)
    }

    fun rgbToHex(rgb: Rgb): String = rgbToHex(rgb.r, rgb.g, rgb.b)

    /**
     * Converts a 24-bit ARGB integer (e.g. from Android Color / Bitmap) to HEX.
     */
    fun intToHex(colorInt: Int): String {
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        return rgbToHex(r, g, b)
    }

    /**
     * Parses a HEX color string (with or without leading '#', 6 or 8 chars) to RGB.
     */
    fun hexToRgb(hex: String): Rgb {
        val cleanHex = hex.trim().removePrefix("#")
        require(cleanHex.length == 6 || cleanHex.length == 8) {
            "Invalid hex color format: $hex"
        }
        val startIdx = if (cleanHex.length == 8) 2 else 0
        val r = cleanHex.substring(startIdx, startIdx + 2).toInt(16)
        val g = cleanHex.substring(startIdx + 2, startIdx + 4).toInt(16)
        val b = cleanHex.substring(startIdx + 4, startIdx + 6).toInt(16)
        return Rgb(r, g, b)
    }

    /**
     * Converts non-linear sRGB channel [0..255] to linear RGB [0.0..1.0].
     * Reference: IEC 61966-2-1:1999 standard sRGB.
     */
    fun sRgbToLinear(c8: Int): Double {
        val c = c8.coerceIn(0, 255) / 255.0
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * Converts linear RGB channel [0.0..1.0] back to non-linear sRGB [0..255].
     */
    fun linearToSRgb(linear: Double): Int {
        val clamped = linear.coerceIn(0.0, 1.0)
        val s = if (clamped <= 0.0031308) {
            12.92 * clamped
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }

    /**
     * Converts sRGB [0..255] to CIE XYZ tristimulus values using D65 reference white.
     */
    fun rgbToXyz(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val rLinear = sRgbToLinear(r)
        val gLinear = sRgbToLinear(g)
        val bLinear = sRgbToLinear(b)

        // Matrix transform from sRGB to CIE XYZ (D65)
        val x = rLinear * 0.4124564 + gLinear * 0.3575761 + bLinear * 0.1804375
        val y = rLinear * 0.2126729 + gLinear * 0.7151522 + bLinear * 0.0721750
        val z = rLinear * 0.0193339 + gLinear * 0.1191920 + bLinear * 0.9503041

        return Triple(x, y, z)
    }

    /**
     * Converts CIE XYZ to CIE L*a*b* (CIELAB 1976).
     * L* in [0, 100], a* in [-128, 127], b* in [-128, 127].
     */
    fun xyzToLab(x: Double, y: Double, z: Double): Lab {
        val xr = x / XN
        val yr = y / YN
        val zr = z / ZN

        fun f(t: Double): Double {
            return if (t > EPSILON) {
                t.pow(1.0 / 3.0)
            } else {
                (KAPPA * t + 16.0) / 116.0
            }
        }

        val fx = f(xr)
        val fy = f(yr)
        val fz = f(zr)

        val l = (116.0 * fy) - 16.0
        val a = 500.0 * (fx - fy)
        val b = 200.0 * (fy - fz)

        return Lab(
            l = l.coerceIn(0.0, 100.0),
            a = a,
            b = b
        )
    }

    /**
     * Direct pipeline from sRGB [0..255] to estimated CIE L*a*b*.
     */
    fun rgbToLab(r: Int, g: Int, b: Int): Lab {
        val (x, y, z) = rgbToXyz(r, g, b)
        return xyzToLab(x, y, z)
    }

    fun rgbToLab(rgb: Rgb): Lab = rgbToLab(rgb.r, rgb.g, rgb.b)

    /**
     * Calculates the CIE Delta E (1976) Euclidean color difference between two Lab colors.
     * ΔE < 1.0: Not perceptible by human eyes.
     * 1.0 <= ΔE < 3.0: Perceptible through close observation.
     * 3.0 <= ΔE < 6.0: Noticeable difference.
     * ΔE >= 6.0: Distinct color difference.
     */
    fun deltaE(lab1: Lab, lab2: Lab): Double {
        val dl = lab1.l - lab2.l
        val da = lab1.a - lab2.a
        val db = lab1.b - lab2.b
        return sqrt(dl * dl + da * da + db * db)
    }

    /**
     * Calculates color difference directly between two RGB colors using CIE L*a*b* space.
     */
    fun colorDistanceLab(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val lab1 = rgbToLab(r1, g1, b1)
        val lab2 = rgbToLab(r2, g2, b2)
        return deltaE(lab1, lab2)
    }
}
