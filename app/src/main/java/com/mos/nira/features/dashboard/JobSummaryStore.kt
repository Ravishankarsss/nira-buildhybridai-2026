package com.mos.nira.features.dashboard

import android.content.Context
import com.mos.nira.features.advice.JobSummary
import org.json.JSONObject

/**
 * Each saved job's decision in one line (method, screens, cost per piece) so Home can show it
 * without opening the job. Saved on this phone only, when the job page works the decision out.
 */
object JobSummaryStore {

    private const val PREFS = "job_summaries"

    fun save(context: Context, recordId: String, s: JobSummary) {
        if (s.method == null) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(recordId, encode(s)).apply()
    }

    fun all(context: Context): Map<String, JobSummary> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.let { decode(it) }?.let { k to it } }.toMap()

    fun remove(context: Context, recordId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(recordId).apply()
    }

    fun encode(s: JobSummary): String = JSONObject()
        .put("method", s.method ?: JSONObject.NULL)
        .put("screens", s.screens ?: JSONObject.NULL)
        .put("per_pc", s.perPc ?: JSONObject.NULL)
        .put("currency", s.currency)
        .put("verified", s.verifiedByRavi)
        .put("sample", s.sampleRates)
        .put("digital", s.digitalBase)
        .toString()

    fun decode(json: String): JobSummary? = runCatching {
        val o = JSONObject(json)
        JobSummary(
            method = if (o.isNull("method")) null else o.getString("method"),
            screens = if (o.isNull("screens")) null else o.getInt("screens"),
            perPc = if (o.isNull("per_pc")) null else o.getDouble("per_pc"),
            currency = o.optString("currency"),
            verifiedByRavi = o.optBoolean("verified"),
            unanswered = 0,
            sampleRates = o.optBoolean("sample"),
            digitalBase = o.optBoolean("digital")
        )
    }.getOrNull()
}
