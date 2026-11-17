package com.mos.nira.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiPrivacyPolicyTest {

    private fun decide(image: Boolean, confidential: Boolean, onDevice: Boolean, upload: Boolean) =
        AiPrivacyPolicy.decide(image, confidential, onDevice, upload)

    @Test
    fun confidentialMode_neverUsesTheCloud() {
        for (image in listOf(true, false)) {
            for (upload in listOf(true, false)) {
                assertEquals(AiRoute.Run(HybridMode.ONLY_ON_DEVICE), decide(image, true, true, upload))
                assertEquals(AiRoute.BlockedByConfidentialMode, decide(image, true, false, upload))
            }
        }
    }

    @Test
    fun textOnly_mayFallBackToTheCloud() {
        assertEquals(AiRoute.Run(HybridMode.PREFER_ON_DEVICE), decide(false, false, true, false))
        assertEquals(AiRoute.Run(HybridMode.PREFER_ON_DEVICE), decide(false, false, false, false))
    }

    @Test
    fun image_staysOnDevice_withoutAgreement() {
        // On-device available: only on device, so a failure cannot send the image.
        assertEquals(AiRoute.Run(HybridMode.ONLY_ON_DEVICE), decide(true, false, true, false))
        // On-device not available: ask first, send nothing.
        assertEquals(AiRoute.AskImageUpload, decide(true, false, false, false))
    }

    @Test
    fun image_mayGoToTheCloud_onlyAfterAgreement() {
        assertEquals(AiRoute.Run(HybridMode.PREFER_ON_DEVICE), decide(true, false, false, true))
        assertEquals(AiRoute.Run(HybridMode.PREFER_ON_DEVICE), decide(true, false, true, true))
    }
}
