package com.goldenshine.marble.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessDocumentTest {
    @Test fun addsVatToTotalWhenSelected() {
        val result = DocumentPricing.calculate(
            listOf(DocumentLine.SquareMeter("توريد رخام", 10.0, 500.0), DocumentLine.LumpSum("نقل", 1000.0)),
            VatTreatment.ADD_TO_TOTAL, 14.0
        )
        assertEquals(6000.0, result.subtotal, 0.001)
        assertEquals(840.0, result.vat, 0.001)
        assertEquals(6840.0, result.total, 0.001)
    }

    @Test fun doesNotAddVatWhenExcluded() {
        val result = DocumentPricing.calculate(listOf(DocumentLine.LumpSum("ترميم", 2000.0)), VatTreatment.EXCLUDED)
        assertEquals(0.0, result.vat, 0.001)
        assertEquals(2000.0, result.total, 0.001)
    }

    @Test fun quotationAndContractHaveIndependentPrefixes() {
        assertEquals("GS-Q-2026-0001", DocumentNumbering.format(DocumentKind.QUOTATION, 2026, 1))
        assertEquals("GS-C-2026-0001", DocumentNumbering.format(DocumentKind.CONTRACT, 2026, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSquareMeterQuantity() {
        DocumentPricing.calculate(listOf(DocumentLine.SquareMeter("رخام", 0.0, 500.0)), VatTreatment.EXCLUDED)
    }
}
