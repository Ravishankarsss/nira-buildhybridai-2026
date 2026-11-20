package com.mos.nira.features.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mos.nira.core.ai.SpeechLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Reads a checked answer aloud with Android's built-in text-to-speech (approved by Ravi, 20 Nov
 * 2026, option A). It only speaks text the app gives it; it never answers anything itself and
 * never changes the phone's speech settings.
 */
class NiraSpeaker(context: Context) {

    enum class Result { SPEAKING, VOICE_MISSING, NOT_READY }

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }

    /** The id of the answer being read, or null. */
    private val _speaking = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { _speaking.value = id }
            override fun onDone(id: String?) { if (_speaking.value == id) _speaking.value = null }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { if (_speaking.value == id) _speaking.value = null }
        })
    }

    fun speak(id: String, text: String, appLanguage: String): Result {
        if (!ready) return Result.NOT_READY
        val lang = SpeechLanguage.of(text) ?: appLanguage
        val locale = if (lang == "ta" || lang == "hi" || lang == "bn") Locale(lang, "IN") else Locale(lang)
        val set = tts.setLanguage(locale)
        if (set == TextToSpeech.LANG_MISSING_DATA || set == TextToSpeech.LANG_NOT_SUPPORTED) return Result.VOICE_MISSING
        _speaking.value = id
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        return Result.SPEAKING
    }

    fun stop() {
        tts.stop()
        _speaking.value = null
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
