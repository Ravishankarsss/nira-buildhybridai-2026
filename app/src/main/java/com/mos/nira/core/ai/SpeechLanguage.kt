package com.mos.nira.core.ai

/**
 * Which voice should read an answer aloud: chosen from the answer's own script, so a Tamil answer
 * is read by the Tamil voice even when the app is in English. Latin-script answers return null
 * (the caller then uses the app's language).
 */
object SpeechLanguage {

    fun of(text: String): String? {
        val counts = mutableMapOf<String, Int>()
        for (ch in text) {
            val lang = when (ch.code) {
                in 0x0B80..0x0BFF -> "ta"
                in 0x0900..0x097F -> "hi"
                in 0x0980..0x09FF -> "bn"
                in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> "zh"
                else -> null
            } ?: continue
            counts[lang] = (counts[lang] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }
}
