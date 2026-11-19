package com.mos.nira.features.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.CardExplanation
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.ReportExplanation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Explain this card": runs only when the user taps the button. Text only (the card's facts),
 * never the image. Routing follows AiPrivacyPolicy: on-device preferred, Confidential mode blocks
 * the cloud. The facts and prompt are saved to the app's own storage for the web sample.
 */
class CardExplanationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()

    private val _state = MutableStateFlow<ReportExplanationState>(ReportExplanationState.Idle)
    val state: StateFlow<ReportExplanationState> = _state.asStateFlow()

    init {
        AiSettings.load(application)
    }

    /** Forget an answer that belongs to an older card. */
    fun resetIfChanged(requestKey: String) {
        val key = when (val s = _state.value) {
            is ReportExplanationState.Running -> return
            is ReportExplanationState.Blocked -> s.requestKey
            is ReportExplanationState.Done -> s.requestKey
            ReportExplanationState.Idle -> return
        }
        if (key != requestKey) _state.value = ReportExplanationState.Idle
    }

    fun explain(requestKey: String, facts: List<String>, methodName: String?, assetPath: String = CardExplanation.ASSET_PATH) {
        if (_state.value is ReportExplanationState.Running) return
        _state.value = ReportExplanationState.Running(requestKey)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val template = withContext(Dispatchers.IO) {
                app.assets.open(assetPath).bufferedReader().use { it.readText() }
            }
            val prompt = CardExplanation.build(template, facts)
            // Only the card explanation is saved for the web sample.
            if (assetPath == CardExplanation.ASSET_PATH) withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                    File(app.filesDir, CardExplanation.EXPORT_FILE).writeText(CardExplanation.exportJson(facts, prompt, stamp, methodName))
                }
            }
            val onDevice = repository.checkOnDeviceStatus() == "available"
            when (val route = AiPrivacyPolicy.decide(
                hasImage = false,
                confidentialMode = AiSettings.confidentialMode.value,
                onDeviceAvailable = onDevice,
                imageUploadAllowed = false
            )) {
                is AiRoute.Run -> {
                    val result = repository.generate(prompt, image = null, mode = route.mode)
                    AiSettings.record(app, result, imageAttached = false)
                    val text = result.text
                    val unverified = text?.let { ReportExplanation.unverifiedNumbers(it, facts) } ?: emptyList()
                    val missing = text?.let { CardExplanation.missingMethodWords(it, methodName) } ?: emptyList()
                    val code = text?.let { CardExplanation.hasColourCode(it) } ?: false
                    _state.value = ReportExplanationState.Done(requestKey, facts, result, unverified, missing, code)
                }
                else -> _state.value = ReportExplanationState.Blocked(requestKey, facts)
            }
        }
    }
}
