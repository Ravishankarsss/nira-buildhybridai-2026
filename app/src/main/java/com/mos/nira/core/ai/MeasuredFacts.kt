package com.mos.nira.core.ai

import java.util.Locale

/**
 * The facts Ask NIRA can answer from before the four brief questions are answered: only what the
 * engine has measured on the artwork. No method, screens, base or cost (they are not decided yet),
 * and the facts say so, so the AI does not guess them.
 */
object MeasuredFacts {

    data class Colour(val name: String, val coveragePercent: Double, val flagged: Boolean)

    /** [backgroundName] and [backgroundCoveragePercent] when a plain background was found; [backgroundPrinted] is the user's answer (null = not answered). */
    fun facts(
        isPhoto: Boolean,
        colours: List<Colour>,
        backgroundName: String? = null,
        backgroundCoveragePercent: Double? = null,
        backgroundPrinted: Boolean? = null,
        lowResolution: Boolean = false
    ): List<String> = buildList {
        add(if (isPhoto) "Input: a photo of a printed garment." else "Input: an artwork file.")
        val sure = colours.filter { !it.flagged }
        val flagged = colours.filter { it.flagged }
        add("Print colours measured by NIRA's engine (no AI): ${sure.size}" + if (flagged.isNotEmpty()) ", plus ${flagged.size} that need the user's check (shadow, edge or speck)." else ".")
        colours.sortedByDescending { it.coveragePercent }.forEach {
            add("Colour ${it.name}: ${pct(it.coveragePercent)} of the print" + if (it.flagged) " (needs the user's check)." else ".")
        }
        if (backgroundName != null && backgroundCoveragePercent != null) {
            add(
                "Plain background $backgroundName covers ${pct(backgroundCoveragePercent)} of the image: " + when (backgroundPrinted) {
                    true -> "the user said to print it."
                    false -> "the user said it is not ink; it is left out."
                    null -> "the user has not said yet whether it is ink."
                }
            )
        }
        if (lowResolution) add("The artwork resolution is below the rules file's limit for fine lines and small text.")
        add("Not decided yet: the print method, screens, white base, mesh, ink and cost. They need the four brief questions answered first (garment colour, fibre and fabric, quantity, feel).")
    }

    private fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v)
}
