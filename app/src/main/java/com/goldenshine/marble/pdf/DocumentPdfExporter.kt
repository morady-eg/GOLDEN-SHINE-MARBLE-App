package com.goldenshine.marble.pdf

import android.content.ContentValues
import android.net.Uri
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.provider.MediaStore
import com.goldenshine.marble.data.BusinessDocumentEntity
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale

object DocumentPdfExporter {
    fun save(context: Context, document: BusinessDocumentEntity, language: String = "ar"): Uri {
        val ar = language == "ar"
        val pdf = PdfDocument()
        val width = 595
        val height = 842
        val margin = 38f
        val gold = android.graphics.Color.rgb(177, 137, 48)
        val black = android.graphics.Color.rgb(30, 30, 30)
        val gray = android.graphics.Color.rgb(100, 100, 100)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; textSize = 21f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = black; textSize = 15f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = black; textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val muted = Paint(body).apply { color = gray; textSize = 10f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; strokeWidth = 1.2f }
        var pageNumber = 0
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNumber).create())
        var canvas = page.canvas
        var y = 42f
        fun draw(text: String, x: Float, baseline: Float, paint: Paint, right: Boolean = false) {
            paint.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
            canvas.drawText(text, x, baseline, paint)
        }
        fun newPage() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNumber).create())
            canvas = page.canvas
            y = 42f
            draw("GOLDEN SHINE MARBLE", margin, y, titlePaint)
            y += 28f
            canvas.drawLine(margin, y, width - margin, y, linePaint)
            y += 24f
        }
        draw("GOLDEN SHINE MARBLE", margin, y, titlePaint)
        draw(if (ar) "لأعمال الرخام والجرانيت" else "MARBLE & GRANITE WORKS", width - margin, y, muted, true)
        y += 20f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 31f
        draw(if (ar) (if (document.kind == "CONTRACT") "عقد مقاولة" else "عرض سعر") else (if (document.kind == "CONTRACT") "CONTRACT AGREEMENT" else "QUOTATION"), if (ar) width - margin else margin, y, heading, ar)
        y += 24f
        draw((if (ar) "رقم المستند: " else "Document No: ") + document.documentNumber, if (ar) width - margin else margin, y, body, ar)
        y += 22f
        draw((if (ar) "العميل: " else "Client: ") + document.clientName, if (ar) width - margin else margin, y, body, ar)
        y += 19f
        if (document.clientPhone.isNotBlank()) {
            draw((if (ar) "الهاتف: " else "Phone: ") + document.clientPhone, if (ar) width - margin else margin, y, body, ar)
            y += 19f
        }
        if (document.title.isNotBlank()) {
            draw((if (ar) "المشروع / العنوان: " else "Project / Title: ") + document.title, if (ar) width - margin else margin, y, body, ar)
            y += 24f
        }
        val right = ar
        val x1 = if (ar) width - margin else margin
        val x2 = if (ar) margin else width - margin
        draw(if (ar) "بيان الأعمال" else "WORK DESCRIPTION", x1, y, heading, right)
        draw(if (ar) "الكمية" else "QTY", if (ar) 205f else 390f, y, heading, right)
        draw(if (ar) "السعر" else "RATE", if (ar) 125f else 470f, y, heading, right)
        draw(if (ar) "الإجمالي" else "AMOUNT", if (ar) 70f else 525f, y, heading, right)
        y += 10f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 21f
        val rows = JSONArray(document.linesJson)
        for (i in 0 until rows.length()) {
            val item = rows.getJSONObject(i)
            val description = item.optString("description")
            val sqm = item.optString("type") == "SQM"
            val quantity = if (sqm) item.optDouble("quantity") else 1.0
            val rateValue = if (sqm) item.optDouble("unitPrice") else item.optDouble("amount")
            val amount = if (sqm) quantity * rateValue else rateValue
            if (y > 735f) newPage()
            draw(description.take(48), x1, y, body, right)
            draw(if (sqm) number(quantity) else "1", if (ar) 205f else 390f, y, body, right)
            draw(money(rateValue), if (ar) 125f else 470f, y, body, right)
            draw(money(amount), if (ar) 70f else 525f, y, body, right)
            y += 24f
            canvas.drawLine(margin, y - 8f, width - margin, y - 8f, Paint(linePaint).apply { alpha = 65; strokeWidth = .5f })
        }
        y += 12f
        if (y > 700f) newPage()
        val summaryX = if (ar) width - margin else margin
        draw((if (ar) "الإجمالي قبل الضريبة: " else "Subtotal: ") + money(document.subtotal) + " EGP", summaryX, y, body, ar); y += 21f
        draw((if (ar) "ضريبة القيمة المضافة (" else "VAT (") + number(document.vatRate) + "%): " + money(document.vatAmount) + " EGP", summaryX, y, body, ar); y += 25f
        draw((if (ar) "الإجمالي النهائي: " else "GRAND TOTAL: ") + money(document.total) + " EGP", summaryX, y, heading, ar); y += 30f
        if (document.notes.isNotBlank()) {
            draw(if (ar) "ملاحظات:" else "Notes:", summaryX, y, heading, ar); y += 19f
            val note = document.notes.take(180)
            draw(note, summaryX, y, body, ar)
        }
        draw("GOLDEN SHINE MARBLE", margin, 800f, Paint(muted).apply { color = gold; typeface = Typeface.DEFAULT_BOLD })
        draw((if (ar) "صفحة " else "Page ") + pageNumber, width - margin, 800f, muted, true)
        pdf.finishPage(page)
        val filename = document.documentNumber.replace('/', '-') + "-" + language + ".pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run { pdf.close(); error("تعذر إنشاء ملف PDF") }
        try {
            context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) } ?: error("تعذر حفظ PDF")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } finally { pdf.close() }
        return uri
    }

    private fun money(value: Double) = NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)
    private fun number(value: Double) = NumberFormat.getNumberInstance(Locale.US).format(value)
}
