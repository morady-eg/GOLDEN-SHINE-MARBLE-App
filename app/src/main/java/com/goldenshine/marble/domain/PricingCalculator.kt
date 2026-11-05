package com.goldenshine.marble.domain

import java.math.BigDecimal
import java.math.RoundingMode

sealed interface PriceLine {
    val description: String
    data class SquareMeter(
        override val description: String,
        val quantity: Double,
        val unitPrice: Double
    ) : PriceLine
    data class LumpSum(
        override val description: String,
        val amount: Double
    ) : PriceLine
}

sealed interface VatMode {
    data object Excluded : VatMode
    data class Included(val ratePercent: Double = 14.0) : VatMode
}

data class PriceSummary(
    val subtotal: Double,
    val vat: Double,
    val total: Double
)

object PricingCalculator {
    fun calculate(lines: List<PriceLine>, vatMode: VatMode): PriceSummary {
        require(lines.isNotEmpty()) { "At least one price line is required" }
        val subtotal = lines.sumOf { line ->
            when (line) {
                is PriceLine.SquareMeter -> {
                    require(line.quantity >= 0.0 && line.unitPrice >= 0.0) {
                        "Quantity and unit price must not be negative"
                    }
                    line.quantity * line.unitPrice
                }
                is PriceLine.LumpSum -> {
                    require(line.amount >= 0.0) { "Lump sum must not be negative" }
                    line.amount
                }
            }
        }.money()
        val rate = when (vatMode) {
            VatMode.Excluded -> 0.0
            is VatMode.Included -> {
                require(vatMode.ratePercent >= 0.0) { "VAT rate must not be negative" }
                vatMode.ratePercent
            }
        }
        val vat = (subtotal * rate / 100.0).money()
        return PriceSummary(subtotal, vat, (subtotal + vat).money())
    }

    private fun Double.money(): Double =
        BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()
}
