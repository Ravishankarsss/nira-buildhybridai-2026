package com.mos.nira.features.artwork.data

import android.content.Context
import com.mos.nira.core.advice.Feel
import com.mos.nira.core.advice.Fibre
import com.mos.nira.features.artwork.domain.NormalizedRect
import com.mos.nira.features.artwork.domain.RegionAnswer
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's answers for one saved job, so reopening the job does not ask again: the background
 * question, the four brief questions, the photo questions, the preview ink swaps and the Print
 * Report settings (print box, erased areas, print width, mesh, strokes, wastage, white as base).
 * Text only, on this phone (app-private preferences). No image.
 */
data class JobAnswers(
    val backgroundPrinted: Boolean? = null,
    val garmentHex: String? = null,
    val fibre: Fibre? = null,
    val fabric: String? = null,
    val quantity: Int? = null,
    val feel: Feel? = null,
    val inkSwaps: Map<String, String> = emptyMap(),
    val regionAnswers: List<RegionAnswer> = emptyList(),
    val smallColourAnswers: List<RegionAnswer> = emptyList(),
    val edgeBlendAnswer: Boolean? = null,
    val allUncertainAreFoldShadows: Boolean = false,
    val keepEdgeColours: Boolean = false,
    val includePhotoEdgeArea: Boolean = false,
    val userGarmentHex: String? = null,
    val useWhiteBaseOverride: Boolean? = null,
    /** Print box set by hand; null = the detected box. */
    val printBox: NormalizedRect? = null,
    val erasedRectangles: List<NormalizedRect> = emptyList(),
    /** Print Report settings; null = not changed (the app's default is used). */
    val printWidthCm: Double? = null,
    val meshTpi: Int? = null,
    val strokes: Int? = null,
    val wastagePercent: Double? = null,
    val whitePrintActsAsBase: Boolean? = null
) {
    /** True when there is nothing to restore. */
    val isEmpty: Boolean get() = this == JobAnswers()
}

object JobAnswersStore {

    private const val PREFS = "job_answers"

    fun save(context: Context, recordId: String, answers: JobAnswers) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (answers.isEmpty) prefs.remove(recordId) else prefs.putString(recordId, encode(answers))
        prefs.apply()
    }

    fun load(context: Context, recordId: String): JobAnswers? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(recordId, null)?.let { decode(it) }

    fun remove(context: Context, recordId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(recordId).apply()
    }

    fun encode(a: JobAnswers): String = JSONObject().apply {
        put("v", 1)
        putOpt("background_printed", a.backgroundPrinted)
        putOpt("garment_hex", a.garmentHex)
        putOpt("fibre", a.fibre?.name)
        putOpt("fabric", a.fabric)
        putOpt("quantity", a.quantity)
        putOpt("feel", a.feel?.name)
        put("ink_swaps", JSONObject().apply { a.inkSwaps.forEach { (k, v) -> put(k, v) } })
        put("region_answers", regions(a.regionAnswers))
        put("small_colour_answers", regions(a.smallColourAnswers))
        putOpt("edge_blend", a.edgeBlendAnswer)
        put("all_fold_shadows", a.allUncertainAreFoldShadows)
        put("keep_edge_colours", a.keepEdgeColours)
        put("include_photo_edge", a.includePhotoEdgeArea)
        putOpt("user_garment_hex", a.userGarmentHex)
        putOpt("white_base_override", a.useWhiteBaseOverride)
        a.printBox?.let { put("print_box", rect(it)) }
        put("erased", JSONArray().apply { a.erasedRectangles.forEach { put(rect(it)) } })
        putOpt("print_width_cm", a.printWidthCm)
        putOpt("mesh_tpi", a.meshTpi)
        putOpt("strokes", a.strokes)
        putOpt("wastage_percent", a.wastagePercent)
        putOpt("white_print_acts_as_base", a.whitePrintActsAsBase)
    }.toString()

    fun decode(json: String): JobAnswers? = runCatching {
        val o = JSONObject(json)
        fun optBool(k: String): Boolean? = if (o.has(k) && !o.isNull(k)) o.getBoolean(k) else null
        fun optStr(k: String): String? = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
        val swaps = o.optJSONObject("ink_swaps")
        JobAnswers(
            backgroundPrinted = optBool("background_printed"),
            garmentHex = optStr("garment_hex"),
            fibre = optStr("fibre")?.let { n -> Fibre.values().firstOrNull { it.name == n } },
            fabric = optStr("fabric"),
            quantity = if (o.has("quantity") && !o.isNull("quantity")) o.getInt("quantity") else null,
            feel = optStr("feel")?.let { n -> Feel.values().firstOrNull { it.name == n } },
            inkSwaps = swaps?.keys()?.asSequence()?.associateWith { swaps.getString(it) } ?: emptyMap(),
            regionAnswers = regions(o.optJSONArray("region_answers")),
            smallColourAnswers = regions(o.optJSONArray("small_colour_answers")),
            edgeBlendAnswer = optBool("edge_blend"),
            allUncertainAreFoldShadows = o.optBoolean("all_fold_shadows", false),
            keepEdgeColours = o.optBoolean("keep_edge_colours", false),
            includePhotoEdgeArea = o.optBoolean("include_photo_edge", false),
            userGarmentHex = optStr("user_garment_hex"),
            useWhiteBaseOverride = optBool("white_base_override"),
            printBox = o.optJSONObject("print_box")?.let { rect(it) },
            erasedRectangles = o.optJSONArray("erased")?.let { arr -> (0 until arr.length()).map { rect(arr.getJSONObject(it)) } } ?: emptyList(),
            printWidthCm = if (o.has("print_width_cm") && !o.isNull("print_width_cm")) o.getDouble("print_width_cm") else null,
            meshTpi = if (o.has("mesh_tpi") && !o.isNull("mesh_tpi")) o.getInt("mesh_tpi") else null,
            strokes = if (o.has("strokes") && !o.isNull("strokes")) o.getInt("strokes") else null,
            wastagePercent = if (o.has("wastage_percent") && !o.isNull("wastage_percent")) o.getDouble("wastage_percent") else null,
            whitePrintActsAsBase = optBool("white_print_acts_as_base")
        )
    }.getOrNull()

    private fun rect(r: NormalizedRect) = JSONObject().put("l", r.left.toDouble()).put("t", r.top.toDouble())
        .put("r", r.right.toDouble()).put("b", r.bottom.toDouble())

    private fun rect(o: JSONObject) = NormalizedRect(
        o.getDouble("l").toFloat(), o.getDouble("t").toFloat(), o.getDouble("r").toFloat(), o.getDouble("b").toFloat()
    )

    private fun regions(list: List<RegionAnswer>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("x", it.normX.toDouble()).put("y", it.normY.toDouble()).put("ink", it.isInk)) }
    }

    private fun regions(arr: JSONArray?): List<RegionAnswer> =
        if (arr == null) emptyList() else (0 until arr.length()).map {
            val r = arr.getJSONObject(it)
            RegionAnswer(r.getDouble("x").toFloat(), r.getDouble("y").toFloat(), r.getBoolean("ink"))
        }
}
