package com.mos.nira.features.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mos.nira.core.ai.AiPrivacyPolicy
import com.mos.nira.core.ai.AiRoute
import com.mos.nira.core.ai.AiSettings
import com.mos.nira.core.ai.AskNira
import com.mos.nira.core.ai.NiraActions
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
    /** Confidential mode is on and no on-device model is available (or a voice question). */
    val blocked: Boolean = false,
    /** A spoken question: [question] is what the AI wrote down from the recording. */
    val voice: Boolean = false,
    /** The answer to show (from the JSON reply). */
    val answer: String? = null,
    /** Actions the AI proposed, each checked by NiraActions.validate (rejected ones say why). */
    val actions: List<com.mos.nira.core.ai.NiraActions.Proposed> = emptyList()
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

    fun ask(question: String, facts: List<String>, actionContext: NiraActions.Context?) {
        val q = question.trim()
        if (q.isEmpty() || running) return
        // Only answers that passed the checks go back as context.
        val history = history()
        _messages.update { it + AskMessage(q) }
        val index = _messages.value.lastIndex
        viewModelScope.launch {
            val app = getApplication<Application>()
            val template = withContext(Dispatchers.IO) {
                app.assets.open(AskNira.ASSET_PATH).bufferedReader().use { it.readText() }
            }
            val prompt = AskNira.build(template, facts, history, q, lists(actionContext))
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
                    val reply = result.text?.let { NiraActions.parseReply(it) }
                    when {
                        result.text == null || reply == null -> AskMessage(q, result, answer = null)
                        else -> AskMessage(
                            q, result, AskNira.check(reply.answer, facts, q), answer = reply.answer,
                            actions = actionsOf(reply, actionContext, q)
                        )
                    }
                }
                else -> AskMessage(q, blocked = true)
            }
            _messages.update { list -> list.toMutableList().also { if (index in it.indices) it[index] = done } }
        }
    }

    /** Only answers that passed the checks go back as context. */
    private fun history(): List<AskNira.Turn> = _messages.value.mapNotNull { m ->
        m.answer?.takeIf { m.hold == null && m.question.isNotBlank() }?.let { AskNira.Turn(m.question, it) }
    }

    /**
     * A spoken question: the recording goes to the cloud model, which writes the question down and
     * answers from the facts. Never sent in Confidential mode (the on-device model has no audio).
     * The same checks run on the written-down question and the answer.
     */
    fun askVoice(audio: ByteArray, mimeType: String, facts: List<String>, pendingLabel: String, actionContext: NiraActions.Context?) {
        if (running || audio.isEmpty()) return
        val history = history()
        _messages.update { it + AskMessage(pendingLabel, voice = true) }
        val index = _messages.value.lastIndex
        viewModelScope.launch {
            val app = getApplication<Application>()
            val done = if (AiSettings.confidentialMode.value) {
                AskMessage(pendingLabel, blocked = true, voice = true)
            } else {
                val template = withContext(Dispatchers.IO) { app.assets.open(AskNira.VOICE_ASSET_PATH).bufferedReader().use { it.readText() } }
                val result = repository.generateWithAudio(AskNira.buildVoice(template, facts, history, lists(actionContext)), audio, mimeType, com.mos.nira.core.ai.HybridMode.ONLY_IN_CLOUD)
                AiSettings.record(app, result, imageAttached = false)
                val reply = result.text?.let { NiraActions.parseReply(it) }
                val heard = reply?.question
                when {
                    result.text == null -> AskMessage(pendingLabel, result, voice = true)
                    reply == null || heard == null -> AskMessage(pendingLabel, result, voice = true, answer = null)
                    else -> AskMessage(
                        heard, result, AskNira.check(reply.answer, facts, heard), voice = true, answer = reply.answer,
                        actions = actionsOf(reply, actionContext, heard)
                    )
                }
            }
            _messages.update { list -> list.toMutableList().also { if (index in it.indices) it[index] = done } }
        }
    }

    private fun lists(c: NiraActions.Context?): Map<String, String> =
        c?.let { NiraActions.promptLists(it) } ?: mapOf("{inks}" to "(none)", "{colours}" to "(none)", "{fibres}" to "(none)", "{fabrics}" to "(none)", "{languages}" to "(none)")

    /** Actions are run only when the answer itself passed the checks. */
    private fun actionsOf(reply: NiraActions.Reply, c: NiraActions.Context?, question: String): List<NiraActions.Proposed> =
        if (c == null) emptyList() else NiraActions.validate(reply.actions, c, question)
}
