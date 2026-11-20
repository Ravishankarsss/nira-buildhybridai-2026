package com.mos.nira

import com.mos.nira.core.ai.VoiceTurn
import com.mos.nira.core.ai.VoiceTurn.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/** Conversation mode ends a question after speech and a pause, and sends nothing on silence. */
class VoiceTurnTest {

    private fun run(levels: List<Int>, maxMs: Int = 30_000): Pair<Decision, Int> {
        var s = VoiceTurn.State()
        levels.forEachIndexed { i, a ->
            val (n, d) = VoiceTurn.step(s, a, maxMs)
            s = n
            if (d != Decision.KEEP_LISTENING) return d to (i + 1) * VoiceTurn.STEP_MS
        }
        return Decision.KEEP_LISTENING to levels.size * VoiceTurn.STEP_MS
    }

    @Test
    fun speechThenPauseSends() {
        // 2 s of speech, then quiet: sent after 1.5 s of quiet.
        val (d, at) = run(List(20) { 5000 } + List(30) { 200 })
        assertEquals(Decision.SEND, d)
        assertEquals(2000 + VoiceTurn.END_SILENCE_MS, at)
    }

    @Test
    fun shortPausesInsideASentenceDoNotSend() {
        val (d, _) = run(List(10) { 5000 } + List(10) { 100 } + List(10) { 5000 })
        assertEquals(Decision.KEEP_LISTENING, d)
    }

    @Test
    fun silenceSendsNothing() {
        val (d, at) = run(List(100) { 300 })
        assertEquals(Decision.NOTHING_SAID, d)
        assertEquals(VoiceTurn.NO_SPEECH_MS, at)
    }

    @Test
    fun longSpeechIsSentAtTheLimit() {
        val (d, at) = run(List(400) { 5000 }, maxMs = 30_000)
        assertEquals(Decision.SEND, d)
        assertEquals(30_000, at)
    }
}
