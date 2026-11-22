package com.mos.nira.features.commercial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mos.nira.R
import com.mos.nira.core.commercial.FactoryRates

private val RMuted = Color(0xFFA8B4CE)
private val RAmber = Color(0xFFFFC870)
private val RGreen = Color(0xFF7BE0A0)
private val RCyan = Color(0xFF55D6E8)

/** The current factory rates (loaded from this phone on first use). */
@Composable
fun rememberFactoryRates(): FactoryRates {
    val context = LocalContext.current
    LaunchedEffect(Unit) { FactoryRatesStore.load(context) }
    val rates by FactoryRatesStore.rates.collectAsState()
    return rates
}

/** "Sample rates - not a quote" or "Based on your factory rates". */
@Composable
fun RatesBadge(rates: FactoryRates, fontSize: Int = 11) {
    Text(
        stringResource(if (rates.isSample) R.string.rates_sample_badge else R.string.rates_factory_badge),
        color = if (rates.isSample) RAmber else RGreen, fontSize = fontSize.sp, fontWeight = FontWeight.Bold
    )
}

/**
 * Editor for the factory's own rates. Editing any value turns the sample badge off. Used in
 * Settings and in the dialog the decision card opens.
 */
@Composable
fun FactoryRatesEditor(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rates = rememberFactoryRates()
    fun put(r: FactoryRates) = FactoryRatesStore.save(context, r.copy(isSample = false))
    // Bumped when "Load sample" or "Clear" replaces every value, so the fields show the new text.
    var resetKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.rates_hint), color = RMuted, fontSize = 12.sp, lineHeight = 17.sp)
        if (!rates.isEmpty) RatesBadge(rates)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { FactoryRatesStore.sample(context)?.let { FactoryRatesStore.save(context, it); resetKey++ } }) {
                Text(stringResource(R.string.rates_load_sample), fontSize = 12.sp, color = RCyan)
            }
            TextButton(onClick = { FactoryRatesStore.save(context, FactoryRates()); resetKey++ }) {
                Text(stringResource(R.string.rates_clear), fontSize = 12.sp, color = RMuted)
            }
        }
        CurrencyField(rates.currency) { code -> put(rates.copy(currency = code)) }
        NumberField(resetKey, stringResource(R.string.rates_screen), rates.screenMakeCost) { put(rates.copy(screenMakeCost = it)) }
        NumberField(resetKey, stringResource(R.string.rates_setup), rates.jobSetupCost) { put(rates.copy(jobSetupCost = it)) }
        NumberField(resetKey, stringResource(R.string.rates_run), rates.printRunPerScreen) { put(rates.copy(printRunPerScreen = it)) }
        NumberField(resetKey, stringResource(R.string.rates_ink), rates.inkCostPerKg) { put(rates.copy(inkCostPerKg = it)) }
        NumberField(resetKey, stringResource(R.string.rates_digital), rates.digitalRatePerPc) { put(rates.copy(digitalRatePerPc = it)) }
        // Market extras: optional; an empty field is "not included", never a guess.
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.rates_market_title), color = RCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.rates_market_hint), color = RMuted, fontSize = 12.sp, lineHeight = 17.sp)
        NumberField(resetKey, stringResource(R.string.rates_discharge_paste), rates.dischargePastePerPc) { put(rates.copy(dischargePastePerPc = it)) }
        NumberField(resetKey, stringResource(R.string.rates_flash), rates.flashCurePerPc) { put(rates.copy(flashCurePerPc = it)) }
        NumberField(resetKey, stringResource(R.string.rates_wastage), rates.wastagePercent) { put(rates.copy(wastagePercent = it)) }
        NumberField(resetKey, stringResource(R.string.rates_min_order), rates.minOrderCharge) { put(rates.copy(minOrderCharge = it)) }
        NumberField(resetKey, stringResource(R.string.rates_gst), rates.gstPercent) { put(rates.copy(gstPercent = it)) }
        Spacer(Modifier.height(4.dp))
    }
}

/** A list of world currencies with their names in the phone's language. */
@Composable
private fun CurrencyField(code: String, onPick: (String) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val M = com.mos.nira.core.commercial.Money
    fun label(c: String) = M.symbol(c) + "  " + M.displayName(c, locale) + " (" + c + ")"
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.rates_currency) + ": " + (if (code.isBlank()) "—" else label(code)),
                fontSize = 13.sp, color = RCyan
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            M.codes.forEach { c ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label(c), fontSize = 13.sp) },
                    onClick = { onPick(c); open = false }
                )
            }
        }
    }
}

@Composable
private fun NumberField(resetKey: Int, label: String, value: Double?, onValue: (Double?) -> Unit) {
    // The text the user types is kept as typed; the number saved is null until it parses.
    var text by androidx.compose.runtime.remember(resetKey) {
        androidx.compose.runtime.mutableStateOf(value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val clean = t.filter { it.isDigit() || it == '.' }.take(10)
            text = clean
            val v = clean.toDoubleOrNull()?.takeIf { it >= 0 }
            onValue(v)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
