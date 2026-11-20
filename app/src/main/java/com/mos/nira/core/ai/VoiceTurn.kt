package com.mos.nira.core.ai

/**
 * Conversation mode: decides, from the microphone level every [STEP_MS], when the speaker has
 * finished (so nobody has to hold a button). These are app timing values for a phone microphone,
 * not print engineering values.
 */
object VoiceTurn {

    const val STEP_MS = 100
    /** MediaRecorder.getMaxAmplitude above this counts as speech (0..32767). */
    const val SPEECH_LEVEL = 1500
    /** After speech, this much quiet ends the question. */
    const val END_SILENCE_MS = 1500
    /** No speech at all for this long: nothing is sent. */
    const val NO_SPEECH_MS = 7000
    /** Two turns without speech in a row end the conversation. */
    const val MAX_SILENT_TURNS = 2

    data class State(val heardSpeech: Boolean = false, val quietMs: Int = 0, val elapsedMs: Int = 0)

    enum class Decision { KEEP_LISTENING, SEND, NOTHING_SAID }

    fun step(s: State, amplitude: Int, maxMs: Int): Pair<State, Decision> {
        val speech = amplitude > SPEECH_LEVEL
        val next = State(
            heardSpeech = s.heardSpeech || speech,
            quietMs = if (speech) 0 else if (s.heardSpeech) s.quietMs + STEP_MS else 0,
            elapsedMs = s.elapsedMs + STEP_MS
        )
        val decision = when {
            next.heardSpeech && (next.quietMs >= END_SILENCE_MS || next.elapsedMs >= maxMs) -> Decision.SEND
            !next.heardSpeech && next.elapsedMs >= minOf(NO_SPEECH_MS, maxMs) -> Decision.NOTHING_SAID
            else -> Decision.KEEP_LISTENING
        }
        return next to decision
    }
}
