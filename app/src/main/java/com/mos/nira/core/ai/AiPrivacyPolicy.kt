package com.mos.nira.core.ai

/** What to do with one AI request after the privacy rules are applied. */
sealed interface AiRoute {
    /** Send the request with this mode. */
    data class Run(val mode: HybridMode) : AiRoute

    /** The image would have to go to the cloud: ask the user first. Nothing is sent yet. */
    data object AskImageUpload : AiRoute

    /** Confidential mode is on and the on-device model is not available: nothing is sent. */
    data object BlockedByConfidentialMode : AiRoute
}

/**
 * Privacy rules from docs/MASTER_CONTEXT.md (sections 4, 5 and 12):
 * - The confidential image stays on the device unless the user agrees to send it.
 * - Confidential mode blocks every cloud call.
 * - Text and engine numbers may go to the cloud when Confidential mode is off.
 *
 * Pure Kotlin so the rules are unit tested.
 */
object AiPrivacyPolicy {

    /**
     * @param hasImage the request includes the artwork image
     * @param confidentialMode the Confidential mode switch in Settings
     * @param onDeviceAvailable the SDK reported the on-device model as available
     * @param imageUploadAllowed the user agreed to send this image to the cloud for this request
     */
    fun decide(
        hasImage: Boolean,
        confidentialMode: Boolean,
        onDeviceAvailable: Boolean,
        imageUploadAllowed: Boolean
    ): AiRoute = when {
        confidentialMode ->
            if (onDeviceAvailable) AiRoute.Run(HybridMode.ONLY_ON_DEVICE) else AiRoute.BlockedByConfidentialMode

        !hasImage -> AiRoute.Run(HybridMode.PREFER_ON_DEVICE)

        // The image may reach the cloud only with the user's agreement.
        imageUploadAllowed -> AiRoute.Run(HybridMode.PREFER_ON_DEVICE)

        // On-device only, so a failure can never fall back to sending the image.
        onDeviceAvailable -> AiRoute.Run(HybridMode.ONLY_ON_DEVICE)

        else -> AiRoute.AskImageUpload
    }
}
