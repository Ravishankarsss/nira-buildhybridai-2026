package com.mos.nira.core.commercial

import org.json.JSONArray
import org.json.JSONObject

/**
 * An order with more than one print on the garment (front, back, sleeve...). Each print is costed
 * on its own job with [PrintCost] and saved here as a snapshot; the order adds them up. Nothing
 * is shared between prints unless the user's factory says so: every print keeps its own screens
 * and its own set-up. [OrderTotal.setupOnceSaving] shows the difference if the factory charges
 * set-up once per order instead (plain arithmetic on the factory's own set-up rate).
 */
object OrderPlacements {

    enum class Position { FRONT, BACK, LEFT_SLEEVE, RIGHT_SLEEVE, OTHER }

    data class Placement(
        val position: Position,
        /** What the user sees for the job, e.g. "Artwork · Navy, Red". */
        val jobLabel: String,
        val method: CostMethod,
        val screens: Int,
        val quantity: Int?,
        val fixed: Double?,
        val perPc: Double?,
        val total: Double?,
        val complete: Boolean,
        val currency: String,
        /** The set-up rate included in [fixed] (null for digital, which has no set-up). */
        val setup: Double?
    )

    fun from(cost: CostResult, position: Position, jobLabel: String, rates: FactoryRates) = Placement(
        position = position,
        jobLabel = jobLabel,
        method = cost.method,
        screens = cost.screens,
        quantity = cost.quantity,
        fixed = cost.fixed,
        perPc = cost.perPc,
        total = cost.total,
        complete = cost.complete,
        currency = rates.currency,
        setup = if (cost.method == CostMethod.DIGITAL) null else rates.jobSetupCost
    )

    data class OrderTotal(
        val prints: Int,
        val screens: Int,
        /** Cost per garment with all prints; null when any print has no per-piece cost. */
        val perGarment: Double?,
        /** Order total; null when any total is missing or the quantities differ. */
        val total: Double?,
        val quantity: Int?,
        val quantitiesDiffer: Boolean,
        val currenciesDiffer: Boolean,
        /** True when any print's cost is partial (something not included). */
        val incomplete: Boolean,
        /** How much less if set-up is charged once per order; null when not applicable. */
        val setupOnceSaving: Double?
    )

    fun total(placements: List<Placement>): OrderTotal {
        val quantities = placements.map { it.quantity }.distinct()
        val quantitiesDiffer = quantities.size > 1
        val currenciesDiffer = placements.map { it.currency }.distinct().size > 1
        val usable = !currenciesDiffer
        val perGarment = if (usable && placements.isNotEmpty() && placements.all { it.perPc != null }) placements.sumOf { it.perPc!! } else null
        val total = if (usable && !quantitiesDiffer && placements.isNotEmpty() && placements.all { it.total != null }) placements.sumOf { it.total!! } else null
        val setups = placements.mapNotNull { it.setup }
        val saving = if (usable && setups.size > 1 && setups.distinct().size == 1) setups.first() * (setups.size - 1) else null
        return OrderTotal(
            prints = placements.size,
            screens = placements.sumOf { it.screens },
            perGarment = perGarment,
            total = total,
            quantity = quantities.singleOrNull(),
            quantitiesDiffer = quantitiesDiffer,
            currenciesDiffer = currenciesDiffer,
            incomplete = placements.any { !it.complete },
            setupOnceSaving = saving
        )
    }

    /** Adds a print, replacing any print already saved at the same position. */
    fun put(list: List<Placement>, p: Placement): List<Placement> =
        (list.filter { it.position != p.position } + p).sortedBy { it.position.ordinal }

    fun toJson(list: List<Placement>): String = JSONArray(list.map { p ->
        JSONObject()
            .put("position", p.position.name).put("job", p.jobLabel).put("method", p.method.name)
            .put("screens", p.screens).put("quantity", p.quantity ?: JSONObject.NULL)
            .put("fixed", p.fixed ?: JSONObject.NULL).put("per_pc", p.perPc ?: JSONObject.NULL)
            .put("total", p.total ?: JSONObject.NULL).put("complete", p.complete)
            .put("currency", p.currency).put("setup", p.setup ?: JSONObject.NULL)
    }).toString()

    fun fromJson(json: String?): List<Placement> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            fun d(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }
            runCatching {
                Placement(
                    position = Position.valueOf(o.getString("position")),
                    jobLabel = o.optString("job"),
                    method = CostMethod.valueOf(o.getString("method")),
                    screens = o.optInt("screens"),
                    quantity = if (o.isNull("quantity")) null else o.optInt("quantity"),
                    fixed = d("fixed"), perPc = d("per_pc"), total = d("total"),
                    complete = o.optBoolean("complete"),
                    currency = o.optString("currency"),
                    setup = d("setup")
                )
            }.getOrNull()
        }
    }
}
