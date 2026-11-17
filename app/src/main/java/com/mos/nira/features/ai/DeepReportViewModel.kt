package com.mos.nira.features.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.DeepReportPrompt
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import com.mos.nira.core.ai.HybridMode
import com.mos.nira.core.ai.ReportLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DeepReportState {
    data object Idle : DeepReportState
    data object Running : DeepReportState
    data object BlockedByConfidentialMode : DeepReportState
    data class Done(val key: ArtworkKey?, val language: ReportLanguage, val result: HybridInferenceResult) : DeepReportState
}

/**
 * Deep report from the cloud model (master context, layer C): called only when the user asks,
 * with text and numbers only. Confidential mode blocks it.
 */
class DeepReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()

    private val _state = MutableStateFlow<DeepReportState>(DeepReportState.Idle)
    val state: StateFlow<DeepReportState> = _state.asStateFlow()

    init {
        AiSettings.load(application)
    }

    fun write(key: ArtworkKey?, facts: String, language: ReportLanguage) {
        if (_state.value == DeepReportState.Running) return
        if (AiSettings.confidentialMode.value) {
            _state.value = DeepReportState.BlockedByConfidentialMode
            return
        }
        _state.value = DeepReportState.Running
        viewModelScope.launch {
            val app = getApplication<Application>()
            val prompt = withContext(Dispatchers.IO) {
                val template = app.assets.open(DeepReportPrompt.ASSET_PATH).bufferedReader().use { it.readText() }
                DeepReportPrompt.build(template, facts, language)
            }
            // Text only: no image is attached to this request.
            val result = repository.generate(prompt, image = null, mode = HybridMode.ONLY_IN_CLOUD)
            AiSettings.record(app, result, imageAttached = false)
            _state.value = DeepReportState.Done(key, language, result)
        }
    }
}
