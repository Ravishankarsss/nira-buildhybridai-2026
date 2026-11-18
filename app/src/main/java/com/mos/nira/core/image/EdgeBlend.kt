package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.EdgeBlendRules

/**
 * print_rules.json "edge_blend": thin bands of mixed colour along the border between two inks (or
 * ink and fabric), from camera blur or artwork anti-aliasing. Every limit comes from the file.
 */
object EdgeBlend {

    /** Where a colour sits between two others: ratio t (0 = first, 1 = second) and its distance off the line. */
    data class MixFit(val t: Double, val deltaE00: Double)

    private fun linear(c: ColorConverter.Rgb) =
        doubleArrayOf(ColorConverter.sRgbToLinear(c.r), ColorConverter.sRgbToLinear(c.g), ColorConverter.sRgbToLinear(c.b))

    /**
     * Mixing ratio of [c] between [a] and [b] in linear RGB (light mixes linearly), and the
     * CIEDE2000 distance from [c] to that mix. t is not clamped, so t outside 0..1 means "not between".
     */
    fun mixFit(c: ColorConverter.Rgb, a: ColorConverter.Rgb, b: ColorConverter.Rgb): MixFit {
        val lc = linear(c)
        val la = linear(a)
        val lb = linear(b)
        var dot = 0.0
        var len = 0.0
        for (k in 0..2) {
            val d = lb[k] - la[k]
            dot += (lc[k] - la[k]) * d
            len += d * d
        }
        if (len <= 0.0) return MixFit(0.0, ColorConverter.deltaE00(ColorConverter.rgbToLab(c), ColorConverter.rgbToLab(a)))
        val t = dot / len
        val tc = t.coerceIn(0.0, 1.0)
        val mix = ColorConverter.Rgb(
            ColorConverter.linearToSRgb(la[0] + tc * (lb[0] - la[0])),
            ColorConverter.linearToSRgb(la[1] + tc * (lb[1] - la[1])),
            ColorConverter.linearToSRgb(la[2] + tc * (lb[2] - la[2]))
        )
        return MixFit(t, ColorConverter.deltaE00(ColorConverter.rgbToLab(c), ColorConverter.rgbToLab(mix)))
    }

    /** Only the mixing ratio of [c] between [a] (0) and [b] (1) in linear RGB, unclamped. */
    fun mixRatio(c: ColorConverter.Rgb, a: ColorConverter.Rgb, b: ColorConverter.Rgb): Double {
        val lc = linear(c)
        val la = linear(a)
        val lb = linear(b)
        var dot = 0.0
        var len = 0.0
        for (k in 0..2) {
            val d = lb[k] - la[k]
            dot += (lc[k] - la[k]) * d
            len += d * d
        }
        return if (len <= 0.0) 0.0 else dot / len
    }

    /**
     * Widest band (pixels, in the analysed image) that can be blur. Artwork: band_max_px_artwork.
     * Photo: the measured blur times the factor, kept between the minimum and the cap; the fallback
     * when no clean edge could be measured.
     */
    fun bandMaxPx(isPhoto: Boolean, measuredBlurPx: Double?, rules: EdgeBlendRules): Double = when {
        !isPhoto -> rules.bandMaxPxArtwork
        measuredBlurPx == null -> rules.bandMaxPxFallback
        else -> (measuredBlurPx * rules.bandMaxPxFromMeasuredBlurFactor).coerceIn(rules.bandMaxPxMin, rules.bandMaxPxCap)
    }

    /**
     * One crossing of a candidate band: the mixing ratios of its pixels, in order from one
     * neighbour to the other. Blur changes step by step with no flat part; a printed line has a
     * flat middle (plateau_min_px pixels within plateau_ratio_tolerance). With monotonic_required,
     * the ratios must not turn back by more than the tolerance.
     */
    fun crossingLooksLikeBlur(ratios: List<Double>, rules: EdgeBlendRules): Boolean {
        if (ratios.isEmpty()) return false
        // Flat middle: plateau_min_px consecutive pixels with the same ratio within the tolerance.
        val run = rules.plateauMinPx
        if (run >= 2 && ratios.size >= run) {
            for (i in 0..ratios.size - run) {
                val window = ratios.subList(i, i + run)
                if (window.max() - window.min() <= rules.plateauRatioTolerance) return false
            }
        }
        if (rules.monotonicRequired && ratios.size >= 2) {
            val direction = if (ratios.last() >= ratios.first()) 1.0 else -1.0
            for (i in 1 until ratios.size) {
                if ((ratios[i] - ratios[i - 1]) * direction < -rules.plateauRatioTolerance) return false
            }
        }
        return true
    }

    /** Median of a list, or null when it is empty. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}
