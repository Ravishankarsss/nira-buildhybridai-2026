package com.mos.nira.features.commercial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.core.commercial.CostResult
import com.mos.nira.core.commercial.FactoryRates
import com.mos.nira.core.commercial.OrderPlacements
import com.mos.nira.core.commercial.OrderPlacements.Position

private val OInner = Color(0xFF0F1526)
private val OWhite = Color(0xFFF5F6FF)
private val OMuted = Color(0xFFA8B4CE)
private val OCyan = Color(0xFF55D6E8)
private val OAmber = Color(0xFFFFC870)
private val OGreen = Color(0xFF7BE0A0)

@Composable
fun positionName(p: Position): String = stringResource(
    when (p) {
        Position.FRONT -> R.string.order_front
        Position.BACK -> R.string.order_back
        Position.LEFT_SLEEVE -> R.string.order_left_sleeve
        Position.RIGHT_SLEEVE -> R.string.order_right_sleeve
        Position.OTHER -> R.string.order_other
    }
)

/**
 * Front + back (and sleeve) orders: add this job's cost to the order at a position; the order adds
 * the prints up. Each print keeps its own screens and set-up; the set-up-once difference is shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderPlacementsBlock(cost: CostResult?, rates: FactoryRates, jobLabel: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { OrderStore.load(context) }
    val order by OrderStore.order.collectAsState()
    if (cost?.perPc == null && order.isEmpty()) return

    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OInner).padding(12.dp)) {
        Text(stringResource(R.string.order_title), color = OMuted, fontSize = 11.sp)
        if (cost?.perPc != null) {
            Text(stringResource(R.string.order_add_hint), color = OMuted, fontSize = 11.sp, lineHeight = 15.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Position.values().forEach { p ->
                    val here = order.firstOrNull { it.position == p }
                    AssistChip(
                        onClick = { OrderStore.put(context, OrderPlacements.from(cost, p, jobLabel, rates)) },
                        label = { Text((if (here?.jobLabel == jobLabel) "✓ " else "+ ") + positionName(p), fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = if (here?.jobLabel == jobLabel) OGreen else OCyan)
                    )
                }
            }
        }
        if (order.isEmpty()) return@Column

        Spacer(Modifier.height(6.dp))
        order.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(positionName(p.position) + " · " + p.jobLabel, color = OWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        (p.perPc?.let { stringResource(R.string.cost_per_pc, M(p.currency, it, 2)) } ?: "—") +
                            "  ·  " + stringResource(R.string.order_screens, p.screens) +
                            (if (!p.complete) "  ·  " + stringResource(R.string.order_partial) else ""),
                        color = if (p.complete) OMuted else OAmber, fontSize = 11.sp
                    )
                }
                TextButton(onClick = { OrderStore.remove(context, p.position) }) { Text("✕", color = OMuted) }
            }
        }
        val t = OrderPlacements.total(order)
        val currency = order.first().currency
        Spacer(Modifier.height(4.dp))
        when {
            t.currenciesDiffer -> Text(stringResource(R.string.order_currency_differ), color = OAmber, fontSize = 12.sp)
            else -> {
                t.perGarment?.let {
                    Text(stringResource(R.string.order_per_garment, M(currency, it, 2), t.prints), color = OWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (t.quantitiesDiffer) Text(stringResource(R.string.order_qty_differ), color = OAmber, fontSize = 11.sp, lineHeight = 15.sp)
                t.total?.let { tot -> t.quantity?.let { q -> Text(stringResource(R.string.cost_total, M(currency, tot, 0), q), color = OWhite, fontSize = 13.sp) } }
                if (t.incomplete) Text(stringResource(R.string.order_incomplete), color = OAmber, fontSize = 11.sp)
                t.setupOnceSaving?.let { s ->
                    Text(stringResource(R.string.order_setup_once, M(currency, s, 0)), color = OMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
        TextButton(onClick = { OrderStore.clear(context) }) { Text(stringResource(R.string.order_clear), color = OMuted, fontSize = 11.sp) }
    }
}

private fun M(currency: String, v: Double, decimals: Int) = com.mos.nira.core.commercial.Money.format(currency, v, decimals)
