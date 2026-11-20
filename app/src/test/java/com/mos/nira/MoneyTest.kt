package com.mos.nira

import com.mos.nira.core.ai.ReportExplanation
import com.mos.nira.core.commercial.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** World currencies: symbol, grouping and decimals per currency; old symbol-only rates still work. */
class MoneyTest {

    @Test
    fun eachCurrencyUsesItsOwnStyle() {
        assertEquals("₹1,92,000", Money.format("INR", 192000.0, 0))
        assertEquals("$192,000.00", Money.format("USD", 192000.0, 2))
        assertEquals("¥19,200", Money.format("JPY", 19200.0, 2))   // yen has no decimals
        assertTrue(Money.format("EUR", 192000.0, 2).startsWith("€192.000,00"))
        assertEquals("Rp 19.200", Money.format("IDR", 19200.0, 2))
    }

    @Test
    fun oldSymbolOnlyRatesStillWork() {
        assertEquals("INR", Money.normalise("₹"))
        assertEquals("₹10.25", Money.format("₹", 10.25, 2))
        assertTrue(Money.codes.size >= 30)
        assertEquals("₹1,00,00,000.00", Money.format("INR", 1.0e7, 2))
        assertEquals("₹950", Money.format("INR", 950.0, 0))
    }

    @Test
    fun plainAmountsPassTheNumberCheck() {
        val facts = listOf("Order cost now: ${Money.plain("IDR", 19200.0, 2)} for 500 pieces.")
        assertEquals("Rp 19200", Money.plain("IDR", 19200.0, 2))
        assertTrue(ReportExplanation.unverifiedNumbers("It costs Rp 19,200 for the order.", facts).isEmpty())
    }
}
