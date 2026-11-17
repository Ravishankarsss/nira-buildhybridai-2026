package com.mos.nira.core.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CancellationException

/** The routing mode asked for. The same four modes as the Firebase AI Logic hybrid SDK. */
enum class HybridMode {
    PREFER_ON_DEVICE,
    PREFER_IN_CLOUD,
    ONLY_ON_DEVICE,
    ONLY_IN_CLOUD
}

/**
 * Result of one AI request.
 *
 * inferenceSource is what the Firebase SDK reported for this response, never a guess from the
 * requested mode: "ON_DEVICE" or "IN_CLOUD". When the request failed it is "UNAVAILABLE"
 * (on-device model not available) or "ERROR", and text is null.
 */
data class HybridInferenceResult(
    val text: String?,
    val inferenceSource: String,
    val durationMs: Long,
    val requestedMode: HybridMode,
    val cloudModelName: String,
    val errorMessage: String? = null
)

/**
 * The only place in the app that calls generative AI. Every request goes through Firebase AI
 * Logic (Gemini Developer API backend); no other AI service or REST API is used.
 *
 * The hybrid part of the Firebase SDK is a public preview, so the opt-in is kept inside this
 * class and the rest of the app only sees [HybridMode] and [HybridInferenceResult].
 */
@OptIn(PublicPreviewAPI::class)
class HybridInferenceRepository {

    /**
     * Sends a text prompt through Firebase AI Logic hybrid inference and returns the text,
     * the real inference source and the duration.
     *
     * The default mode prefers the on-device model and falls back to the cloud model.
     */
    suspend fun generateText(
        prompt: String,
        mode: HybridMode = HybridMode.PREFER_ON_DEVICE
    ): HybridInferenceResult {
        val start = System.nanoTime()
        return try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = CLOUD_MODEL_NAME,
                onDeviceConfig = OnDeviceConfig(mode = sdkMode(mode))
            )
            val response = model.generateContent(prompt)
            HybridInferenceResult(
                text = response.text,
                inferenceSource = sourceName(response.inferenceSource),
                durationMs = elapsedMs(start),
                requestedMode = mode,
                cloudModelName = CLOUD_MODEL_NAME
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAIOnDeviceNotAvailableException) {
            failure("UNAVAILABLE", e, start, mode)
        } catch (e: Exception) {
            failure("ERROR", e, start, mode)
        }
    }

    /**
     * Queries the real Firebase AI on-device SDK for model availability status.
     * Returns "available", "downloadable", "downloading", "unavailable", etc. exactly as reported by the SDK.
     */
    suspend fun checkOnDeviceStatus(): String {
        return try {
            when (FirebaseAIOnDevice.checkStatus()) {
                OnDeviceModelStatus.AVAILABLE -> "available"
                OnDeviceModelStatus.DOWNLOADABLE -> "downloadable"
                OnDeviceModelStatus.DOWNLOADING -> "downloading"
                OnDeviceModelStatus.UNAVAILABLE -> "unavailable"
                else -> "unavailable"
            }
        } catch (e: Exception) {
            "unavailable"
        }
    }

    private fun failure(source: String, e: Exception, start: Long, mode: HybridMode) =
        HybridInferenceResult(
            text = null,
            inferenceSource = source,
            durationMs = elapsedMs(start),
            requestedMode = mode,
            cloudModelName = CLOUD_MODEL_NAME,
            errorMessage = e.message ?: e.javaClass.simpleName
        )

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun sdkMode(mode: HybridMode): InferenceMode = when (mode) {
        HybridMode.PREFER_ON_DEVICE -> InferenceMode.PREFER_ON_DEVICE
        HybridMode.PREFER_IN_CLOUD -> InferenceMode.PREFER_IN_CLOUD
        HybridMode.ONLY_ON_DEVICE -> InferenceMode.ONLY_ON_DEVICE
        HybridMode.ONLY_IN_CLOUD -> InferenceMode.ONLY_IN_CLOUD
    }

    /** Name of the source the SDK reported for a response. */
    private fun sourceName(source: InferenceSource): String = when (source) {
        InferenceSource.ON_DEVICE -> "ON_DEVICE"
        InferenceSource.IN_CLOUD -> "IN_CLOUD"
        else -> source.toString()
    }

    companion object {
        /**
         * Cloud model used when a request runs in the cloud. The SDK has no default cloud model.
         *
         * "gemini-3.5-flash-lite" is a stable model for the Gemini Developer API with no shutdown
         * date listed on https://firebase.google.com/docs/ai-logic/models (checked 16 Nov 2026).
         * Use an explicit stable name, not a "-latest" alias. Check the page again before
         * submission; this is planned to move to Remote Config.
         *
         * Chosen from real runs on the vivo V60e on 17 Nov 2026 (one short text prompt):
         *   gemini-3.5-flash-lite  answered in 2.4 to 8.1 s
         *   gemini-4-hydrogen      answered in 65 s
         *   gemini-3.8-flash       no answer: timed out after 180 s, twice
         */
        const val CLOUD_MODEL_NAME = "gemini-3.5-flash-lite"
    }
}
