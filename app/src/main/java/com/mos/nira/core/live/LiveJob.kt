package com.mos.nira.core.live

import kotlin.random.Random

/**
 * Live link to the web page: one job's text facts, shared under a random code for a limited time.
 * Pure functions (no Firebase) so the limits are unit-tested; firestore.rules checks the same limits.
 */
object LiveJob {
    const val COLLECTION = "live"

    /** No I, O, 0 or 1: easy to read out and type. Must match the pattern in firestore.rules. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 8

    /** How long the web page can read a shared job (firestore.rules allows at most 25 hours). */
    const val HOURS = 24

    const val MAX_FACTS = 60
    const val MAX_FACT_CHARS = 500
    const val MAX_PROMPT_CHARS = 20_000
    const val MAX_TITLE_CHARS = 120

    const val WEB_URL = "https://nira-90d39.web.app"

    private val CODE = Regex("^[A-HJ-NP-Z2-9]{8}$")

    fun newCode(random: Random = Random.Default): String =
        (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun isCode(code: String): Boolean = CODE.matches(code)

    /** "K7QP3MZD" -> "K7QP-3MZD" for reading out; the web accepts both. */
    fun display(code: String): String = if (code.length == CODE_LENGTH) code.take(4) + "-" + code.drop(4) else code

    /** Typed code -> stored code: upper case, dashes and spaces removed. Null if it is not a code. */
    fun normalise(typed: String): String? = typed.uppercase().filter { it.isLetterOrDigit() }.takeIf { isCode(it) }

    fun webLink(code: String): String = "$WEB_URL/?live=$code#try"

    /**
     * The document fields except uid and the two server times (added by the caller).
     * Facts and texts are cut to the limits so a write is never refused for size.
     */
    fun fields(title: String, methodName: String?, savedAt: String, facts: List<String>, prompt: String): Map<String, Any?> = mapOf(
        "v" to 1,
        "title" to title.take(MAX_TITLE_CHARS),
        "method_name" to methodName?.take(MAX_TITLE_CHARS),
        "saved_at" to savedAt.take(40),
        "generated_by" to "NIRA Android app, live (engine and rules files; no AI used to make these facts)",
        "facts" to facts.take(MAX_FACTS).map { it.take(MAX_FACT_CHARS) },
        "prompt" to prompt.take(MAX_PROMPT_CHARS),
    )
}
