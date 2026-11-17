package com.mos.nira.core.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts of real AI answers on this phone. Only what the SDK reported is counted. */
data class AiUsageCounts(
    val answersOnDevice: Int = 0,
    val answersInCloud: Int = 0,
    val imagesUploaded: Int = 0
)

/**
 * Confidential mode and the AI usage counters, kept in app-private SharedPreferences.
 * Nothing here contains artwork or answer text.
 */
object AiSettings {

    private const val PREFS = "nira_ai_prefs"
    private const val KEY_CONFIDENTIAL = "confidential_mode"
    private const val KEY_ON_DEVICE = "answers_on_device"
    private const val KEY_IN_CLOUD = "answers_in_cloud"
    private const val KEY_IMAGES = "images_uploaded"

    private val _confidentialMode = MutableStateFlow(false)
    val confidentialMode: StateFlow<Boolean> = _confidentialMode.asStateFlow()

    private val _counts = MutableStateFlow(AiUsageCounts())
    val counts: StateFlow<AiUsageCounts> = _counts.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        _confidentialMode.value = p.getBoolean(KEY_CONFIDENTIAL, false)
        _counts.value = AiUsageCounts(
            answersOnDevice = p.getInt(KEY_ON_DEVICE, 0),
            answersInCloud = p.getInt(KEY_IN_CLOUD, 0),
            imagesUploaded = p.getInt(KEY_IMAGES, 0)
        )
    }

    fun setConfidentialMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONFIDENTIAL, enabled).apply()
        _confidentialMode.value = enabled
    }

    /**
     * Records one answer. An image counts as uploaded only when it was attached and the SDK
     * reported that the answer came from the cloud.
     */
    fun record(context: Context, result: HybridInferenceResult, imageAttached: Boolean) {
        val now = _counts.value
        val updated = when (result.inferenceSource) {
            "ON_DEVICE" -> now.copy(answersOnDevice = now.answersOnDevice + 1)
            "IN_CLOUD" -> now.copy(
                answersInCloud = now.answersInCloud + 1,
                imagesUploaded = now.imagesUploaded + if (imageAttached) 1 else 0
            )
            else -> return
        }
        prefs(context).edit()
            .putInt(KEY_ON_DEVICE, updated.answersOnDevice)
            .putInt(KEY_IN_CLOUD, updated.answersInCloud)
            .putInt(KEY_IMAGES, updated.imagesUploaded)
            .apply()
        _counts.value = updated
    }
}
