package com.goldenshine.marble.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.provider.MediaStore
import com.goldenshine.marble.data.BusinessDocumentEntity
import java.text.NumberFormat
import java.util.Locale

object DocumentPdfExporter {
    fun save(context: Context, document: BusinessDocumentEntity): String {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas
        val gold = android.graphics.Color.rgb(185, 145, 55)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 13f }
        val heading = Paint(paint).apply { color = gold; textSize = 23f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("GOLDEN SHINE MARBLE", 40f, 55f, heading)
        canvas.drawText(if (document.kind == "CONTRACT") "CONTRACT" else "QUOTATION", 40f, 92f, Paint(heading).apply { textSize = 18f })
        canvas.drawText(document.documentNumber, 40f, 125f, paint)
        canvas.drawText("Client: ${document.clientName}", 40f, 170f, paint)
        canvas.drawText("Phone: ${document.clientPhone}", 40f, 195f, paint)
        canvas.drawText("Description: ${document.title}", 40f, 230f, paint)
        canvas.drawText("Subtotal: ${money(document.subtotal)} EGP", 40f, 290f, paint)
        canvas.drawText("VAT (${document.vatRate}%): ${money(document.vatAmount)} EGP", 40f, 320f, paint)
        canvas.drawText("TOTAL: ${money(document.total)} EGP", 40f, 360f, Paint(heading).apply { textSize = 19f })
        if (document.notes.isNotBlank()) canvas.drawText("Notes: ${document.notes.take(90)}", 40f, 410f, paint)
        canvas.drawText("GOLDEN SHINE MARBLE", 40f, 790f, Paint(paint).apply { color = gold; typeface = Typeface.DEFAULT_BOLD })
        pdf.finishPage(page)
        val filename = "${document.documentNumber.replace('/', '-')}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: run { pdf.close(); error("تعذر إنشاء ملف PDF") }
        context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) } ?: error("تعذر حفظ PDF")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        pdf.close()
        return filename
    }

    private fun money(value: Double) = NumberFormat.getNumberInstance(Locale.US).format(value)
}
