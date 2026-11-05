package com.goldenshine.marble.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.goldenshine.marble.data.BusinessDocumentEntity
import org.json.JSONArray
import java.io.File
import java.text.NumberFormat
import java.util.Locale

object DocumentPdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val CONTENT_TOP = 150f
    private const val CONTENT_BOTTOM = 735f

    fun save(context: Context, document: BusinessDocumentEntity, language: String = "ar"): Uri {
        val ar = language == "ar"
        val pdf = PdfDocument()
        val letterhead = loadLetterhead(context)
        val margin = 38f
        val gold = android.graphics.Color.rgb(177, 137, 48)
        val black = android.graphics.Color.rgb(30, 30, 30)
        val gray = android.graphics.Color.rgb(75, 75, 75)
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black; textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black; textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val muted = Paint(body).apply { color = gray; textSize = 10f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; strokeWidth = 1.2f }

        var pageNumber = 0
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create())
        var canvas = page.canvas
        var y = CONTENT_TOP

        fun drawLetterhead(target: Canvas) {
            if (letterhead != null) {
                val scale = minOf(PAGE_WIDTH.toFloat() / letterhead.width, PAGE_HEIGHT.toFloat() / letterhead.height)
                val w = letterhead.width * scale
                val h = letterhead.height * scale
                val left = (PAGE_WIDTH - w) / 2f
                val top = (PAGE_HEIGHT - h) / 2f
                target.drawBitmap(letterhead, null, RectF(left, top, left + w, top + h),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } else {
                val fallback = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gold; textSize = 21f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                target.drawText("GOLDEN SHINE MARBLE", margin, 48f, fallback)
                target.drawLine(margin, 68f, PAGE_WIDTH - margin, 68f, linePaint)
            }
        }

        fun draw(text: String, x: Float, baseline: Float, paint: Paint, right: Boolean = false) {
            paint.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
            canvas.drawText(text, x, baseline, paint)
        }

        fun startNextPage() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create())
            canvas = page.canvas
            drawLetterhead(canvas)
            y = CONTENT_TOP
        }

        drawLetterhead(canvas)
        val title = if (ar) {
            if (document.kind == "CONTRACT") "عقد مقاولة" else "عرض سعر"
        } else {
            if (document.kind == "CONTRACT") "CONTRACT AGREEMENT" else "QUOTATION"
        }
        draw(title, if (ar) PAGE_WIDTH - margin else margin, y, heading, ar)
        y += 25f
        draw((if (ar) "رقم المستند: " else "Document No: ") + document.documentNumber,
            if (ar) PAGE_WIDTH - margin else margin, y, body, ar)
        y += 22f
        draw((if (ar) "العميل: " else "Client: ") + document.clientName,
            if (ar) PAGE_WIDTH - margin else margin, y, body, ar)
        y += 19f
        if (document.clientPhone.isNotBlank()) {
            draw((if (ar) "الهاتف: " else "Phone: ") + document.clientPhone,
                if (ar) PAGE_WIDTH - margin else margin, y, body, ar)
            y += 19f
        }
        if (document.title.isNotBlank()) {
            draw((if (ar) "المشروع / العنوان: " else "Project / Title: ") + document.title,
                if (ar) PAGE_WIDTH - margin else margin, y, body, ar)
            y += 24f
        }

        val right = ar
        val x1 = if (ar) PAGE_WIDTH - margin else margin
        draw(if (ar) "بيان الأعمال" else "WORK DESCRIPTION", x1, y, heading, right)
        draw(if (ar) "الكمية" else "QTY", if (ar) 205f else 390f, y, heading, right)
        draw(if (ar) "السعر" else "RATE", if (ar) 125f else 470f, y, heading, right)
        draw(if (ar) "الإجمالي" else "AMOUNT", if (ar) 70f else 525f, y, heading, right)
        y += 10f
        canvas.drawLine(margin, y, PAGE_WIDTH - margin, y, linePaint)
        y += 21f

        val rows = JSONArray(document.linesJson)
        for (i in 0 until rows.length()) {
            if (y > CONTENT_BOTTOM - 30f) startNextPage()
            val item = rows.getJSONObject(i)
            val description = item.optString("description")
            val sqm = item.optString("type") == "SQM"
            val quantity = if (sqm) item.optDouble("quantity") else 1.0
            val rateValue = if (sqm) item.optDouble("unitPrice") else item.optDouble("amount")
            val amount = if (sqm) quantity * rateValue else rateValue
            draw(description.take(48), x1, y, body, right)
            draw(if (sqm) number(quantity) else "1", if (ar) 205f else 390f, y, body, right)
            draw(money(rateValue), if (ar) 125f else 470f, y, body, right)
            draw(money(amount), if (ar) 70f else 525f, y, body, right)
            y += 24f
            canvas.drawLine(margin, y - 8f, PAGE_WIDTH - margin, y - 8f,
                Paint(linePaint).apply { alpha = 65; strokeWidth = .5f })
        }

        y += 12f
        if (y > CONTENT_BOTTOM - 110f) startNextPage()
        val summaryX = if (ar) PAGE_WIDTH - margin else margin
        draw((if (ar) "الإجمالي قبل الضريبة: " else "Subtotal: ") + money(document.subtotal) + " EGP",
            summaryX, y, body, ar); y += 21f
        draw((if (ar) "ضريبة القيمة المضافة (" else "VAT (") + number(document.vatRate) +
            "%): " + money(document.vatAmount) + " EGP", summaryX, y, body, ar); y += 25f
        draw((if (ar) "الإجمالي النهائي: " else "GRAND TOTAL: ") + money(document.total) + " EGP",
            summaryX, y, heading, ar); y += 30f
        if (document.notes.isNotBlank()) {
            if (y > CONTENT_BOTTOM - 35f) startNextPage()
            val noteX = if (ar) PAGE_WIDTH - margin else margin
            draw(if (ar) "ملاحظات:" else "Notes:", noteX, y, heading, ar)
            y += 19f
            draw(document.notes.take(180), noteX, y, body, ar)
        }
        draw((if (ar) "صفحة " else "Page ") + pageNumber, PAGE_WIDTH - margin, 785f, muted, true)

        pdf.finishPage(page)
        letterhead?.recycle()
        val filename = document.documentNumber.replace('/', '-') + "-" + language + ".pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: run { pdf.close(); error("تعذر إنشاء ملف PDF") }
        try {
            context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                ?: error("تعذر حفظ PDF")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } finally {
            pdf.close()
        }
        return uri
    }

    private fun loadLetterhead(context: Context): Bitmap? {
        val tempFile = File(context.cacheDir, "golden_shine_letterhead.pdf")
        return try {
            context.assets.open("letterhead/golden_shine_letterhead.pdf").use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { sourcePage ->
                        val bitmap = Bitmap.createBitmap(
                            sourcePage.width.coerceAtLeast(1),
                            sourcePage.height.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

    private fun money(value: Double) = NumberFormat.getNumberInstance(Locale.US)
        .apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)

    private fun number(value: Double) = NumberFormat.getNumberInstance(Locale.US).format(value)
}
