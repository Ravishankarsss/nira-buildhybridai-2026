package com.mos.nira.features.ai

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.ArtworkRead
import com.mos.nira.core.ai.ArtworkReadPrompt
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface ArtworkAiReadState {
    data object Idle : ArtworkAiReadState
    data object Running : ArtworkAiReadState
    data object AskImageUpload : ArtworkAiReadState
    data object BlockedByConfidentialMode : ArtworkAiReadState
    data object NoImage : ArtworkAiReadState

    /** read is null when the answer had no usable structure; the raw text is then shown. */
    data class Done(val result: HybridInferenceResult, val read: ArtworkRead?) : ArtworkAiReadState
}

/**
 * Runs the AI read of the artwork. The engine numbers are passed in as facts and are never
 * changed by the AI. Privacy rules come from [AiPrivacyPolicy].
 */
class ArtworkAiReadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()

    private val _state = MutableStateFlow<ArtworkAiReadState>(ArtworkAiReadState.Idle)
    val state: StateFlow<ArtworkAiReadState> = _state.asStateFlow()

    private var pending: Triple<Bitmap?, List<String>, String?>? = null

    init {
        AiSettings.load(application)
    }

    fun read(bitmap: Bitmap?, printColourHexes: List<String>, garmentHex: String?) {
        pending = Triple(bitmap, printColourHexes, garmentHex)
        run(imageUploadAllowed = false)
    }

    /** The user agreed to send this image to the cloud once. */
    fun confirmImageUpload() = run(imageUploadAllowed = true)

    fun cancelImageUpload() {
        _state.value = ArtworkAiReadState.Idle
    }

    private fun run(imageUploadAllowed: Boolean) {
        val (bitmap, hexes, garmentHex) = pending ?: return
        if (bitmap == null) {
            _state.value = ArtworkAiReadState.NoImage
            return
        }
        if (_state.value == ArtworkAiReadState.Running) return
        _state.value = ArtworkAiReadState.Running

        viewModelScope.launch {
            val app = getApplication<Application>()
            val onDeviceAvailable = repository.checkOnDeviceStatus() == "available"
            val route = AiPrivacyPolicy.decide(
                hasImage = true,
                confidentialMode = AiSettings.confidentialMode.value,
                onDeviceAvailable = onDeviceAvailable,
                imageUploadAllowed = imageUploadAllowed
            )
            when (route) {
                AiRoute.AskImageUpload -> _state.value = ArtworkAiReadState.AskImageUpload
                AiRoute.BlockedByConfidentialMode -> _state.value = ArtworkAiReadState.BlockedByConfidentialMode
                is AiRoute.Run -> {
                    val prompt = withContext(Dispatchers.IO) {
                        val template = app.assets.open(ArtworkReadPrompt.ASSET_PATH).bufferedReader().use { it.readText() }
                        ArtworkReadPrompt.build(template, hexes, garmentHex)
                    }
                    val image = withContext(Dispatchers.Default) { scaleForAi(bitmap) }
                    val result = repository.generate(prompt, image, route.mode)
                    AiSettings.record(app, result, imageAttached = true)
                    _state.value = ArtworkAiReadState.Done(result, ArtworkReadPrompt.parse(result.text))
                }
            }
        }
    }

    /**
     * A smaller copy of the artwork for the AI: enough to see motifs and fine detail, and less
     * data to send if the user allows the cloud. Measurements never use this copy.
     */
    private fun scaleForAi(bitmap: Bitmap): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= MAX_AI_IMAGE_SIDE) return bitmap
        val factor = MAX_AI_IMAGE_SIDE.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).roundToInt().coerceAtLeast(1),
            (bitmap.height * factor).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        /** Long side in pixels of the image given to the AI. Not an engineering value. */
        const val MAX_AI_IMAGE_SIDE = 768
    }
}
