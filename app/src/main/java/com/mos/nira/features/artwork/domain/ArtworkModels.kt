package com.mos.nira.features.artwork.domain

/**
 * Technical representation of an extracted dominant or engineered print color.
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
    val groupId: Int = id,
    val isLocked: Boolean = false,
    val isGarmentBase: Boolean = false,
    val assignedShade: ShadeEntry? = null,
    val nearestShades: List<ShadeMatch> = emptyList()
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
 * Configuration options for auto-detecting and tuning print color separation.
 */
data class PrintColorConfig(
    val maxScreens: Int = 8,
    val mergeThresholdDeltaE00: Double = 3.2,
    val minCoveragePercentage: Float = 0.5f, // Drop colors below 0.5% coverage as speckle/noise
    val garmentColorHex: String? = null // When specified, pixels matching this are treated as fabric base
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
    val garmentColor: DominantColor? = null,
    val algorithmVersion: String = "2.0.0-ciede2000-automerge",
    /** Print colours measured by the Print Report engine (garment removed), when calculated. */
    val measuredPrintColourCount: Int? = null,
    /** "PHOTO" or "ARTWORK": what the user loaded; null for records saved before 18 Nov 2026. */
    val inputKind: String? = null,
    /** True once the user answered "Treat it as a photo?" (or the reverse). */
    val inputKindConfirmed: Boolean = false
) {
    /** The count shown to users: the measured print colours, or the older count for old records. */
    val printColourCount: Int get() = measuredPrintColourCount ?: palette.count { !it.isGarmentBase }
}
