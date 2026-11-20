package com.mos.nira.core.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Which NIRA feature made an AI request. */
enum class AiFeature { ASK, ASK_VOICE, EXPLAIN_CARD, COST_DOWN, ARTWORK_READ, BRIEF_READER, REPORT_EXPLAIN, DEEP_REPORT, SHEET_TRANSLATE, OTHER }

/** What NIRA's checks did with the answer. */
enum class AiOutcome { PASSED, HELD, ERROR }

/**
 * One AI request as the Firebase SDK reported it: when, which feature, on-device or cloud, how
 * long, whether an image or a voice clip went with it, and whether NIRA's checks passed or held
 * the answer back. No artwork, prompt or answer text is kept.
 */
data class AiActivity(
    val timeMs: Long,
    val feature: AiFeature,
    /** "ON_DEVICE", "IN_CLOUD", or "ERROR" / "UNAVAILABLE" when the request failed. */
    val source: String,
    val durationMs: Long,
    val image: Boolean,
    val voice: Boolean,
    val outcome: AiOutcome
)

/** The last [MAX] AI requests on this phone, newest first (SharedPreferences "ai_activity"). */
object AiActivityLog {

    const val MAX = 200
    private const val PREFS = "ai_activity"
    private const val KEY = "entries"

    private val _entries = MutableStateFlow<List<AiActivity>>(emptyList())
    val entries: StateFlow<List<AiActivity>> = _entries.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _entries.value = decode(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun add(context: Context, entry: AiActivity) {
        load(context)
        val next = (listOf(entry) + _entries.value).take(MAX)
        _entries.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(next)).apply()
    }

    fun clear(context: Context) {
        _entries.value = emptyList()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Totals for the top of the page. */
    data class Totals(val onDevice: Int, val cloud: Int, val failed: Int, val held: Int, val images: Int, val voice: Int)

    fun totals(list: List<AiActivity>) = Totals(
        onDevice = list.count { it.source == "ON_DEVICE" },
        cloud = list.count { it.source == "IN_CLOUD" },
        failed = list.count { it.outcome == AiOutcome.ERROR },
        held = list.count { it.outcome == AiOutcome.HELD },
        images = list.count { it.image && it.source == "IN_CLOUD" },
        voice = list.count { it.voice && it.source == "IN_CLOUD" }
    )

    fun encode(list: List<AiActivity>): String = JSONArray(list.map {
        JSONObject().put("t", it.timeMs).put("f", it.feature.name).put("s", it.source).put("d", it.durationMs)
            .put("i", it.image).put("v", it.voice).put("o", it.outcome.name)
    }).toString()

    fun decode(json: String?): List<AiActivity> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                AiActivity(
                    o.getLong("t"),
                    runCatching { AiFeature.valueOf(o.getString("f")) }.getOrDefault(AiFeature.OTHER),
                    o.getString("s"), o.optLong("d"), o.optBoolean("i"), o.optBoolean("v"),
                    runCatching { AiOutcome.valueOf(o.getString("o")) }.getOrDefault(AiOutcome.PASSED)
                )
            }.getOrNull()
        }
    }
}
