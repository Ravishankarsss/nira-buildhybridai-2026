package com.mos.nira.features.commercial

import android.content.Context
import com.mos.nira.core.commercial.OrderPlacements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The current multi-print order, kept on this phone only (SharedPreferences "order_placements"). */
object OrderStore {

    private const val PREFS = "order_placements"
    private const val KEY = "placements"
    private val _order = MutableStateFlow<List<OrderPlacements.Placement>>(emptyList())
    val order: StateFlow<List<OrderPlacements.Placement>> = _order.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _order.value = OrderPlacements.fromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    private fun save(context: Context, list: List<OrderPlacements.Placement>) {
        _order.value = list
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, OrderPlacements.toJson(list)).apply()
    }

    fun put(context: Context, p: OrderPlacements.Placement) = save(context, OrderPlacements.put(_order.value, p))

    fun remove(context: Context, position: OrderPlacements.Position) = save(context, _order.value.filter { it.position != position })

    fun clear(context: Context) = save(context, emptyList())
}
