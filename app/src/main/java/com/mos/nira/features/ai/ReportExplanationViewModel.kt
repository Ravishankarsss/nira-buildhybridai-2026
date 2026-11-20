package com.mos.nira.features.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import com.mos.nira.core.ai.ReportExplanation
import com.mos.nira.features.artwork.domain.PartBResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface ReportExplanationState {
    data object Idle : ReportExplanationState
    data class Running(val requestKey: String) : ReportExplanationState
    data class Blocked(val requestKey: String, val facts: List<String>) : ReportExplanationState
    data class Done(
        val requestKey: String,
        val facts: List<String>,
        val result: HybridInferenceResult,
        /** Numbers in the answer that are not in the facts; when not empty the text is not shown. */
        val unverified: List<Double>,
        /** Card explanation only: method words the answer did not use; when not empty the text is not shown. */
        val missingMethodWords: List<String> = emptyList(),
        /** Card explanation only: the answer has a colour code; the text is not shown. */
        val colourCode: Boolean = false
    ) : ReportExplanationState
}

/**
 * The AI explanation in the Print Report: after the engine runs, the model gets the engine result
 * and the matching rules (text only, never the image) and writes 2-3 plain sentences. Routing
 * follows AiPrivacyPolicy: on-device preferred, Confidential mode blocks the cloud.
 */
class ReportExplanationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()

    private val _state = MutableStateFlow<ReportExplanationState>(ReportExplanationState.Idle)
    val state: StateFlow<ReportExplanationState> = _state.asStateFlow()

    init {
        AiSettings.load(application)
    }

    fun explain(requestKey: String, engineFacts: List<String>, partB: PartBResult) {
        val current = _state.value
        val sameRequest = when (current) {
            is ReportExplanationState.Running -> current.requestKey == requestKey
            is ReportExplanationState.Blocked -> current.requestKey == requestKey
            is ReportExplanationState.Done -> current.requestKey == requestKey
            ReportExplanationState.Idle -> false
        }
        if (sameRequest || current is ReportExplanationState.Running) return
        _state.value = ReportExplanationState.Running(requestKey)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val (template, rules) = withContext(Dispatchers.IO) {
                val t = app.assets.open(ReportExplanation.ASSET_PATH).bufferedReader().use { it.readText() }
                val json = JSONObject(app.assets.open("print_rules.json").bufferedReader().use { it.readText() })
                t to ruleFacts(json, partB.isDarkGarment)
            }
            val facts = engineFacts + rules
            val onDevice = repository.checkOnDeviceStatus() == "available"
            when (val route = AiPrivacyPolicy.decide(
                hasImage = false,
                confidentialMode = AiSettings.confidentialMode.value,
                onDeviceAvailable = onDevice,
                imageUploadAllowed = false
            )) {
                is AiRoute.Run -> {
                    // Text only: no image is attached to this request.
                    val result = repository.generate(ReportExplanation.build(template, facts), image = null, mode = route.mode)
                    val unverified = result.text?.let { ReportExplanation.unverifiedNumbers(it, facts) } ?: emptyList()
                    AiSettings.record(app, result, imageAttached = false, feature = com.mos.nira.core.ai.AiFeature.REPORT_EXPLAIN, held = unverified.isNotEmpty())
                    _state.value = ReportExplanationState.Done(requestKey, facts, result, unverified)
                }
                else -> _state.value = ReportExplanationState.Blocked(requestKey, facts)
            }
        }
    }

    /** The rules from print_rules.json that the recommendation used, as plain lines. */
    private fun ruleFacts(json: JSONObject, darkGarment: Boolean): List<String> = buildList {
        json.optJSONObject("recommendation")?.let { r ->
            add("Rule: ${r.optString("principle")}")
            add("Rule: quality tolerance mean dE00 ${r.optDouble("quality_tolerance_mean_de00")}, 95% dE00 ${r.optDouble("quality_tolerance_p95_de00")}.")
            r.optString("gradient_rule").takeIf { it.isNotBlank() }?.let { add("Rule: $it") }
        }
        if (darkGarment) {
            json.optJSONObject("white_base")?.let { w ->
                add("Rule: a white base is needed when ${w.optString("needed_when")}.")
                add("Rule: the white base goes ${w.optString("area")}.")
            }
            json.optJSONObject("underbase_rules")?.let { u ->
                add("Rule: the white base is choked by ${u.optDouble("choke_mm_default")} mm.")
            }
        }
    }
}
