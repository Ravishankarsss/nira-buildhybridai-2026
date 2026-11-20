package com.mos.nira.core.commercial

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Currencies for factory rates, worldwide. A rate card stores the ISO code (INR, USD, EUR...);
 * amounts are shown with that currency's symbol, its own number grouping (1,92,000 for rupees,
 * 192,000 for dollars, 192.000 for euros) and its own decimals (none for yen, won, dong, rupiah).
 */
object Money {

    /** code, symbol, locale whose number style the currency uses. */
    private val TABLE: List<Triple<String, String, Locale>> = listOf(
        Triple("INR", "₹", Locale("en", "IN")),
        Triple("USD", "$", Locale.US),
        Triple("EUR", "€", Locale.GERMANY),
        Triple("GBP", "£", Locale.UK),
        Triple("CNY", "¥", Locale.CHINA),
        Triple("JPY", "¥", Locale.JAPAN),
        Triple("BDT", "৳", Locale("en", "BD")),
        Triple("PKR", "Rs", Locale("en", "PK")),
        Triple("LKR", "Rs", Locale("en", "LK")),
        Triple("NPR", "Rs", Locale("en", "NP")),
        Triple("VND", "₫", Locale("vi", "VN")),
        Triple("IDR", "Rp", Locale("in", "ID")),
        Triple("THB", "฿", Locale("th", "TH")),
        Triple("MYR", "RM", Locale("ms", "MY")),
        Triple("PHP", "₱", Locale("en", "PH")),
        Triple("KHR", "៛", Locale("km", "KH")),
        Triple("MMK", "K", Locale("my", "MM")),
        Triple("KRW", "₩", Locale.KOREA),
        Triple("TRY", "₺", Locale("tr", "TR")),
        Triple("AED", "AED", Locale("en", "AE")),
        Triple("SAR", "SAR", Locale("en", "SA")),
        Triple("EGP", "E£", Locale("en", "EG")),
        Triple("ETB", "Br", Locale("en", "ET")),
        Triple("KES", "KSh", Locale("en", "KE")),
        Triple("MAD", "MAD", Locale("fr", "MA")),
        Triple("ZAR", "R", Locale("en", "ZA")),
        Triple("MXN", "$", Locale("es", "MX")),
        Triple("BRL", "R$", Locale("pt", "BR")),
        Triple("CAD", "$", Locale.CANADA),
        Triple("AUD", "$", Locale("en", "AU")),
        Triple("SGD", "$", Locale("en", "SG")),
        Triple("HKD", "HK$", Locale("zh", "HK"))
    )

    val codes: List<String> = TABLE.map { it.first }

    /** Rates saved before currencies had codes stored a symbol; map the common ones. */
    fun normalise(code: String): String = when (code.trim()) {
        "₹", "Rs.", "INR" -> "INR"
        "$" -> "USD"
        "€" -> "EUR"
        "£" -> "GBP"
        else -> code.trim()
    }

    fun symbol(code: String): String {
        val c = normalise(code)
        return TABLE.firstOrNull { it.first == c }?.second
            ?: runCatching { Currency.getInstance(c).symbol }.getOrNull()
            ?: c
    }

    private fun locale(code: String): Locale = TABLE.firstOrNull { it.first == normalise(code) }?.third ?: Locale.getDefault()

    /** Currencies written without decimals in practice (ISO says 2 for some of them). */
    private val NO_DECIMALS = setOf("IDR", "KHR", "MMK")

    /** Currencies grouped in lakhs and crores: 1,92,000 and 1,00,00,000. */
    private val LAKH = setOf("INR", "BDT", "PKR", "NPR", "LKR")

    private fun fractionDigits(code: String, wanted: Int): Int {
        if (normalise(code) in NO_DECIMALS) return 0
        val d = runCatching { Currency.getInstance(normalise(code)).defaultFractionDigits }.getOrNull() ?: 2
        return minOf(wanted, maxOf(0, d))
    }

    /** "₹1,92,000", "$192,000.00", "¥19,200", "Rp 19.200". */
    fun format(code: String, value: Double, decimals: Int): String {
        if (code.isBlank()) return number(value, decimals, Locale.getDefault())
        val sym = symbol(code)
        val digits = fractionDigits(code, decimals)
        val n = if (normalise(code) in LAKH) lakh(value, digits) else number(value, digits, locale(code))
        return if (sym.last().isLetter()) "$sym $n" else "$sym$n"
    }

    /** Lakh grouping, done here so it is the same on every phone: 1,92,000.50 */
    private fun lakh(value: Double, decimals: Int): String {
        val fixed = String.format(Locale.US, "%.${decimals}f", kotlin.math.abs(value))
        val whole = fixed.substringBefore('.')
        val frac = if (decimals > 0) "." + fixed.substringAfter('.') else ""
        val grouped = if (whole.length <= 3) whole else {
            val head = whole.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
        }
        return (if (value < 0) "-" else "") + grouped + frac
    }

    /** Plain amount for the AI facts: no grouping, so the number check matches it exactly. */
    fun plain(code: String, value: Double, decimals: Int): String {
        val n = String.format(Locale.US, "%.${fractionDigits(code, decimals)}f", value)
        if (code.isBlank()) return n
        val sym = symbol(code)
        return if (sym.last().isLetter()) "$sym $n" else "$sym$n"
    }

    /** The currency's name in the phone's language, e.g. "Indian Rupee", "இந்திய ரூபாய்". */
    fun displayName(code: String, inLocale: Locale): String =
        runCatching { Currency.getInstance(normalise(code)).getDisplayName(inLocale) }.getOrNull() ?: code

    private fun number(value: Double, decimals: Int, locale: Locale): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        return nf.format(value)
    }
}
