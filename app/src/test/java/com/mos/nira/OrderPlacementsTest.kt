package com.mos.nira

import com.mos.nira.core.commercial.CostMethod
import com.mos.nira.core.commercial.FactoryRates
import com.mos.nira.core.commercial.OrderPlacements
import com.mos.nira.core.commercial.OrderPlacements.Position
import com.mos.nira.core.advice.PlannedScreen
import com.mos.nira.core.commercial.PrintCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Front + back orders: each print costed on its own, then added; nothing shared unless stated. */
class OrderPlacementsTest {

    private val rates = FactoryRates("INR", 450.0, 500.0, 3.5, 850.0, 45.0)

    private fun screens(n: Int, grams: Double) = (1..n).map { PlannedScreen("Ink $it", "#1F2A5C", false, "137–156 tpi", "sourced", grams, grams, grams) }

    private fun screenPrint(pos: Position, n: Int, grams: Double, qty: Int?) =
        OrderPlacements.from(PrintCost.screen(screens(n, grams), qty, rates), pos, "job $pos", rates)

    @Test
    fun frontAndBackAddUp() {
        val front = screenPrint(Position.FRONT, 2, 1.0, 500)
        val back = OrderPlacements.from(PrintCost.digital(500, rates), Position.BACK, "back", rates)
        val t = OrderPlacements.total(listOf(front, back))
        assertEquals(2, t.prints)
        assertEquals(2, t.screens)
        assertEquals(front.perPc!! + 45.0, t.perGarment!!, 1e-9)
        assertEquals(front.total!! + 45.0 * 500, t.total!!, 1e-9)
        assertEquals(500, t.quantity)
        // Digital has no set-up, so there is only one set-up to share: no saving shown.
        assertNull(t.setupOnceSaving)
    }

    @Test
    fun setupOncePerOrderIsShownAsADifferenceNotAssumed() {
        val t = OrderPlacements.total(listOf(screenPrint(Position.FRONT, 2, 1.0, 500), screenPrint(Position.BACK, 1, 0.5, 500)))
        assertEquals(500.0, t.setupOnceSaving!!, 1e-9) // one set-up of 500 fewer
        // Both set-ups are still in the total.
        assertEquals(2 * 500.0 + 3 * 450.0, listOf(screenPrint(Position.FRONT, 2, 1.0, 500), screenPrint(Position.BACK, 1, 0.5, 500)).sumOf { it.fixed!! }, 1e-9)
    }

    @Test
    fun differentQuantitiesOrCurrenciesAreNotAdded() {
        val t = OrderPlacements.total(listOf(screenPrint(Position.FRONT, 1, 1.0, 500), screenPrint(Position.BACK, 1, 1.0, 800)))
        assertTrue(t.quantitiesDiffer)
        assertNull(t.total)
        val usd = OrderPlacements.from(PrintCost.digital(500, rates.copy(currency = "USD")), Position.BACK, "b", rates.copy(currency = "USD"))
        val m = OrderPlacements.total(listOf(screenPrint(Position.FRONT, 1, 1.0, 500), usd))
        assertTrue(m.currenciesDiffer)
        assertNull(m.perGarment)
        assertNull(m.total)
    }

    @Test
    fun samePositionReplacesAndJsonRoundTrips() {
        var list = OrderPlacements.put(emptyList(), screenPrint(Position.BACK, 1, 1.0, 500))
        list = OrderPlacements.put(list, screenPrint(Position.FRONT, 2, 1.0, 500))
        list = OrderPlacements.put(list, screenPrint(Position.FRONT, 3, 1.0, 500))
        assertEquals(listOf(Position.FRONT, Position.BACK), list.map { it.position })
        assertEquals(3, list.first().screens)
        val back = OrderPlacements.fromJson(OrderPlacements.toJson(list))
        assertEquals(list, back)
        assertTrue(OrderPlacements.fromJson("not json").isEmpty())
        assertFalse(OrderPlacements.total(list).incomplete)
        assertEquals(CostMethod.SCREEN, back.first().method)
    }
}
