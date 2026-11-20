package com.mos.nira.features.commercial

import android.content.Context
import com.mos.nira.core.commercial.FactoryRates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The factory's own rates, kept only on this phone (SharedPreferences). Shared by Settings and the
 * decision card. "Load sample rates" reads assets/sample_factory_rates.json; nothing is hard-coded.
 */
object FactoryRatesStore {

    private const val PREFS = "factory_rates"
    private val _rates = MutableStateFlow(FactoryRates())
    val rates: StateFlow<FactoryRates> = _rates.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun d(key: String) = p.getString(key, null)?.toDoubleOrNull()
        _rates.value = FactoryRates(
            currency = com.mos.nira.core.commercial.Money.normalise(p.getString("currency", "") ?: ""),
            screenMakeCost = d("screen_make_cost"),
            jobSetupCost = d("job_setup_cost"),
            printRunPerScreen = d("print_run_per_screen"),
            inkCostPerKg = d("ink_cost_per_kg"),
            digitalRatePerPc = d("digital_rate_per_pc"),
            isSample = p.getBoolean("is_sample", false)
        )
    }

    fun save(context: Context, rates: FactoryRates) {
        _rates.value = rates
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("currency", rates.currency)
            .putString("screen_make_cost", rates.screenMakeCost?.toString())
            .putString("job_setup_cost", rates.jobSetupCost?.toString())
            .putString("print_run_per_screen", rates.printRunPerScreen?.toString())
            .putString("ink_cost_per_kg", rates.inkCostPerKg?.toString())
            .putString("digital_rate_per_pc", rates.digitalRatePerPc?.toString())
            .putBoolean("is_sample", rates.isSample)
            .apply()
    }

    /** The sample rates from the assets file, marked as samples; null if the file cannot be read. */
    fun sample(context: Context): FactoryRates? = try {
        val j = JSONObject(context.assets.open("sample_factory_rates.json").bufferedReader().use { it.readText() })
        FactoryRates(
            currency = j.getString("currency"),
            screenMakeCost = j.getDouble("screen_make_cost"),
            jobSetupCost = j.getDouble("job_setup_cost"),
            printRunPerScreen = j.getDouble("print_run_per_screen"),
            inkCostPerKg = j.getDouble("ink_cost_per_kg"),
            digitalRatePerPc = j.getDouble("digital_rate_per_pc"),
            isSample = true
        )
    } catch (e: Exception) {
        null
    }
}
