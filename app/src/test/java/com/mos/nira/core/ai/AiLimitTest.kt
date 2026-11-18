package com.mos.nira.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLimitTest {

    @Test
    fun quotaException_andHttp429_meanTheLimitIsReached() {
        assertTrue(AiLimit.isLimitReached("com.google.firebase.ai.type.QuotaExceededException", null))
        assertTrue(AiLimit.isLimitReached("com.google.firebase.ai.type.ServerException", "HTTP 429: RESOURCE_EXHAUSTED"))
        assertTrue(AiLimit.isLimitReached("java.lang.Exception", "Quota exceeded for requests per minute"))
        assertTrue(AiLimit.isLimitReached("java.lang.Exception", "Too Many Requests"))
    }

    @Test
    fun otherErrors_areNotTheLimit() {
        assertFalse(AiLimit.isLimitReached("com.google.firebase.ai.type.ServerException", "Firebase App Check token is invalid."))
        assertFalse(AiLimit.isLimitReached("java.io.IOException", "Unable to resolve host"))
        assertFalse(AiLimit.isLimitReached("java.lang.Exception", "took 4290 ms")) // 429 only as a whole number
        assertFalse(AiLimit.isLimitReached("java.lang.Exception", null))
    }
}
