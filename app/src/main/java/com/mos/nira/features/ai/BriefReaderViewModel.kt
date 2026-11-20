package com.mos.nira.features.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.BriefReader
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BriefReadState {
    data object Idle : BriefReadState
    data object Running : BriefReadState
    data object Blocked : BriefReadState
    /** [reading] is null when the AI reply was not readable JSON. */
    data class Done(val result: HybridInferenceResult, val reading: BriefReader.Reading?) : BriefReadState
}

/**
 * Reads a buyer's brief (pasted text, or a page photo the user chose) with Firebase AI Logic.
 * Text goes on-device first. A page photo leaves the phone only because the user pressed
 * "Read" on a screen that says so; Confidential mode keeps it on the phone (AiPrivacyPolicy).
 */
class BriefReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()
    private val _state = MutableStateFlow<BriefReadState>(BriefReadState.Idle)
    val state: StateFlow<BriefReadState> = _state.asStateFlow()

    init {
        AiSettings.load(application)
    }

    fun reset() {
        if (_state.value !is BriefReadState.Running) _state.value = BriefReadState.Idle
    }

    fun read(text: String?, imageUri: Uri?, colourKeys: List<String>, fabrics: List<String>) {
        if (_state.value is BriefReadState.Running) return
        _state.value = BriefReadState.Running
        viewModelScope.launch {
            val app = getApplication<Application>()
            val template = withContext(Dispatchers.IO) { app.assets.open(BriefReader.ASSET_PATH).bufferedReader().use { it.readText() } }
            val image = imageUri?.let { withContext(Dispatchers.IO) { decodeSmall(it) } }
            val prompt = BriefReader.build(template, if (image != null) "" else text.orEmpty(), colourKeys, fabrics)
            val onDevice = repository.checkOnDeviceStatus() == "available"
            when (val route = AiPrivacyPolicy.decide(
                hasImage = image != null,
                confidentialMode = AiSettings.confidentialMode.value,
                onDeviceAvailable = onDevice,
                imageUploadAllowed = true
            )) {
                is AiRoute.Run -> {
                    val result = repository.generate(prompt, image = image, mode = route.mode)
                    val reading = result.text?.let { BriefReader.parse(it, colourKeys, fabrics, if (image != null) null else text) }
                    AiSettings.record(
                        app, result, imageAttached = image != null, feature = com.mos.nira.core.ai.AiFeature.BRIEF_READER,
                        held = reading == null || reading.fields.any { it.dropped != null }
                    )
                    _state.value = BriefReadState.Done(result, reading)
                }
                else -> _state.value = BriefReadState.Blocked
            }
        }
    }

    /** A page photo, scaled so its long side is at most [MAX_SIDE] px (enough to read, light on memory). */
    private fun decodeSmall(uri: Uri): Bitmap? = runCatching {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return@runCatching null
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale >= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }.getOrNull()

    private companion object {
        const val MAX_SIDE = 1600
    }
}
