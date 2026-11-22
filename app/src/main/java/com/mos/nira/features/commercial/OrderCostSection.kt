package com.mos.nira.features.commercial

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mos.nira.R
import com.mos.nira.core.commercial.CostDownKind
import com.mos.nira.core.commercial.CostDownOption
import com.mos.nira.core.commercial.CostMethod
import com.mos.nira.core.commercial.CostResult
import com.mos.nira.core.commercial.FactoryRates
import com.mos.nira.core.commercial.Missing
import com.mos.nira.core.commercial.Money as M
import com.mos.nira.features.ai.CardExplanationSection
import com.mos.nira.features.ai.CardExplanationViewModel
import com.mos.nira.features.ai.activityViewModelStoreOwner
import com.mos.nira.features.artwork.presentation.colourName

private val CInner = Color(0xFF0F1526)
private val CWhite = Color(0xFFF5F6FF)
private val CMuted = Color(0xFFA8B4CE)
private val CCyan = Color(0xFF55D6E8)
private val CViolet = Color(0xFFB3A8FF)
private val CAmber = Color(0xFFFFC870)
private val CGreen = Color(0xFF7BE0A0)

/** Money in the factory's currency: Indian grouping for ₹ (1,92,000), the phone's grouping otherwise. */
fun money(rates: FactoryRates, v: Double, decimals: Int): String =
    com.mos.nira.core.commercial.Money.format(rates.currency, v, decimals)



@Composable
private fun missingText(m: Missing, result: CostResult): String = when (m) {
    Missing.QUANTITY -> stringResource(R.string.miss_quantity)
    Missing.SCREEN_MAKE -> stringResource(R.string.miss_screen_make)
    Missing.JOB_SETUP -> stringResource(R.string.miss_setup)
    Missing.PRINT_RUN -> stringResource(R.string.miss_run)
    Missing.INK_PRICE -> stringResource(R.string.miss_ink_price)
    Missing.INK_GRAMS -> stringResource(R.string.miss_ink_grams, result.inksWithoutGrams.joinToString(", "))
    Missing.DIGITAL_RATE -> stringResource(R.string.miss_digital)
}

@Composable
private fun methodName(m: CostMethod): String = stringResource(
    when (m) {
        CostMethod.SCREEN -> R.string.method_screen
        CostMethod.DIGITAL -> R.string.method_digital
        CostMethod.DISCHARGE_DIGITAL -> R.string.method_discharge_digital
    }
)

/**
 * The order cost strip and the Cost-Down / rates actions inside the decision card. Every amount is
 * the deterministic PrintCost result with the factory's own (or clearly badged sample) rates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCostSection(
    cost: CostResult?,
    options: List<CostDownOption>,
    rates: FactoryRates,
    cardFacts: List<String>,
    costDownBefore: Bitmap?,
    costDownAfter: Bitmap?,
    onPreviewOption: (CostDownOption) -> Unit,
    onClosePreview: () -> Unit,
    onOptionSelected: (CostDownOption?) -> Unit = {},
    /** The job's name in the order list (front + back orders). */
    jobLabel: String = "",
    extraActions: @Composable () -> Unit = {}
) {
    var ratesOpen by remember { mutableStateOf(false) }
    var costDownOpen by remember { mutableStateOf(false) }

    if (ratesOpen) {
        AlertDialog(
            onDismissRequest = { ratesOpen = false },
            title = { Text(stringResource(R.string.rates_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { FactoryRatesEditor() } },
            confirmButton = { TextButton(onClick = { ratesOpen = false }) { Text(stringResource(R.string.rates_done)) } }
        )
    }

    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CInner).padding(12.dp)) {
        Text(stringResource(R.string.cost_title), color = CMuted, fontSize = 11.sp)
        when {
            cost == null -> {
                Text(stringResource(R.string.cost_add_rates), color = CAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { ratesOpen = true }) { Text(stringResource(R.string.cost_add_rates_button), color = CCyan, fontSize = 12.sp) }
            }
            cost.quantity == null -> Text(stringResource(R.string.cost_enter_qty), color = CAmber, fontSize = 13.sp)
            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        cost.perPc?.let { stringResource(R.string.cost_per_pc, money(rates, it, 2)) } ?: "—",
                        color = CWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    cost.total?.let { Text(stringResource(R.string.cost_total, money(rates, it, 0), cost.quantity), color = CWhite, fontSize = 13.sp) }
                }
                RatesBadge(rates, 11)
                Text(methodName(cost.method), color = CMuted, fontSize = 11.sp)
                if (cost.fixed != null && cost.variablePerPc != null) {
                    Text(
                        stringResource(R.string.cost_breakdown, cost.screens, money(rates, cost.fixed, 0), money(rates, cost.variablePerPc, 2)) +
                            (cost.inkCostPerPc?.let { "  •  " + stringResource(R.string.cost_ink, money(rates, it, 2)) } ?: ""),
                        color = CMuted, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
                // Market extras the factory set: spare pieces, minimum charge, GST.
                if (cost.wastagePieces > 0) Text(
                    stringResource(R.string.cost_wastage, cost.wastagePieces, pct(rates.wastagePercent)),
                    color = CMuted, fontSize = 11.sp, lineHeight = 15.sp
                )
                if (cost.minChargeApplied) Text(stringResource(R.string.cost_min_applied), color = CAmber, fontSize = 11.sp, lineHeight = 15.sp)
                cost.totalWithTax?.let {
                    Text(stringResource(R.string.cost_with_gst, pct(cost.taxPercent), money(rates, it, 0)), color = CWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                val notIncluded = cost.missing.filter { it != Missing.QUANTITY }
                if (notIncluded.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (cost.total == null) R.string.cost_needs else R.string.cost_not_included, notIncluded.map { missingText(it, cost) }.joinToString("; ")),
                        color = CAmber, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
            }
        }
        if (cost != null) {
            TextButton(onClick = { ratesOpen = true }) { Text(stringResource(R.string.cost_edit_rates), color = CCyan, fontSize = 11.sp) }
        }
    }

    // Front + back: add this print to the order; the order adds the prints up.
    OrderPlacementsBlock(cost, rates, jobLabel)

    // Actions: Cost-Down and the share sheet, one above the other (long labels in Tamil fit).
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        val best = options.maxOfOrNull { it.savingPerPc }
        Button(
            onClick = { costDownOpen = true },
            enabled = cost != null && cost.quantity != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CGreen, contentColor = Color(0xFF0F1526))
        ) {
            Text(
                if (best != null && best > 0) stringResource(R.string.costdown_button_save, money(rates, best, 2)) else stringResource(R.string.costdown_button),
                fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
        extraActions()
    }

    if (costDownOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selected by remember { mutableStateOf<CostDownOption?>(null) }
        ModalBottomSheet(
            onDismissRequest = { costDownOpen = false; onClosePreview() },
            sheetState = sheetState,
            containerColor = Color(0xFF171F35)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.costdown_title).uppercase(), color = CViolet, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.costdown_hint), color = CMuted, fontSize = 12.sp, lineHeight = 17.sp)
                RatesBadge(rates)

                if (selected != null && (costDownBefore != null || costDownAfter != null)) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(costDownBefore to R.string.costdown_now, costDownAfter to R.string.costdown_after).forEach { (bmp, label) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(), contentDescription = stringResource(label),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Text(stringResource(label), color = CMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (options.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.costdown_none), color = CMuted, fontSize = 13.sp)
                }
                options.forEach { o ->
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected == o) Color(0xFF263254) else CInner)
                            .clickable {
                                selected = o
                                onOptionSelected(o)
                                if (o.kind != CostDownKind.SWITCH_METHOD) onPreviewOption(o) else onClosePreview()
                            }
                            .padding(12.dp)
                    ) {
                        Text(optionTitle(o), color = CWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                        if (o.kind == CostDownKind.SWITCH_METHOD && o.breakEvenQty != null) {
                            Text(stringResource(R.string.costdown_breakeven, o.breakEvenQty), color = CMuted, fontSize = 12.sp)
                        }
                        Text(
                            stringResource(
                                R.string.costdown_new,
                                money(rates, o.after.perPc ?: 0.0, 2),
                                money(rates, o.savingPerPc, 2),
                                money(rates, o.savingTotal, 0)
                            ),
                            color = CGreen, fontSize = 12.sp, lineHeight = 17.sp
                        )
                        optionBadge(o)?.let { (text, colour) -> Text(text, color = colour, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        if (o.kind != CostDownKind.SWITCH_METHOD) Text(stringResource(R.string.costdown_tap_preview), color = CMuted, fontSize = 10.sp)
                    }
                }

                if (options.isNotEmpty() && cost?.total != null && cost.perPc != null) {
                    val facts = cardFacts + tradeOffFacts(cost, options, rates)
                    CardExplanationSection(
                        facts = facts,
                        methodName = null,
                        assetPath = com.mos.nira.core.ai.CardExplanation.TRADEOFF_ASSET_PATH,
                        buttonText = stringResource(R.string.costdown_explain),
                        hintText = stringResource(R.string.costdown_explain_hint),
                        viewModel = viewModel(viewModelStoreOwner = activityViewModelStoreOwner(), key = "tradeoff")
                    )
                }
            }
        }
    }
}

@Composable
private fun optionTitle(o: CostDownOption): String = when (o.kind) {
    CostDownKind.MERGE_COLOURS -> stringResource(R.string.costdown_merge, colourName(o.fromHex ?: ""), colourName(o.intoHex ?: ""))
    CostDownKind.SWITCH_METHOD -> stringResource(R.string.costdown_switch, methodName(o.after.method), o.after.quantity ?: 0)
    CostDownKind.LIGHT_GARMENT -> stringResource(R.string.costdown_light)
}

@Composable
private fun optionBadge(o: CostDownOption): Pair<String, Color>? = when (o.kind) {
    CostDownKind.MERGE_COLOURS -> if (o.closeMatch == true) stringResource(R.string.badge_close) to CGreen else stringResource(R.string.badge_visible) to CAmber
    CostDownKind.LIGHT_GARMENT -> stringResource(R.string.badge_buyer) to CAmber
    CostDownKind.SWITCH_METHOD -> null
}

/** English facts for "Explain trade-off": the engine's exact before / after numbers, no codes. */
fun tradeOffFacts(cost: CostResult, options: List<CostDownOption>, rates: FactoryRates): List<String> = buildList {
    val src = if (rates.isSample) "sample rates, not a quote" else "the factory's own rates"
    val c = rates.currency
    add("Order cost now: ${M.plain(c, cost.perPc!!, 2)} per piece, ${M.plain(c, cost.total!!, 0)} for ${cost.quantity} pieces (${src}).")
    options.forEach { o ->
        val what = when (o.kind) {
            CostDownKind.MERGE_COLOURS -> "Merge the ${com.mos.nira.core.image.ColourName.english(o.fromHex ?: "")} screen into the " +
                "${com.mos.nira.core.image.ColourName.english(o.intoHex ?: "")} screen, saving 1 screen (" +
                (if (o.closeMatch == true) "close colour match" else "visible colour shift, buyer approval needed") + ")"
            CostDownKind.SWITCH_METHOD -> "Switch to " + (if (o.after.method == CostMethod.SCREEN) "screen printing" else "digital printing") +
                " for ${o.after.quantity} pieces" + (o.breakEvenQty?.let { " (break-even at $it pieces)" } ?: "")
            CostDownKind.LIGHT_GARMENT -> "Print on a light garment and drop the white underbase screen (a buyer decision: the garment colour changes)"
        }
        add("Cost-Down option: $what. New cost ${M.plain(c, o.after.perPc ?: 0.0, 2)} per piece; saves ${M.plain(c, o.savingPerPc, 2)} per piece and ${M.plain(c, o.savingTotal, 0)} on the order.")
    }
}

/** "3" or "2.5" for a percentage the factory entered. */
private fun pct(v: Double?): String = when {
    v == null -> ""
    v % 1.0 == 0.0 -> v.toLong().toString()
    else -> v.toString()
}
