package com.mos.nira.features.artwork.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkModelsTest {

    @Test
    fun dominantColor_formattingIsAccurate() {
        val color = DominantColor(
            id = 1,
            hex = "#8B7CFF",
            r = 139,
            g = 124,
            b = 255,
            labL = 58.2,
            labA = 27.4,
            labB = -60.1,
            pixelCount = 1500,
            pixelPercentage = 34.56f
        )

        assertEquals("RGB(139, 124, 255)", color.formattedRgb)
        assertEquals("34.6%", color.formattedPercentage)
        assertEquals("L* 58.2  a* 27.4  b* -60.1", color.formattedLab)
    }

    @Test
    fun artworkMetadata_fileSizeAndResolutionFormatting() {
        val metaSmall = ArtworkMetadata(
            fileName = "print.png",
            width = 1920,
            height = 1080,
            fileSizeBytes = 512 * 1024, // 512 KB
            mimeType = "image/png"
        )
        assertEquals("1920 × 1080 px", metaSmall.resolutionFormatted)
        assertEquals("512.0 KB", metaSmall.fileSizeFormatted)

        val metaLarge = ArtworkMetadata(
            fileName = "garment_design.jpg",
            width = 4000,
            height = 3000,
            fileSizeBytes = 3500 * 1024, // ~3.42 MB
            mimeType = "image/jpeg"
        )
        assertEquals("4000 × 3000 px", metaLarge.resolutionFormatted)
        assertEquals("3.42 MB", metaLarge.fileSizeFormatted)
    }
}
