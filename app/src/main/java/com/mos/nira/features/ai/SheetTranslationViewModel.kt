package com.mos.nira.features.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import com.mos.nira.core.ai.SheetTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface SheetTranslateState {
    data object Idle : SheetTranslateState
    data class Running(val key: String) : SheetTranslateState
    data class Blocked(val key: String) : SheetTranslateState
    /** [result] is null when the reply could not be used at all. */
    data class Done(val key: String, val ai: HybridInferenceResult, val result: SheetTranslation.Result?) : SheetTranslateState
}

/** Translates the sheet's rules-file texts on request (text only; on-device preferred). */
class SheetTranslationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()
    private val _state = MutableStateFlow<SheetTranslateState>(SheetTranslateState.Idle)
    val state: StateFlow<SheetTranslateState> = _state.asStateFlow()

    fun key(texts: List<String>, language: String) = "$language:${texts.hashCode()}"

    init {
        AiSettings.load(application)
    }

    fun translate(texts: List<String>, language: String) {
        if (texts.isEmpty() || _state.value is SheetTranslateState.Running) return
        val key = key(texts, language)
        _state.value = SheetTranslateState.Running(key)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val template = withContext(Dispatchers.IO) { app.assets.open(SheetTranslation.ASSET_PATH).bufferedReader().use { it.readText() } }
            val prompt = SheetTranslation.build(template, texts, Locale(language).getDisplayLanguage(Locale.ENGLISH))
            val onDevice = repository.checkOnDeviceStatus() == "available"
            _state.value = when (val route = AiPrivacyPolicy.decide(
                hasImage = false,
                confidentialMode = AiSettings.confidentialMode.value,
                onDeviceAvailable = onDevice,
                imageUploadAllowed = false
            )) {
                is AiRoute.Run -> {
                    val result = repository.generate(prompt, image = null, mode = route.mode)
                    AiSettings.record(app, result, imageAttached = false)
                    SheetTranslateState.Done(key, result, result.text?.let { SheetTranslation.parse(it, texts) })
                }
                else -> SheetTranslateState.Blocked(key)
            }
        }
    }
}
