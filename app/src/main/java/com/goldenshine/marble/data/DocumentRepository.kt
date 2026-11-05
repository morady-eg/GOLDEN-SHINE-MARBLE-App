package com.goldenshine.marble.data

import androidx.room.withTransaction
import com.goldenshine.marble.domain.DocumentKind
import com.goldenshine.marble.domain.DocumentNumbering
import org.json.JSONArray
import org.json.JSONObject

class DocumentRepository(private val database: GoldenShineDatabase) {
    private val dao = database.documents()

    suspend fun all() = dao.getAll()
    suspend fun search(query: String) = if (query.isBlank()) dao.getAll() else dao.search(query)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun update(document: BusinessDocumentEntity) = dao.update(document)

    suspend fun create(
        kind: DocumentKind, clientName: String, phone: String, title: String,
        description: String, pricingType: String, quantity: Double, unitPrice: Double, lumpSum: Double,
        addVat: Boolean, vatRate: Double, notes: String
    ): Long = database.withTransaction {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val sequence = dao.maxSequence(kind.name, year) + 1
        val number = DocumentNumbering.format(kind, year, sequence)
        val amount = if (pricingType == "SQM") quantity * unitPrice else lumpSum
        require(clientName.isNotBlank() && description.isNotBlank())
        require(amount >= 0.0 && vatRate >= 0.0)
        val subtotal = amount
        val vat = if (addVat) subtotal * vatRate / 100.0 else 0.0
        val lines = JSONArray().put(JSONObject().put("description", description).put("type", pricingType)
            .put("quantity", quantity).put("unitPrice", unitPrice).put("amount", lumpSum)).toString()
        val now = System.currentTimeMillis()
        dao.insert(BusinessDocumentEntity(
            kind = kind.name, year = year, sequence = sequence, documentNumber = number,
            clientName = clientName.trim(), clientPhone = phone.trim(), title = title.trim(),
            linesJson = lines, vatTreatment = if (addVat) "ADD_TO_TOTAL" else "EXCLUDED",
            vatRate = vatRate, subtotal = subtotal, vatAmount = vat,
            total = subtotal + vat, notes = notes.trim(), createdAt = now, updatedAt = now
        ))
    }
}
