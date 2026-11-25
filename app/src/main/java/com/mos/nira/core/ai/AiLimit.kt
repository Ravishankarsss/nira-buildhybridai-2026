package com.mos.nira.core.ai

/**
 * Recognises "the free AI limit is used up": the Gemini Developer API returns HTTP 429
 * (RESOURCE_EXHAUSTED) when a project's requests per minute or per day are used up, and the
 * Firebase AI SDK throws QuotaExceededException. Limits are per Firebase project, so all users
 * (including judges) share them. Pure Kotlin, unit tested.
 */
object AiLimit {

    fun isLimitReached(exceptionClassName: String, message: String?): Boolean {
        if (exceptionClassName.endsWith("QuotaExceededException")) return true
        val m = message?.lowercase() ?: return false
        return "resource_exhausted" in m || "quota" in m || Regex("\\b429\\b").containsMatchIn(m) ||
            "rate limit" in m || "too many requests" in m
    }

    /** The model is busy for everyone (HTTP 500/503 "high demand", UNAVAILABLE, overloaded): try again later. */
    fun isBusy(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return "high demand" in m || "overloaded" in m || "unavailable" in m || Regex("""\b503\b""").containsMatchIn(m)
    }
}
