package com.mos.nira.core.ai

/**
 * Ask NIRA: questions about one print job, answered by the AI from the job's facts only (the same
 * engine facts as "Explain this card", plus the order cost when the factory's rates are set).
 *
 * Guardrails, checked in code on every answer:
 *  - every number must be in the facts or in the user's own question (no AI arithmetic);
 *  - no colour codes (the facts have none);
 * An answer that fails a check is not shown; the user sees why instead.
 */
object AskNira {

    const val ASSET_PATH = "prompts/ask_nira_v2.txt"

    /** How many earlier question-and-answer pairs go back to the model (small on-device context). */
    const val HISTORY_TURNS = 3

    /** Longest earlier answer sent back as context, in characters. */
    private const val HISTORY_ANSWER_CHARS = 400

    data class Turn(val question: String, val answer: String)

    /** [lists] fills the action lists of the v2 prompts ({inks}, {colours}...; NiraActions.promptLists). */
    fun build(template: String, facts: List<String>, history: List<Turn>, question: String, lists: Map<String, String> = emptyMap()): String {
        val conversation = history.takeLast(HISTORY_TURNS).joinToString("\n") {
            "Q: ${it.question.trim()}\nA: ${it.answer.trim().take(HISTORY_ANSWER_CHARS)}"
        }.ifBlank { "(none)" }
        var t = template
        lists.forEach { (k, v) -> t = t.replace(k, v) }
        return t
            .replace("{facts}", facts.joinToString("\n") { "- $it" })
            .replace("{conversation}", conversation)
            .replace("{question}", question.trim())
            .trim()
    }

    const val VOICE_ASSET_PATH = "prompts/ask_nira_voice_v2.txt"

    /** Longest voice question recorded, in seconds. */
    const val VOICE_MAX_SECONDS = 30

    /** The voice prompt: the same facts and turns; the question is the attached audio. */
    fun buildVoice(template: String, facts: List<String>, history: List<Turn>, lists: Map<String, String> = emptyMap()): String =
        build(template, facts, history, "", lists)

    /** The model's written-down question and its answer; null when the reply is not usable. */
    fun parseVoice(reply: String): Turn? {
        val json = BriefReader.extractJson(reply) ?: return null
        val q = json.optString("question").trim()
        val a = json.optString("answer").trim()
        if (q.isEmpty() || a.isEmpty() || q == "null" || a == "null") return null
        return Turn(q, a)
    }

    /** Numbers in the answer that are neither in the facts nor in the question. */
    fun unverifiedNumbers(answer: String, facts: List<String>, question: String): List<Double> =
        ReportExplanation.unverifiedNumbers(answer, facts + question)

    /** Why an answer is held back, or null when it passed every check. */
    sealed interface Hold {
        data class Numbers(val values: List<Double>) : Hold
        data object ColourCode : Hold
    }

    fun check(answer: String, facts: List<String>, question: String): Hold? {
        val unverified = unverifiedNumbers(answer, facts, question)
        return when {
            unverified.isNotEmpty() -> Hold.Numbers(unverified)
            // A code the user typed in the question may be repeated; any other code was made up.
            CardExplanation.hasColourCode(answer.replace(Regex("#[0-9A-Fa-f]{6}")) { m ->
                if (question.contains(m.value, ignoreCase = true)) "" else m.value
            }) -> Hold.ColourCode
            else -> null
        }
    }
}
