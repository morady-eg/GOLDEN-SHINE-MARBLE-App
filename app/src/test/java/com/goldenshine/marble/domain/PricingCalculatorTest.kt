package com.goldenshine.marble.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PricingCalculatorTest {
    @Test fun calculatesSquareMeterLineAsQuantityTimesUnitPrice() {
        val result = PricingCalculator.calculate(
            listOf(PriceLine.SquareMeter("Marble polishing", 12.5, 200.0)),
            VatMode.Excluded
        )
        assertEquals(2500.0, result.subtotal, 0.001)
        assertEquals(0.0, result.vat, 0.001)
        assertEquals(2500.0, result.total, 0.001)
    }

    @Test fun combinesSquareMeterAndLumpSumLines() {
        val result = PricingCalculator.calculate(
            listOf(
                PriceLine.SquareMeter("Supply", 10.0, 300.0),
                PriceLine.LumpSum("Transport", 500.0)
            ),
            VatMode.Excluded
        )
        assertEquals(3500.0, result.subtotal, 0.001)
    }

    @Test fun addsVatToSubtotalWhenDocumentIsVatInclusive() {
        val result = PricingCalculator.calculate(
            listOf(PriceLine.LumpSum("Work", 1000.0)),
            VatMode.Included(ratePercent = 14.0)
        )
        assertEquals(140.0, result.vat, 0.001)
        assertEquals(1140.0, result.total, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeQuantity() {
        PricingCalculator.calculate(
            listOf(PriceLine.SquareMeter("Work", -1.0, 100.0)),
            VatMode.Excluded
        )
    }
}
