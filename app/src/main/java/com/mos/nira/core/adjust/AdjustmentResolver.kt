package com.mos.nira.core.adjust

/**
 * What the AI hands to the engine: an intent and whatever parameters it could read from the
 * user's words. Anything it could not read stays null. The AI never edits pixels.
 */
data class AdjustmentRequest(
    val intent: AdjustmentIntent,
    val ink: String? = null,
    val inkB: String? = null,
    val percentChange: Double? = null,
    val region: String? = null,
    val hex: String? = null,
    val shadeCode: String? = null,
    /** A HEX colour, or the word "garment". */
    val colour: String? = null,
    val minAreaMm2: Double? = null,
    val millimetres: Double? = null,
    val colourwayName: String? = null,
    val colourway: Map<String, String>? = null
)

/** Lookups the resolver needs from the rest of the app. */
class ResolverContext(
    /** Shade code -> HEX from the user's enabled shade libraries, or null if unknown. */
    val shadeHexByCode: (String) -> String? = { null },
    /** Mesh tpi -> minimum line width in mm, from print_rules.json detail_rules. */
    val minLineWidthMmByMesh: Map<Int, Double> = emptyMap()
)

sealed interface Resolution {
    /** The request is complete: this operation can be previewed. */
    class Ready(val op: AdjustmentOp) : Resolution

    /** The request is unclear: ask this one short question. Nothing is changed. */
    class Question(val text: String) : Resolution

    /** COMPARE_BEFORE_AFTER: show the comparison. Nothing is changed. */
    object Compare : Resolution
}

/**
 * Turns an [AdjustmentRequest] into an engine operation, or into one short question when
 * something is missing or ambiguous. It never guesses an ink, an amount or a colour.
 */
object AdjustmentResolver {

    private val HEX = Regex("^#?[0-9A-Fa-f]{6}$")

    fun resolve(request: AdjustmentRequest, state: SeparationState, context: ResolverContext = ResolverContext()): Resolution {
        return when (request.intent) {
            AdjustmentIntent.ADJUST_CHANNEL_STRENGTH -> {
                val ink = findInk(request.ink, state) ?: return askInk(state)
                val percent = percent(request.percentChange) ?: return Resolution.Question("By what percent?")
                Resolution.Ready(AdjustChannelStrength(ink, percent))
            }

            AdjustmentIntent.ADJUST_CHANNEL_CURVE -> {
                val ink = findInk(request.ink, state) ?: return askInk(state)
                val region = ToneRegion.values().firstOrNull { it.name.equals(request.region?.trim(), ignoreCase = true) }
                    ?: return Resolution.Question("Shadows, midtones or highlights?")
                val percent = percent(request.percentChange) ?: return Resolution.Question("By what percent?")
                Resolution.Ready(AdjustChannelCurve(ink, region, percent))
            }

            AdjustmentIntent.MERGE_COLOURS -> {
                val a = findInk(request.ink, state)
                val b = findInk(request.inkB, state)
                if (a == null || b == null || a == b) {
                    return Resolution.Question("Which two inks should be merged? ${inkList(state)}")
                }
                Resolution.Ready(MergeColours(a, b))
            }

            AdjustmentIntent.RECOLOUR_INK -> {
                val ink = findInk(request.ink, state) ?: return askInk(state)
                val hex = normaliseHex(request.hex)
                    ?: request.shadeCode?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                        normaliseHex(context.shadeHexByCode(code))
                            ?: return Resolution.Question("Shade $code is not in your shade library. Which colour (HEX or shade code)?")
                    }
                    ?: return Resolution.Question("Which new colour (HEX or shade code)?")
                Resolution.Ready(RecolourInk(ink, hex))
            }

            AdjustmentIntent.REMOVE_BACKGROUND -> {
                val colour = request.colour?.trim()
                when {
                    colour.equals("garment", ignoreCase = true) ->
                        Resolution.Ready(RemoveBackground(normaliseHex(state.garmentHex) ?: state.garmentHex, "garment"))
                    normaliseHex(colour) != null -> Resolution.Ready(RemoveBackground(normaliseHex(colour)!!))
                    else -> Resolution.Question("Which background colour: the garment, or a HEX colour?")
                }
            }

            AdjustmentIntent.DESPECKLE -> {
                val area = request.minAreaMm2?.takeIf { it.isFinite() && it > 0 }
                    ?: return Resolution.Question("Remove specks smaller than how many mm2?")
                Resolution.Ready(Despeckle(area))
            }

            AdjustmentIntent.CHOKE_BASE -> {
                if (state.channels.none { it.role == ChannelRole.BASE_WHITE }) {
                    return Resolution.Question("This separation has no white base screen. Add a white base first?")
                }
                val mm = request.millimetres?.takeIf { it.isFinite() && it > 0 }
                    ?: return Resolution.Question("Choke the base by how many mm?")
                Resolution.Ready(ChokeBase(mm))
            }

            AdjustmentIntent.THICKEN_LINES -> {
                val ink = if (request.ink.isNullOrBlank()) null else findInk(request.ink, state) ?: return askInk(state)
                val targets = if (ink != null) listOf(state.channel(ink)!!)
                else state.channels.filter { it.role == ChannelRole.COLOUR }
                if (targets.none { context.minLineWidthMmByMesh.containsKey(it.meshTpi) }) {
                    return Resolution.Question(
                        "Minimum line width is not available in the rules file for ${targets.map { it.meshTpi }.distinct().joinToString()} tpi. Which mesh should be used?"
                    )
                }
                Resolution.Ready(ThickenLines(ink, context.minLineWidthMmByMesh))
            }

            AdjustmentIntent.APPLY_COLOURWAY -> {
                val pairs = request.colourway?.takeIf { it.isNotEmpty() }
                    ?: return Resolution.Question("Which inks change, and to which HEX colours?")
                val resolved = LinkedHashMap<String, String>()
                for ((inkText, hexText) in pairs) {
                    val ink = findInk(inkText, state) ?: return Resolution.Question("Which ink is \"$inkText\"? ${inkList(state)}")
                    val hex = normaliseHex(hexText) ?: return Resolution.Question("Which HEX colour for $ink?")
                    resolved[ink] = hex
                }
                val name = request.colourwayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return Resolution.Question("What name should this colourway be saved under?")
                Resolution.Ready(ApplyColourway(name, resolved))
            }

            AdjustmentIntent.COMPARE_BEFORE_AFTER -> Resolution.Compare
        }
    }

    /**
     * Matches text to exactly one ink: by full name, by HEX, or by a part of the name that fits
     * only one ink. Returns null when nothing or more than one ink fits.
     */
    fun findInk(text: String?, state: SeparationState): String? {
        val query = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        state.channels.filter { it.name.equals(query, ignoreCase = true) }.singleOrNull()?.let { return it.name }
        normaliseHex(query)?.let { hex ->
            state.channels.filter { it.hex.equals(hex, ignoreCase = true) }.singleOrNull()?.let { return it.name }
        }
        return state.channels.filter { it.name.contains(query, ignoreCase = true) }.singleOrNull()?.name
    }

    fun normaliseHex(text: String?): String? {
        val t = text?.trim() ?: return null
        if (!HEX.matches(t)) return null
        return "#" + t.removePrefix("#").uppercase()
    }

    private fun percent(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it != 0.0 && it >= -100.0 }

    private fun inkList(state: SeparationState): String = state.channels.joinToString(", ") { it.name }

    private fun askInk(state: SeparationState): Resolution = Resolution.Question("Which ink? ${inkList(state)}")
}
