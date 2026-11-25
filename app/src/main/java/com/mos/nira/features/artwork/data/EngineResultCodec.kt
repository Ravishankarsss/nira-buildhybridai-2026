package com.mos.nira.features.artwork.data

import com.mos.nira.core.image.ColorConverter
import com.mos.nira.core.image.InputKind
import com.mos.nira.core.image.InputKindAssessment
import com.mos.nira.features.artwork.domain.ArtworkBackground
import com.mos.nira.features.artwork.domain.ErrorCurvePoint
import com.mos.nira.features.artwork.domain.GarmentProfile
import com.mos.nira.features.artwork.domain.MeasuredColor
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.PartAResult
import com.mos.nira.features.artwork.domain.PartBResult
import com.mos.nira.features.artwork.domain.SeparationStrategyType
import com.mos.nira.features.artwork.domain.ShadeEntry
import com.mos.nira.features.artwork.domain.StrategyEvaluationResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Text form of the engine's Part A and Part B results, so a saved job reopens without measuring
 * again. Numbers only; the two pictures (mask picture, print mask) are stored next to it by
 * [EngineDiskCache]. Pure JSON, tested on the laptop.
 */
object EngineResultCodec {

    const val VERSION = 1

    fun encodePartA(a: PartAResult): JSONObject = JSONObject().apply {
        put("printWidthCm", a.printWidthCm); put("printHeightCm", a.printHeightCm)
        put("totalBoxAreaCm2", a.totalBoxAreaCm2); put("effectivePrintAreaCm2", a.effectivePrintAreaCm2)
        put("printPixelCount", a.printPixelCount); put("totalPixelCount", a.totalPixelCount)
        put("printMaskCoveragePercent", a.printMaskCoveragePercent)
        put("palette", JSONArray().apply { a.measuredPalette.forEach { put(colour(it)) } })
        put("errorCurve", JSONArray().apply { a.errorCurve.forEach { put(JSONObject().put("k", it.k).put("e", it.meanDeltaE00)) } })
        a.garmentProfile?.let { g ->
            put("garment", JSONObject().put("hex", g.estimatedHex).put("lab", lab(g.medianLab)).put("spread", g.spreadDeltaE00)
                .put("threshold", g.adaptiveThresholdDeltaE00).put("user", g.isUserOverridden))
        }
        put("printBox", rect(a.printBox))
        put("pixelsPerCm", a.pixelsPerCm)
        putOpt("lowResolutionWarning", a.lowResolutionWarning)
        put("minPxPerCm", a.minPxPerCm)
        a.inputKindAssessment?.let { k ->
            put("inputKind", JSONObject().put("chosen", k.chosen.name).putOpt("fileInfoVote", k.fileInfoVote?.name)
                .putOpt("flatNoiseLStd", k.flatNoiseLStd).putOpt("noiseVote", k.noiseVote?.name)
                .putOpt("backgroundLRange", k.backgroundLRange).putOpt("rangeVote", k.rangeVote?.name)
                .put("disagreeing", k.signalsDisagreeing).put("ask", k.askUser).putOpt("suggest", k.signalsSuggest?.name))
        }
        put("foldShadowsRemoved", a.foldShadowsRemoved); put("shadowTestLowResolution", a.shadowTestLowResolution)
        put("edgeBlendPixelsMerged", a.edgeBlendPixelsMerged); putOpt("edgeBlendBandMaxPx", a.edgeBlendBandMaxPx)
        put("speckPixelsRemoved", a.speckPixelsRemoved); put("photoEdgeExcludedPercent", a.photoEdgeExcludedPercent)
        a.artworkBackground?.let { b ->
            put("background", JSONObject().put("hex", b.hex).put("border", b.borderSharePercent).put("image", b.imageCoveragePercent).put("printed", b.printed))
        }
        put("maskWidth", a.maskWidth); put("maskHeight", a.maskHeight)
    }

    /** [printMask] and [maskBitmap] are restored separately (they are pictures, not text). */
    fun decodePartA(o: JSONObject, printMask: BooleanArray?, maskBitmap: android.graphics.Bitmap?): PartAResult = PartAResult(
        printWidthCm = o.getDouble("printWidthCm"), printHeightCm = o.getDouble("printHeightCm"),
        totalBoxAreaCm2 = o.getDouble("totalBoxAreaCm2"), effectivePrintAreaCm2 = o.getDouble("effectivePrintAreaCm2"),
        printPixelCount = o.getInt("printPixelCount"), totalPixelCount = o.getInt("totalPixelCount"),
        printMaskCoveragePercent = o.getDouble("printMaskCoveragePercent"),
        measuredPalette = o.getJSONArray("palette").let { arr -> (0 until arr.length()).map { colour(arr.getJSONObject(it)) } },
        errorCurve = o.getJSONArray("errorCurve").let { arr -> (0 until arr.length()).map { ErrorCurvePoint(arr.getJSONObject(it).getInt("k"), arr.getJSONObject(it).getDouble("e")) } },
        maskBitmap = maskBitmap,
        garmentProfile = o.optJSONObject("garment")?.let { g ->
            GarmentProfile(g.getString("hex"), lab(g.getJSONArray("lab")), g.getDouble("spread"), g.getDouble("threshold"), g.getBoolean("user"))
        },
        printBox = rect(o.getJSONArray("printBox")),
        pixelsPerCm = o.getDouble("pixelsPerCm"),
        lowResolutionWarning = str(o, "lowResolutionWarning"),
        minPxPerCm = o.getDouble("minPxPerCm"),
        inputKindAssessment = o.optJSONObject("inputKind")?.let { k ->
            InputKindAssessment(
                chosen = InputKind.valueOf(k.getString("chosen")),
                fileInfoVote = str(k, "fileInfoVote")?.let { InputKind.valueOf(it) },
                flatNoiseLStd = dbl(k, "flatNoiseLStd"), noiseVote = str(k, "noiseVote")?.let { InputKind.valueOf(it) },
                backgroundLRange = dbl(k, "backgroundLRange"), rangeVote = str(k, "rangeVote")?.let { InputKind.valueOf(it) },
                signalsDisagreeing = k.getInt("disagreeing"), askUser = k.getBoolean("ask"),
                signalsSuggest = str(k, "suggest")?.let { InputKind.valueOf(it) }
            )
        },
        foldShadowsRemoved = o.getInt("foldShadowsRemoved"), shadowTestLowResolution = o.getBoolean("shadowTestLowResolution"),
        edgeBlendPixelsMerged = o.getInt("edgeBlendPixelsMerged"), edgeBlendBandMaxPx = dbl(o, "edgeBlendBandMaxPx"),
        speckPixelsRemoved = o.getInt("speckPixelsRemoved"), photoEdgeExcludedPercent = o.getDouble("photoEdgeExcludedPercent"),
        artworkBackground = o.optJSONObject("background")?.let { b ->
            ArtworkBackground(b.getString("hex"), b.getDouble("border"), b.getDouble("image"), b.getBoolean("printed"))
        },
        printMask = printMask,
        maskWidth = o.getInt("maskWidth"), maskHeight = o.getInt("maskHeight")
    )

    /** Part B without the per-strategy proof pictures (no screen shows them; Part C makes the proof). */
    fun encodePartB(b: PartBResult): JSONObject = JSONObject().apply {
        put("strategies", JSONArray().apply { b.strategies.forEach { put(strategy(it)) } })
        put("selected", b.selectedStrategyType.name)
        put("inks", JSONObject().put("c", b.processInks.cyanHex).put("m", b.processInks.magentaHex).put("y", b.processInks.yellowHex)
            .put("k", b.processInks.blackHex).put("confidence", b.processInks.confidence).put("note", b.processInks.note))
        put("dark", b.isDarkGarment); put("ms", b.executionTimeMs); put("disclaimer", b.disclaimer); put("opaque", b.opaqueInkModel)
    }

    fun decodePartB(o: JSONObject): PartBResult {
        val i = o.getJSONObject("inks")
        return PartBResult(
            strategies = o.getJSONArray("strategies").let { arr -> (0 until arr.length()).map { strategy(arr.getJSONObject(it)) } },
            selectedStrategyType = SeparationStrategyType.valueOf(o.getString("selected")),
            processInks = ProcessInksConfig(i.getString("c"), i.getString("m"), i.getString("y"), i.getString("k"), i.getString("confidence"), i.getString("note")),
            isDarkGarment = o.getBoolean("dark"), executionTimeMs = o.getLong("ms"),
            disclaimer = o.getString("disclaimer"), opaqueInkModel = o.getBoolean("opaque")
        )
    }

    /** Print mask as bits (8 pixels a byte). */
    fun packMask(mask: BooleanArray): ByteArray {
        val out = ByteArray((mask.size + 7) / 8)
        for (i in mask.indices) if (mask[i]) out[i shr 3] = (out[i shr 3].toInt() or (1 shl (i and 7))).toByte()
        return out
    }

    fun unpackMask(bytes: ByteArray, size: Int): BooleanArray =
        BooleanArray(size) { i -> (bytes[i shr 3].toInt() shr (i and 7)) and 1 == 1 }

    private fun strategy(s: StrategyEvaluationResult) = JSONObject().apply {
        put("type", s.strategyType.name); put("name", s.name); put("screens", s.screenCount)
        put("hexes", JSONArray(s.inkHexes)); put("names", JSONArray(s.inkNames))
        put("mean", s.meanDeltaE00); put("p95", s.percentile95DeltaE00); put("within3", s.percentWithinDeltaE3)
        putOpt("darkWarning", s.darkGarmentWarning); put("dark", s.isDarkGarment); put("recommended", s.isRecommended)
        put("reason", s.recommendationReason); putOpt("transition", s.transitionNote); put("notes", s.notes)
        put("spots", JSONArray(s.spotHexes)); put("blackFromK", JSONArray(s.blackFromKHexes)); putOpt("solidCore", s.solidCoreDeltaE00)
    }

    private fun strategy(o: JSONObject) = StrategyEvaluationResult(
        strategyType = SeparationStrategyType.valueOf(o.getString("type")), name = o.getString("name"), screenCount = o.getInt("screens"),
        inkHexes = strings(o.getJSONArray("hexes")), inkNames = strings(o.getJSONArray("names")),
        meanDeltaE00 = o.getDouble("mean"), percentile95DeltaE00 = o.getDouble("p95"), percentWithinDeltaE3 = o.getDouble("within3"),
        darkGarmentWarning = str(o, "darkWarning"), isDarkGarment = o.getBoolean("dark"), isRecommended = o.getBoolean("recommended"),
        recommendationReason = o.getString("reason"), transitionNote = str(o, "transition"), notes = o.getString("notes"),
        proofBitmap = null,
        spotHexes = strings(o.getJSONArray("spots")), blackFromKHexes = strings(o.getJSONArray("blackFromK")), solidCoreDeltaE00 = dbl(o, "solidCore")
    )

    private fun colour(c: MeasuredColor) = JSONObject().apply {
        put("id", c.id); put("hex", c.hex); put("rgb", JSONArray(listOf(c.rgb.r, c.rgb.g, c.rgb.b))); put("lab", lab(c.lab))
        put("px", c.pixelCount); put("cov", c.coveragePercent); put("boxCov", c.totalBoxCoveragePercent); put("area", c.areaCm2)
        c.nearestShade?.let { s ->
            put("shade", JSONObject().put("type", s.type).put("code", s.code).put("name", s.name).put("l", s.labL).put("a", s.labA).put("b", s.labB)
                .put("lib", s.libraryName).put("user", s.isUserCreated))
        }
        putOpt("shadeDe", c.nearestShadeDeltaE00); putOpt("grade", c.matchGrade)
        put("fold", c.possibleFoldShadow)
        put("points", JSONArray().apply { c.uncertainRegionPoints.forEach { put(JSONArray(listOf(it.first.toDouble(), it.second.toDouble()))) } })
        put("blend", c.possibleEdgeBlend); put("small", c.possibleSmallColour)
        c.smallColourPoint?.let { put("smallPoint", JSONArray(listOf(it.first.toDouble(), it.second.toDouble()))) }
    }

    private fun colour(o: JSONObject): MeasuredColor {
        val rgb = o.getJSONArray("rgb")
        return MeasuredColor(
            id = o.getInt("id"), hex = o.getString("hex"), rgb = ColorConverter.Rgb(rgb.getInt(0), rgb.getInt(1), rgb.getInt(2)),
            lab = lab(o.getJSONArray("lab")), pixelCount = o.getInt("px"), coveragePercent = o.getDouble("cov"),
            totalBoxCoveragePercent = o.getDouble("boxCov"), areaCm2 = o.getDouble("area"),
            nearestShade = o.optJSONObject("shade")?.let { s ->
                ShadeEntry(s.getString("type"), s.getString("code"), s.getString("name"), s.getDouble("l"), s.getDouble("a"), s.getDouble("b"),
                    s.getString("lib"), s.getBoolean("user"))
            },
            nearestShadeDeltaE00 = dbl(o, "shadeDe"), matchGrade = str(o, "grade"),
            possibleFoldShadow = o.getBoolean("fold"),
            uncertainRegionPoints = o.getJSONArray("points").let { arr -> (0 until arr.length()).map { p -> arr.getJSONArray(p).let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() } } },
            possibleEdgeBlend = o.getBoolean("blend"), possibleSmallColour = o.getBoolean("small"),
            smallColourPoint = o.optJSONArray("smallPoint")?.let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() }
        )
    }

    private fun lab(l: ColorConverter.Lab) = JSONArray(listOf(l.l, l.a, l.b))
    private fun lab(a: JSONArray) = ColorConverter.Lab(a.getDouble(0), a.getDouble(1), a.getDouble(2))
    private fun rect(r: NormalizedRect) = JSONArray(listOf(r.left.toDouble(), r.top.toDouble(), r.right.toDouble(), r.bottom.toDouble()))
    private fun rect(a: JSONArray) = NormalizedRect(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun str(o: JSONObject, k: String): String? = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
    private fun dbl(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null
}
