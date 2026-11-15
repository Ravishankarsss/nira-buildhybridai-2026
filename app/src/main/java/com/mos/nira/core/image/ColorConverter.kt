package com.mos.nira.core.image

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Technical color space conversions.
 *
 * Implements conversion between:
 * - RGB and 24-bit HEX
 * - sRGB (gamma-corrected) and Linear RGB
 * - Linear RGB and CIE XYZ (under CIE Standard Illuminant D65 with 2° standard observer)
 * - CIE XYZ and CIE L*a*b* (CIELAB 1976)
 * - CIE Delta E 1976 (ΔE76) - preserved for baseline comparison and unit tests
 * - CIEDE2000 (ΔE00) - modern standard metric for all industrial matching and cluster merging
 *
 * Reference: Sharma, Wu, Dalal (2005) "The CIEDE2000 Color-Difference Formula: Implementation Notes,
 * Supplementary Technical Data, and Mathematical Observations"
 */
object ColorConverter {

    private const val XN = 0.95047
    private const val YN = 1.00000
    private const val ZN = 1.08883

    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    data class Rgb(val r: Int, val g: Int, val b: Int) {
        init {
            require(r in 0..255 && g in 0..255 && b in 0..255) {
                "RGB values must be in [0, 255]. Got ($r, $g, $b)"
            }
        }
    }

    data class Lab(val l: Double, val a: Double, val b: Double)

    fun rgbToHex(r: Int, g: Int, b: Int): String {
        val clampedR = r.coerceIn(0, 255)
        val clampedG = g.coerceIn(0, 255)
        val clampedB = b.coerceIn(0, 255)
        return String.format("#%02X%02X%02X", clampedR, clampedG, clampedB)
    }

    fun rgbToHex(rgb: Rgb): String = rgbToHex(rgb.r, rgb.g, rgb.b)

    fun intToHex(colorInt: Int): String {
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        return rgbToHex(r, g, b)
    }

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

    fun sRgbToLinear(c8: Int): Double {
        val c = c8.coerceIn(0, 255) / 255.0
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    fun linearToSRgb(linear: Double): Int {
        val clamped = linear.coerceIn(0.0, 1.0)
        val s = if (clamped <= 0.0031308) {
            12.92 * clamped
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }

    fun rgbToXyz(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val rLinear = sRgbToLinear(r)
        val gLinear = sRgbToLinear(g)
        val bLinear = sRgbToLinear(b)

        val x = rLinear * 0.4124564 + gLinear * 0.3575761 + bLinear * 0.1804375
        val y = rLinear * 0.2126729 + gLinear * 0.7151522 + bLinear * 0.0721750
        val z = rLinear * 0.0193339 + gLinear * 0.1191920 + bLinear * 0.9503041

        return Triple(x, y, z)
    }

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

    fun rgbToLab(r: Int, g: Int, b: Int): Lab {
        val (x, y, z) = rgbToXyz(r, g, b)
        return xyzToLab(x, y, z)
    }

    fun rgbToLab(rgb: Rgb): Lab = rgbToLab(rgb.r, rgb.g, rgb.b)

    fun labToXyz(l: Double, a: Double, b: Double): Triple<Double, Double, Double> {
        val fy = (l + 16.0) / 116.0
        val fx = (a / 500.0) + fy
        val fz = fy - (b / 200.0)

        val fx3 = fx.pow(3.0)
        val fz3 = fz.pow(3.0)

        val xr = if (fx3 > EPSILON) fx3 else (116.0 * fx - 16.0) / KAPPA
        val yr = if (l > KAPPA * EPSILON) ((l + 16.0) / 116.0).pow(3.0) else l / KAPPA
        val zr = if (fz3 > EPSILON) fz3 else (116.0 * fz - 16.0) / KAPPA

        val x = xr * XN
        val y = yr * YN
        val z = zr * ZN
        return Triple(x, y, z)
    }

    fun xyzToRgb(x: Double, y: Double, z: Double): Rgb {
        val rLinear = x * 3.2404542 + y * -1.5371385 + z * -0.4985314
        val gLinear = x * -0.9692660 + y * 1.8760108 + z * 0.0415560
        val bLinear = x * 0.0556434 + y * -0.2040259 + z * 1.0572252

        return Rgb(
            r = linearToSRgb(rLinear),
            g = linearToSRgb(gLinear),
            b = linearToSRgb(bLinear)
        )
    }

    fun labToRgb(l: Double, a: Double, b: Double): Rgb {
        val (x, y, z) = labToXyz(l, a, b)
        return xyzToRgb(x, y, z)
    }

    fun deltaE76(lab1: Lab, lab2: Lab): Double {
        val dl = lab1.l - lab2.l
        val da = lab1.a - lab2.a
        val db = lab1.b - lab2.b
        return sqrt(dl * dl + da * da + db * db)
    }

    @Deprecated("Use deltaE00 for perceptual matching", ReplaceWith("deltaE00(lab1, lab2)"))
    fun deltaE(lab1: Lab, lab2: Lab): Double = deltaE76(lab1, lab2)

    /**
     * CIEDE2000 (ΔE00) Color Difference Formula conforming strictly to Sharma et al. (2005).
     */
    fun deltaE00(
        lab1: Lab,
        lab2: Lab,
        kL: Double = 1.0,
        kC: Double = 1.0,
        kH: Double = 1.0
    ): Double {
        val l1 = lab1.l
        val a1 = lab1.a
        val b1 = lab1.b

        val l2 = lab2.l
        val a2 = lab2.a
        val b2 = lab2.b

        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val cBar = (c1 + c2) / 2.0

        val cBar7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + 25.0.pow(7.0))))

        val a1Prime = a1 * (1.0 + g)
        val a2Prime = a2 * (1.0 + g)

        val c1Prime = hypot(a1Prime, b1)
        val c2Prime = hypot(a2Prime, b2)

        fun computeHPrime(aP: Double, b: Double): Double {
            if (aP == 0.0 && b == 0.0) return 0.0
            val deg = Math.toDegrees(atan2(b, aP))
            return if (deg < 0.0) deg + 360.0 else deg
        }

        val h1Prime = computeHPrime(a1Prime, b1)
        val h2Prime = computeHPrime(a2Prime, b2)

        val deltaLPrime = l2 - l1
        val deltaCPrime = c2Prime - c1Prime

        val hDiff = h2Prime - h1Prime
        val deltaHPrimeDeg: Double = when {
            c1Prime * c2Prime == 0.0 -> 0.0
            abs(hDiff) <= 180.0 -> hDiff
            hDiff > 180.0 -> hDiff - 360.0
            else -> hDiff + 360.0
        }

        val deltaHPrime = 2.0 * sqrt(c1Prime * c2Prime) * sin(Math.toRadians(deltaHPrimeDeg / 2.0))

        val lBarPrime = (l1 + l2) / 2.0
        val cBarPrime = (c1Prime + c2Prime) / 2.0

        val hBarPrime: Double = when {
            c1Prime * c2Prime == 0.0 -> h1Prime + h2Prime
            abs(hDiff) <= 180.0 -> (h1Prime + h2Prime) / 2.0
            h1Prime + h2Prime < 360.0 -> (h1Prime + h2Prime + 360.0) / 2.0
            else -> (h1Prime + h2Prime - 360.0) / 2.0
        }

        val t = 1.0 -
                0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
                0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
                0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
                0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

        val deltaTheta = 30.0 * exp(-(((hBarPrime - 275.0) / 25.0).pow(2.0)))

        val cBarPrime7 = cBarPrime.pow(7.0)
        val rC = 2.0 * sqrt(cBarPrime7 / (cBarPrime7 + 25.0.pow(7.0)))
        val sL = 1.0 + (0.015 * (lBarPrime - 50.0).pow(2.0)) / sqrt(20.0 + (lBarPrime - 50.0).pow(2.0))
        val sC = 1.0 + 0.045 * cBarPrime
        val sH = 1.0 + 0.015 * cBarPrime * t
        val rT = -sin(Math.toRadians(2.0 * deltaTheta)) * rC

        val termL = deltaLPrime / (kL * sL)
        val termC = deltaCPrime / (kC * sC)
        val termH = deltaHPrime / (kH * sH)

        val deltaE00Sq = termL * termL + termC * termC + termH * termH + rT * termC * termH
        return sqrt(maxOf(0.0, deltaE00Sq))
    }

    fun colorDistanceE00(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val lab1 = rgbToLab(r1, g1, b1)
        val lab2 = rgbToLab(r2, g2, b2)
        return deltaE00(lab1, lab2)
    }

    fun matchGrade(deltaE: Double): String = when {
        deltaE < 1.0 -> "Excellent (< 1.0 ΔE00)"
        deltaE <= 2.0 -> "Good (1.0–2.0 ΔE00)"
        deltaE <= 4.0 -> "Visible (2.0–4.0 ΔE00)"
        else -> "Poor (> 4.0 ΔE00)"
    }
}
