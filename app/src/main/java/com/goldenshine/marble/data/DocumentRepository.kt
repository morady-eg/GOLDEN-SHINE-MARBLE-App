package com.goldenshine.marble.data

import androidx.room.withTransaction
import com.goldenshine.marble.domain.DocumentKind
import com.goldenshine.marble.domain.DocumentLine
import com.goldenshine.marble.domain.DocumentNumbering
import com.goldenshine.marble.domain.DocumentPricing
import com.goldenshine.marble.domain.VatTreatment
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class DocumentLineInput(
    val description: String,
    val type: String,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val amount: Double = 0.0
)

class DocumentRepository(private val database: GoldenShineDatabase) {
    private val dao = database.documents()

    suspend fun all() = dao.getAll()
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun search(query: String) = if (query.isBlank()) dao.getAll() else dao.search(query)
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun save(
        id: Long?,
        kind: DocumentKind,
        clientName: String,
        phone: String,
        title: String,
        lines: List<DocumentLineInput>,
        addVat: Boolean,
        vatRate: Double,
        notes: String,
        quotationMetaJson: String = "{}"
    ): Long = database.withTransaction {
        require(clientName.isNotBlank()) { "اسم العميل مطلوب" }
        require(lines.isNotEmpty()) { "أضف بنداً واحداً على الأقل" }
        val domainLines = lines.map {
            require(it.description.isNotBlank()) { "وصف كل بند مطلوب" }
            when (it.type) {
                "SQM" -> DocumentLine.SquareMeter(it.description, it.quantity, it.unitPrice)
                "LUMP" -> DocumentLine.LumpSum(it.description, it.amount)
                else -> error("نوع تسعير غير معروف")
            }
        }
        val incomingMeta = JSONObject(quotationMetaJson)
        val discount = incomingMeta.optDouble("discountAmount", 0.0)
        val totals = DocumentPricing.calculate(
            domainLines,
            if (addVat) VatTreatment.ADD_TO_TOTAL else VatTreatment.EXCLUDED,
            vatRate,
            discount
        )
        val existing = id?.let { dao.getById(it) }
        require(id == null || existing != null) { "المستند المراد تعديله غير موجود" }
        val year = existing?.year ?: Calendar.getInstance().get(Calendar.YEAR)
        val sequence = existing?.sequence ?: (dao.maxSequence(kind.name, year) + 1)
        val number = existing?.documentNumber ?: DocumentNumbering.format(kind, year, sequence)
        val linesJson = JSONArray().apply {
            lines.forEach { line ->
                put(JSONObject().put("description", line.description.trim())
                    .put("type", line.type).put("quantity", line.quantity)
                    .put("unitPrice", line.unitPrice).put("amount", line.amount))
            }
        }.toString()
        val now = System.currentTimeMillis()
        if (existing != null) {
            val previousMeta = runCatching { JSONObject(existing.quotationMetaJson) }.getOrDefault(JSONObject())
            val history = previousMeta.optJSONArray("history") ?: JSONArray()
            history.put(JSONObject().put("updatedAt", existing.updatedAt)
                .put("documentNumber", existing.documentNumber).put("clientName", existing.clientName)
                .put("subtotal", existing.subtotal).put("vatAmount", existing.vatAmount)
                .put("total", existing.total).put("linesJson", existing.linesJson)
                .put("quotationMetaJson", existing.quotationMetaJson))
            incomingMeta.put("history", history)
            if (!incomingMeta.has("status")) incomingMeta.put("status", previousMeta.optString("status", "DRAFT"))
        } else if (!incomingMeta.has("status")) incomingMeta.put("status", "DRAFT")
        val entity = BusinessDocumentEntity(
            id = existing?.id ?: 0,
            kind = kind.name, year = year, sequence = sequence, documentNumber = number,
            clientName = clientName.trim(), clientPhone = phone.trim(), title = title.trim(),
            linesJson = linesJson, vatTreatment = if (addVat) "ADD_TO_TOTAL" else "EXCLUDED",
            vatRate = vatRate, subtotal = totals.subtotal, vatAmount = totals.vat,
            total = totals.total, notes = notes.trim(),
            createdAt = existing?.createdAt ?: now, updatedAt = now,
            quotationMetaJson = incomingMeta.toString()
        )
        if (existing == null) dao.insert(entity) else { dao.update(entity); entity.id }
    }

    suspend fun exportBackup(): String = database.withTransaction {
        val array = JSONArray()
        dao.getAll().forEach { d ->
            array.put(JSONObject().put("id", d.id).put("kind", d.kind).put("year", d.year)
                .put("sequence", d.sequence).put("documentNumber", d.documentNumber)
                .put("clientName", d.clientName).put("clientPhone", d.clientPhone)
                .put("title", d.title).put("linesJson", d.linesJson)
                .put("vatTreatment", d.vatTreatment).put("vatRate", d.vatRate)
                .put("subtotal", d.subtotal).put("vatAmount", d.vatAmount).put("total", d.total)
                .put("notes", d.notes).put("createdAt", d.createdAt).put("updatedAt", d.updatedAt)
                .put("quotationMetaJson", d.quotationMetaJson))
        }
        JSONObject().put("format", "GOLDEN_SHINE_BACKUP").put("version", 1)
            .put("documents", array).toString(2)
    }

    suspend fun restoreBackup(json: String): Int = database.withTransaction {
        val root = JSONObject(json)
        require(root.getString("format") == "GOLDEN_SHINE_BACKUP") { "ملف النسخة الاحتياطية غير صحيح" }
        require(root.getInt("version") == 1) { "إصدار النسخة الاحتياطية غير مدعوم" }
        val array = root.getJSONArray("documents")
        val restored = mutableListOf<BusinessDocumentEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val kind = o.getString("kind")
            require(kind == DocumentKind.QUOTATION.name || kind == DocumentKind.CONTRACT.name)
            val lines = o.getString("linesJson")
            require(JSONArray(lines).length() > 0)
            restored += BusinessDocumentEntity(
                id = o.getLong("id"), kind = kind, year = o.getInt("year"),
                sequence = o.getInt("sequence"), documentNumber = o.getString("documentNumber"),
                clientName = o.getString("clientName"), clientPhone = o.optString("clientPhone"),
                title = o.optString("title"), linesJson = lines,
                vatTreatment = o.getString("vatTreatment"), vatRate = o.getDouble("vatRate"),
                subtotal = o.getDouble("subtotal"), vatAmount = o.getDouble("vatAmount"),
                total = o.getDouble("total"), notes = o.optString("notes"),
                createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
                quotationMetaJson = o.optString("quotationMetaJson", "{}")
            )
        }
        dao.deleteAll()
        dao.insertAll(restored)
        restored.size
    }
}
