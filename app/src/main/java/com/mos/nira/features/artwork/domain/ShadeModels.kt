package com.mos.nira.features.artwork.domain

/**
 * Technical representation of a shade in a color library.
 */
data class ShadeEntry(
    val type: String, // e.g. TCX, TPG, TN, TSX, C, U, FACTORY, BUYER, NIRA_DEMO
    val code: String,
    val name: String,
    val labL: Double,
    val labA: Double,
    val labB: Double,
    val libraryName: String = "Built-in NIRA Demo",
    val isUserCreated: Boolean = false
) {
    val formattedLab: String get() = String.format("L* %.1f  a* %.1f  b* %.1f", labL, labA, labB)
}

/**
 * Result of matching a print color to a library shade.
 */
data class ShadeMatch(
    val shade: ShadeEntry,
    val deltaE00: Double,
    val grade: String // Excellent (<1.0), Good (1-2), Visible (2-4), Poor (>4)
)

/**
 * A shade library container (can be toggled on/off).
 */
data class ShadeLibrary(
    val name: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val entryCount: Int = 0,
    val types: List<String> = emptyList()
)
