package com.goldenshine.marble.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class DocumentKind(val prefix: String, val arabicName: String) {
    QUOTATION("GS-Q", "عرض سعر"),
    CONTRACT("GS-C", "عقد مقاولة")
}

enum class VatTreatment { EXCLUDED, ADD_TO_TOTAL }

sealed interface DocumentLine {
    val description: String
    data class SquareMeter(override val description: String, val quantity: Double, val unitPrice: Double) : DocumentLine
    data class LumpSum(override val description: String, val amount: Double) : DocumentLine
}

data class DocumentTotals(val subtotal: Double, val vat: Double, val total: Double, val discount: Double = 0.0)

object DocumentPricing {
    fun calculate(lines: List<DocumentLine>, treatment: VatTreatment, vatRate: Double = 14.0, discount: Double = 0.0): DocumentTotals {
        require(lines.isNotEmpty()) { "أضف بنداً واحداً على الأقل" }
        require(vatRate.isFinite() && vatRate >= 0.0) { "نسبة الضريبة غير صحيحة" }
        require(discount.isFinite() && discount >= 0.0) { "قيمة الخصم غير صحيحة" }
        val raw = lines.sumOf { line ->
            when (line) {
                is DocumentLine.SquareMeter -> {
                    require(line.description.isNotBlank())
                    require(line.quantity.isFinite() && line.unitPrice.isFinite() && line.quantity > 0.0 && line.unitPrice >= 0.0)
                    line.quantity * line.unitPrice
                }
                is DocumentLine.LumpSum -> {
                    require(line.description.isNotBlank() && line.amount.isFinite() && line.amount >= 0.0)
                    line.amount
                }
            }
        }
        require(raw.isFinite()) { "قيمة المستند أكبر من النطاق المسموح" }
        val subtotal = raw.money()
        require(discount <= subtotal) { "الخصم لا يمكن أن يتجاوز إجمالي البنود" }
        val net = (subtotal - discount).money()
        val vat = if (treatment == VatTreatment.ADD_TO_TOTAL) (net * vatRate / 100.0).money() else 0.0
        return DocumentTotals(subtotal, vat, (net + vat).money(), discount.money())
    }

    private fun Double.money() = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()
}

object DocumentNumbering {
    fun format(kind: DocumentKind, year: Int, sequence: Int): String {
        require(year in 2000..2100 && sequence > 0)
        return "${kind.prefix}-$year-${sequence.toString().padStart(4, '0')}"
    }
}
