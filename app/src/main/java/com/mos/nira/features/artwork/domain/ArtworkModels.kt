package com.mos.nira.features.artwork.domain

/**
 * Technical representation of an extracted dominant color.
 */
data class DominantColor(
    val id: Int,
    val hex: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val labL: Double,
    val labA: Double,
    val labB: Double,
    val pixelCount: Int,
    val pixelPercentage: Float, // 0.0f to 100.0f
    val groupId: Int = id
) {
    val formattedRgb: String get() = "RGB($r, $g, $b)"
    val formattedLab: String get() = String.format("L* %.1f  a* %.1f  b* %.1f", labL, labA, labB)
    val formattedPercentage: String get() = String.format("%.1f%%", pixelPercentage)
}

/**
 * Group of perceptually similar colors clustered together.
 */
data class ColorGroup(
    val groupId: Int,
    val primaryColor: DominantColor,
    val members: List<DominantColor>,
    val totalPercentage: Float
)

/**
 * Technical metadata for an artwork asset.
 */
data class ArtworkMetadata(
    val fileName: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String
) {
    val resolutionFormatted: String get() = "$width × $height px"
    val fileSizeFormatted: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown"
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.2f MB", mb)
            } else {
                String.format("%.1f KB", kb)
            }
        }
}

/**
 * Complete record of a local artwork analysis.
 */
data class ArtworkAnalysisRecord(
    val id: String,
    val fileName: String,
    val imageUri: String,
    val thumbnailPath: String? = null,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val timestamp: Long,
    val palette: List<DominantColor>,
    val colorGroups: List<ColorGroup> = emptyList(),
    val algorithmVersion: String = "1.0.0-kmeans-cielab"
)

/**
 * Pre-structured data container for future AI integration (Phase 2+).
 */
data class ArtworkAnalysisInput(
    val imageReference: String,
    val palette: List<DominantColor>,
    val metadata: ArtworkMetadata,
    val garmentContext: String? = null
)

/**
 * Structured response container for future AI analysis (Phase 2+).
 */
data class ArtworkAIAnalysis(
    val artworkDescription: String,
    val detectedPrintEffects: List<String>,
    val visualDesignObservations: List<String>,
    val suggestedTextilePrintProcess: String,
    val confidence: Float,
    val technicalEvidence: Map<String, String>
)
