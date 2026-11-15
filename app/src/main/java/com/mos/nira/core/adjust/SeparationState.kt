package com.mos.nira.core.adjust

import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.PrintAreaAndPaletteEngine
import com.mos.nira.core.image.SeparationStrategyEngine
import com.mos.nira.features.artwork.data.MeshDepositRule
import com.mos.nira.features.artwork.domain.FilmPositiveChannel
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.PartCResult
import com.mos.nira.features.artwork.domain.PrintJobInputs
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import kotlin.math.exp

enum class ChannelRole { BASE_WHITE, COLOUR, HIGHLIGHT_WHITE }

/**
 * How colour inks are modelled in the proof (print_rules.json, ink_model):
 * TRANSPARENT = absorbance model, darker pixel means more ink (light garment, or over a white base).
 * OPAQUE = pixel = (1 - coverage) x below + coverage x ink, in linear RGB (spot inks on a dark garment).
 * White base and highlight white are always opaque.
 */
enum class InkModel { TRANSPARENT, OPAQUE }

/**
 * One ink screen. coverage holds 0..1 per pixel (1 = 100% ink). Never modified in place:
 * every adjustment builds a new array, so an older state stays exactly as it was.
 */
class InkChannel(
    val name: String,
    val hex: String,
    val coverage: FloatArray,
    val meshTpi: Int,
    val role: ChannelRole = ChannelRole.COLOUR
) {
    fun with(
        name: String = this.name,
        hex: String = this.hex,
        coverage: FloatArray = this.coverage
    ): InkChannel = InkChannel(name, hex, coverage, meshTpi, role)

    fun coverageSum(): Double {
        var sum = 0.0
        for (c in coverage) sum += c
        return sum
    }

    fun contentEquals(other: InkChannel): Boolean =
        name == other.name && hex == other.hex && meshTpi == other.meshTpi &&
            role == other.role && coverage.contentEquals(other.coverage)
}

/**
 * The separation being adjusted: source image, print mask, ink channels and job geometry.
 * Immutable. Adjustment operations return a new state.
 */
class SeparationState(
    val width: Int,
    val height: Int,
    val sourcePixels: IntArray,
    val printMask: BooleanArray,
    val channels: List<InkChannel>,
    val garmentHex: String,
    val inkModel: InkModel,
    val pixelsPerCm: Double,
    val boxAreaCm2: Double,
    val boxPixelCount: Int,
    val garmentThresholdDeltaE00: Double,
    val colourways: Map<String, Map<String, String>> = emptyMap()
) {
    init {
        require(sourcePixels.size == width * height) { "source pixels do not match width x height" }
        require(printMask.size == width * height) { "print mask does not match width x height" }
        require(channels.all { it.coverage.size == width * height }) { "a channel does not match width x height" }
    }

    fun copy(
        printMask: BooleanArray = this.printMask,
        channels: List<InkChannel> = this.channels,
        colourways: Map<String, Map<String, String>> = this.colourways
    ): SeparationState = SeparationState(
        width, height, sourcePixels, printMask, channels, garmentHex, inkModel,
        pixelsPerCm, boxAreaCm2, boxPixelCount, garmentThresholdDeltaE00, colourways
    )

    fun channel(name: String): InkChannel? = channels.firstOrNull { it.name == name }

    /** Replaces one channel, keeping screen order. */
    fun withChannel(updated: InkChannel, replacing: String = updated.name): SeparationState =
        copy(channels = channels.map { if (it.name == replacing) updated else it })

    fun contentEquals(other: SeparationState): Boolean =
        width == other.width && height == other.height &&
            sourcePixels.contentEquals(other.sourcePixels) &&
            printMask.contentEquals(other.printMask) &&
            garmentHex == other.garmentHex && inkModel == other.inkModel &&
            pixelsPerCm == other.pixelsPerCm && boxAreaCm2 == other.boxAreaCm2 &&
            boxPixelCount == other.boxPixelCount &&
            garmentThresholdDeltaE00 == other.garmentThresholdDeltaE00 &&
            colourways == other.colourways &&
            channels.size == other.channels.size &&
            channels.indices.all { channels[it].contentEquals(other.channels[it]) }

    companion object {
        /**
         * Builds the starting state from a finished Print Report (Parts A to C).
         * Returns null if the report has no channel pixel data.
         */
        fun fromReport(
            sourcePixels: IntArray,
            width: Int,
            height: Int,
            partA: PartAResult,
            partB: PartBResult,
            partC: PartCResult,
            inputs: PrintJobInputs
        ): SeparationState? {
            if (sourcePixels.size != width * height || partC.channels.isEmpty()) return null
            val channels = partC.channels.map { ch ->
                val grey = ch.rawGreyscalePixels ?: return null
                if (grey.size != width * height) return null
                InkChannel(
                    name = ch.inkName,
                    hex = ch.inkHex,
                    // Film positive: grey 0 = 100% ink, grey 255 = 0% ink.
                    coverage = FloatArray(grey.size) { 1f - (grey[it] and 0xFF) / 255f },
                    meshTpi = ch.recommendedMesh.filter { it.isDigit() }.toIntOrNull() ?: inputs.meshTpi,
                    role = when {
                        ch.inkName.contains("Base White") -> ChannelRole.BASE_WHITE
                        ch.inkName.contains("Highlight White") -> ChannelRole.HIGHLIGHT_WHITE
                        else -> ChannelRole.COLOUR
                    }
                )
            }
            val spotOnDark = partB.isDarkGarment && partC.strategyType != SeparationStrategyType.CMYK_PROCESS
            return SeparationState(
                width = width,
                height = height,
                sourcePixels = sourcePixels,
                printMask = PrintAreaAndPaletteEngine.computePrintMask(sourcePixels, width, height, inputs),
                channels = channels,
                garmentHex = inputs.userGarmentHex ?: inputs.garmentColorHex,
                inkModel = if (spotOnDark) InkModel.OPAQUE else InkModel.TRANSPARENT,
                pixelsPerCm = if (partA.pixelsPerCm > 0) partA.pixelsPerCm else width / inputs.printWidthCm,
                boxAreaCm2 = partA.totalBoxAreaCm2,
                boxPixelCount = partA.totalPixelCount,
                garmentThresholdDeltaE00 = inputs.garmentThresholdDeltaE00
            )
        }
    }
}

/**
 * Numbers recalculated after every accepted change.
 */
class SeparationMetrics(
    val screens: Int,
    val meanDeltaE00: Double,
    val percentile95DeltaE00: Double,
    val inkGramsPerGarment: Double,
    /** Ink name -> average coverage over the print box, in percent. */
    val channelCoveragePercent: Map<String, Double>,
    /** Ink name -> grams per garment. */
    val channelInkGrams: Map<String, Double>,
    /** Ink name -> HEX. */
    val channelHex: Map<String, String>
)

/**
 * Deterministic proof and metrics for a [SeparationState]. No AI.
 */
object SeparationProof {

    private const val MAX_ERROR_SAMPLES = 20000

    /** Renders the model proof: garment where there is no ink, inks composited in screen order. */
    fun render(state: SeparationState): IntArray {
        val garment = ColorConverter.hexToRgb(state.garmentHex)
        val gLin = doubleArrayOf(
            ColorConverter.sRgbToLinear(garment.r),
            ColorConverter.sRgbToLinear(garment.g),
            ColorConverter.sRgbToLinear(garment.b)
        )
        val inkLin = state.channels.map { ch ->
            val rgb = ColorConverter.hexToRgb(ch.hex)
            doubleArrayOf(
                ColorConverter.sRgbToLinear(rgb.r),
                ColorConverter.sRgbToLinear(rgb.g),
                ColorConverter.sRgbToLinear(rgb.b)
            )
        }
        val inkAbsorbance = state.channels.map { SeparationStrategyEngine.hexToAbsorbance(it.hex) }
        val out = IntArray(state.width * state.height)
        val lin = DoubleArray(3)

        for (i in out.indices) {
            lin[0] = gLin[0]; lin[1] = gLin[1]; lin[2] = gLin[2]
            for (k in state.channels.indices) {
                val ch = state.channels[k]
                val c = ch.coverage[i].toDouble()
                if (c <= 0.0) continue
                val opaque = ch.role != ChannelRole.COLOUR || state.inkModel == InkModel.OPAQUE
                if (opaque) {
                    for (j in 0..2) lin[j] = (1 - c) * lin[j] + c * inkLin[k][j]
                } else {
                    for (j in 0..2) lin[j] *= exp(-c * inkAbsorbance[k][j])
                }
            }
            out[i] = (0xFF shl 24) or
                (ColorConverter.linearToSRgb(lin[0].coerceIn(0.0, 1.0)) shl 16) or
                (ColorConverter.linearToSRgb(lin[1].coerceIn(0.0, 1.0)) shl 8) or
                ColorConverter.linearToSRgb(lin[2].coerceIn(0.0, 1.0))
        }
        return out
    }

    /**
     * Recalculates screens, colour error (proof against source over the print mask) and ink
     * quantity. Ink quantity uses the same Part D formula as the Print Report.
     */
    fun metrics(
        state: SeparationState,
        inputs: PrintJobInputs,
        depositRules: Map<Int, MeshDepositRule>,
        proof: IntArray = render(state)
    ): SeparationMetrics {
        // Colour error over the print mask, on an evenly spaced sample for large images.
        val maskCount = state.printMask.count { it }
        val step = maxOf(1, maskCount / MAX_ERROR_SAMPLES)
        val errors = ArrayList<Double>(minOf(maskCount, MAX_ERROR_SAMPLES) + 1)
        var seen = 0
        for (i in state.printMask.indices) {
            if (!state.printMask[i]) continue
            if (seen % step == 0) {
                val s = state.sourcePixels[i]
                val p = proof[i]
                errors.add(
                    ColorConverter.colorDistanceE00(
                        (s shr 16) and 0xFF, (s shr 8) and 0xFF, s and 0xFF,
                        (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF
                    )
                )
            }
            seen++
        }
        errors.sort()
        val mean = if (errors.isEmpty()) 0.0 else errors.sum() / errors.size
        val p95 = if (errors.isEmpty()) 0.0 else errors[((errors.size - 1) * 0.95).toInt()]

        val boxPixels = maxOf(1, state.boxPixelCount)
        val filmChannels = state.channels.mapIndexed { index, ch ->
            val boxCoverage = ch.coverageSum() / boxPixels * 100.0
            FilmPositiveChannel(
                channelIndex = index,
                inkName = ch.name,
                inkHex = ch.hex,
                inkRgb = ColorConverter.hexToRgb(ch.hex),
                averageCoveragePercent = boxCoverage,
                boxCoveragePercent = boxCoverage,
                recommendedMesh = "${ch.meshTpi} tpi"
            )
        }
        val partD = SeparationStrategyEngine.calculatePartD(
            partA = PartAResult(
                printWidthCm = inputs.printWidthCm,
                printHeightCm = if (inputs.printWidthCm > 0) state.boxAreaCm2 / inputs.printWidthCm else 0.0,
                totalBoxAreaCm2 = state.boxAreaCm2,
                effectivePrintAreaCm2 = state.boxAreaCm2 * maskCount / boxPixels,
                printPixelCount = maskCount,
                totalPixelCount = state.boxPixelCount,
                printMaskCoveragePercent = maskCount * 100.0 / boxPixels,
                measuredPalette = emptyList(),
                errorCurve = emptyList()
            ),
            partC = PartCResult(
                strategyType = SeparationStrategyType.BEST_N_SPOT_HALFTONE,
                strategyName = "Adjusted separation",
                channels = filmChannels
            ),
            inputs = inputs,
            depositRules = depositRules
        )

        return SeparationMetrics(
            screens = state.channels.size,
            meanDeltaE00 = mean,
            percentile95DeltaE00 = p95,
            inkGramsPerGarment = partD.totalGramsPerGarment,
            channelCoveragePercent = filmChannels.associate { it.inkName to it.boxCoveragePercent },
            channelInkGrams = partD.channels.associate { it.inkName to it.inkGramsPerGarment },
            channelHex = state.channels.associate { it.name to it.hex }
        )
    }
}
