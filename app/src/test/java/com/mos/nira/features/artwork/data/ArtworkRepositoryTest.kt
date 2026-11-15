package com.mos.nira.features.artwork.data

import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.ColorGroup
import com.mos.nira.features.artwork.domain.DominantColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkRepositoryTest {

    private fun createSampleRecord(id: String): ArtworkAnalysisRecord {
        val c1 = DominantColor(
            id = 0,
            hex = "#8B7CFF",
            r = 139,
            g = 124,
            b = 255,
            labL = 58.2,
            labA = 27.4,
            labB = -60.1,
            pixelCount = 1000,
            pixelPercentage = 60.0f
        )
        val c2 = DominantColor(
            id = 1,
            hex = "#55D6E8",
            r = 85,
            g = 214,
            b = 232,
            labL = 79.5,
            labA = -29.2,
            labB = -18.7,
            pixelCount = 666,
            pixelPercentage = 40.0f
        )

        val group = ColorGroup(
            groupId = 0,
            primaryColor = c1,
            members = listOf(c1),
            totalPercentage = 60.0f
        )

        return ArtworkAnalysisRecord(
            id = id,
            fileName = "sample_print.png",
            imageUri = "content://media/external/images/media/42",
            thumbnailPath = null,
            width = 1200,
            height = 800,
            fileSizeBytes = 204800L,
            timestamp = 1700000000000L,
            palette = listOf(c1, c2),
            colorGroups = listOf(group)
        )
    }

    @Test
    fun testRecordDataIntegrity() {
        val record = createSampleRecord("analysis-001")
        assertEquals("analysis-001", record.id)
        assertEquals("sample_print.png", record.fileName)
        assertEquals(2, record.palette.size)
        assertEquals(1, record.colorGroups.size)
        assertEquals(100.0f, record.palette[0].pixelPercentage + record.palette[1].pixelPercentage, 0.1f)
    }
}
