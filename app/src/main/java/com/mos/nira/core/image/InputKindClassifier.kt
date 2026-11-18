package com.mos.nira.core.image

import com.mos.nira.features.artwork.data.PhotoVsArtworkRules
import kotlin.math.abs
import kotlin.math.sqrt

/** What the user loaded: a photo of a garment, or a flat artwork file. */
enum class InputKind { PHOTO, ARTWORK }

data class InputKindAssessment(
    /** The user's chosen mode. This is always the answer used (print_rules.json "primary"). */
    val chosen: InputKind,
    /** Camera information / transparency vote; null when the file information is not known. */
    val fileInfoVote: InputKind?,
    /** Robust lightness noise (L* standard deviation) in plain background areas. */
    val flatNoiseLStd: Double?,
    val noiseVote: InputKind?,
    /** Lightness range of the background across the frame (L* units). */
    val backgroundLRange: Double?,
    val rangeVote: InputKind?,
    /** How many of the three signals disagree with the chosen mode. */
    val signalsDisagreeing: Int,
    /** True when the app should ask "This looks like a photo. Treat it as a photo?" (or the reverse). */
    val askUser: Boolean,
    /** What most signals point to; null when they are split or missing. */
    val signalsSuggest: InputKind?
)

/**
 * Tells a garment photo from flat artwork (print_rules.json "photo_vs_artwork"). The user's
 * chosen mode is used; the three signals only decide whether to ask the user to confirm.
 * Every limit comes from print_rules.json.
 */
object InputKindClassifier {

    /** Grid used to measure lighting change across the background (cells per side). */
    private const val LIGHTING_GRID = 8

    /** Fewest neighbour pairs or background pixels per cell needed before a signal is trusted. */
    private const val MIN_NOISE_PAIRS = 100
    private const val MIN_CELL_PIXELS = 16

    /**
     * [backgroundMask] marks plain fabric or artwork background (true = background).
     * [cameraExif] is true when the file has camera make or exposure information, false when it
     * has none, null when unknown. [hasTransparency] is true for an image with transparent pixels.
     */
    fun assess(
        chosen: InputKind,
        pixels: IntArray,
        width: Int,
        height: Int,
        backgroundMask: BooleanArray,
        cameraExif: Boolean?,
        hasTransparency: Boolean,
        rules: PhotoVsArtworkRules
    ): InputKindAssessment {
        val labCache = HashMap<Int, Double>()
        fun lOf(p: Int): Double {
            val c = pixels[p] and 0xFFFFFF
            return labCache.getOrPut(c) { ColorConverter.rgbToLab((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).l }
        }

        // Signal 2: file information. Transparency almost always means artwork.
        val fileInfoVote = when {
            hasTransparency -> InputKind.ARTWORK
            cameraExif == true -> InputKind.PHOTO
            cameraExif == false -> InputKind.ARTWORK
            else -> null
        }

        // Signal 3: noise in plain areas, from differences between horizontal neighbours.
        // The robust spread (median absolute deviation) ignores the few pairs that cross an
        // edge, and a smooth lighting change barely affects neighbour differences.
        val diffs = ArrayList<Double>()
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width - 1) {
                val p = row + x
                if (backgroundMask[p] && backgroundMask[p + 1]) diffs.add(lOf(p + 1) - lOf(p))
            }
        }
        val flatNoise = if (diffs.size >= MIN_NOISE_PAIRS) {
            val med = median(diffs)
            // 1.4826 turns a median absolute deviation into a standard deviation (normal noise);
            // dividing by sqrt(2) turns the noise of a difference into the noise of one pixel.
            1.4826 * median(diffs.map { abs(it - med) }) / sqrt(2.0)
        } else null
        val noiseVote = when {
            flatNoise == null -> null
            flatNoise <= rules.artworkFlatNoiseLStdMax -> InputKind.ARTWORK
            flatNoise >= rules.photoFlatNoiseLStdMin -> InputKind.PHOTO
            else -> null
        }

        // Signal 4: lighting change across the background (median lightness per grid cell).
        val cellMedians = ArrayList<Double>()
        for (gy in 0 until LIGHTING_GRID) {
            for (gx in 0 until LIGHTING_GRID) {
                val values = ArrayList<Double>()
                for (y in gy * height / LIGHTING_GRID until (gy + 1) * height / LIGHTING_GRID) {
                    for (x in gx * width / LIGHTING_GRID until (gx + 1) * width / LIGHTING_GRID) {
                        val p = y * width + x
                        if (backgroundMask[p]) values.add(lOf(p))
                    }
                }
                if (values.size >= MIN_CELL_PIXELS) cellMedians.add(median(values))
            }
        }
        val range = if (cellMedians.size >= 2) cellMedians.max() - cellMedians.min() else null
        val rangeVote = when {
            range == null -> null
            range >= rules.photoBackgroundLRangeMin -> InputKind.PHOTO
            else -> InputKind.ARTWORK
        }

        val votes = listOfNotNull(fileInfoVote, noiseVote, rangeVote)
        val disagreeing = votes.count { it != chosen }
        val photoVotes = votes.count { it == InputKind.PHOTO }
        val artworkVotes = votes.size - photoVotes
        val suggest = when {
            photoVotes > artworkVotes -> InputKind.PHOTO
            artworkVotes > photoVotes -> InputKind.ARTWORK
            else -> null
        }
        return InputKindAssessment(
            chosen = chosen,
            fileInfoVote = fileInfoVote,
            flatNoiseLStd = flatNoise,
            noiseVote = noiseVote,
            backgroundLRange = range,
            rangeVote = rangeVote,
            signalsDisagreeing = disagreeing,
            askUser = disagreeing >= rules.askWhenSignalsDisagreeWithMode,
            signalsSuggest = suggest
        )
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}
