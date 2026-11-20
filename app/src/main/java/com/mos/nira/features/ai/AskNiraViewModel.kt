package com.mos.nira.features.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.AskNira
import com.mos.nira.core.ai.HybridInferenceRepository
import com.mos.nira.core.ai.HybridInferenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One question and what came back. */
data class AskMessage(
    val question: String,
    /** null while the request runs. */
    val result: HybridInferenceResult? = null,
    /** Set when an answer failed a check and is not shown. */
    val hold: AskNira.Hold? = null,
    /** Confidential mode is on and no on-device model is available. */
    val blocked: Boolean = false
)

/**
 * Ask NIRA chat for one job. Text only (the job's facts), never the image. Routing follows
 * AiPrivacyPolicy: on-device preferred, Confidential mode blocks the cloud. Each answer is checked
 * with [AskNira.check] before it is shown.
 */
class AskNiraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HybridInferenceRepository()

    private val _messages = MutableStateFlow<List<AskMessage>>(emptyList())
    val messages: StateFlow<List<AskMessage>> = _messages.asStateFlow()

    private var jobKey: String? = null

    init {
        AiSettings.load(application)
    }

    /** A different job starts a new conversation. */
    fun openJob(key: String) {
        if (key != jobKey) {
            jobKey = key
            _messages.value = emptyList()
        }
    }

    val running: Boolean get() = _messages.value.any { it.result == null && !it.blocked }

    fun ask(question: String, facts: List<String>) {
        val q = question.trim()
        if (q.isEmpty() || running) return
        // Only answers that passed the checks go back as context.
        val history = _messages.value.mapNotNull { m ->
            m.result?.text?.takeIf { m.hold == null }?.let { AskNira.Turn(m.question, it) }
        }
        _messages.update { it + AskMessage(q) }
        val index = _messages.value.lastIndex
        viewModelScope.launch {
            val app = getApplication<Application>()
            val template = withContext(Dispatchers.IO) {
                app.assets.open(AskNira.ASSET_PATH).bufferedReader().use { it.readText() }
            }
            val prompt = AskNira.build(template, facts, history, q)
            val onDevice = repository.checkOnDeviceStatus() == "available"
            val done = when (val route = AiPrivacyPolicy.decide(
                hasImage = false,
                confidentialMode = AiSettings.confidentialMode.value,
                onDeviceAvailable = onDevice,
                imageUploadAllowed = false
            )) {
                is AiRoute.Run -> {
                    val result = repository.generate(prompt, image = null, mode = route.mode)
                    AiSettings.record(app, result, imageAttached = false)
                    AskMessage(q, result, result.text?.let { AskNira.check(it, facts, q) })
                }
                else -> AskMessage(q, blocked = true)
            }
            _messages.update { list -> list.toMutableList().also { if (index in it.indices) it[index] = done } }
        }
    }
}
