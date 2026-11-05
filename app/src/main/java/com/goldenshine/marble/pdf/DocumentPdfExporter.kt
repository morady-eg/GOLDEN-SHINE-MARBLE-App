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
        if (document.kind == "QUOTATION") return saveQuotation(context, document, language)
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


    private fun saveQuotation(context: Context, document: BusinessDocumentEntity, language: String): Uri {
        val ar = language == "ar"
        val pdf = PdfDocument()
        val letterhead = loadLetterhead(context)
        val gold = android.graphics.Color.rgb(177, 137, 48)
        val ink = android.graphics.Color.rgb(28, 31, 35)
        val muted = android.graphics.Color.rgb(90, 94, 99)
        val margin = 38f
        val meta = runCatching { org.json.JSONObject(document.quotationMetaJson) }.getOrDefault(org.json.JSONObject())
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 10.5f }
        val bold = Paint(body).apply { textSize = 14f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val small = Paint(body).apply { textSize = 9f; color = muted }
        val goldLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; strokeWidth = 1.1f }
        var pageNo = 0
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNo).create())
        var canvas = page.canvas
        var y = CONTENT_TOP
        fun background() {
            if (letterhead != null) {
                val scale = minOf(PAGE_WIDTH.toFloat() / letterhead.width, PAGE_HEIGHT.toFloat() / letterhead.height)
                val w = letterhead.width * scale
                val h = letterhead.height * scale
                val left = (PAGE_WIDTH - w) / 2f
                val top = (PAGE_HEIGHT - h) / 2f
                canvas.drawBitmap(letterhead, null, RectF(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        }
        fun nextPage() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNo).create())
            canvas = page.canvas
            background()
            y = CONTENT_TOP
        }
        fun text(value: String, x: Float, baseline: Float, paint: Paint, align: Paint.Align = if (ar) Paint.Align.RIGHT else Paint.Align.LEFT) {
            paint.textAlign = align
            canvas.drawText(value, x, baseline, paint)
        }
        fun wrapped(value: String, x: Float, maxChars: Int = 82, paint: Paint = body) {
            val clean = value.trim()
            if (clean.isEmpty()) return
            var rest = clean
            while (rest.isNotEmpty()) {
                if (y > CONTENT_BOTTOM - 22f) nextPage()
                val cut = rest.take(maxChars).lastIndexOf(' ').let { if (it > 8) it else minOf(maxChars, rest.length) }
                val part = rest.take(cut).trim()
                text(part, x, y, paint)
                y += 17f
                rest = rest.drop(cut).trim()
            }
        }
        background()
        val center = PAGE_WIDTH / 2f
        text(if (ar) "عرض سعر" else "QUOTATION", center, y, bold, Paint.Align.CENTER)
        y += 27f
        text((if (ar) "رقم العرض: " else "Quotation No: ") + document.documentNumber, if (ar) PAGE_WIDTH-margin else margin, y, body)
        text((if (ar) "التاريخ: " else "Date: ") + java.text.SimpleDateFormat("dd/MM/yyyy", if (ar) Locale("ar") else Locale.US).format(java.util.Date(document.createdAt)), if (ar) margin else PAGE_WIDTH-margin, y, body)
        y += 24f
        val recipient = meta.optString("recipient", document.clientName)
        val attention = meta.optString("attention")
        text((if (ar) "السادة / " else "To: ") + recipient, if (ar) PAGE_WIDTH-margin else margin, y, body); y += 19f
        if (attention.isNotBlank()) { text((if (ar) "عناية / " else "Attn: ") + attention, if (ar) PAGE_WIDTH-margin else margin, y, body); y += 19f }
        text(if (ar) "تحية طيبة وبعد،" else "Dear Sir/Madam,", center, y, bold, Paint.Align.CENTER); y += 24f
        wrapped(if (ar) "نتشرف نحن شركة جولدن شاين ماربل بتقديم عرضنا المالي لسيادتكم بخصوص الأعمال الآتي ذكرها." else "Golden Shine Marble is pleased to submit our financial quotation for the following scope of work.", if (ar) PAGE_WIDTH-margin else margin)
        val scope = meta.optString("scope")
        if (scope.isNotBlank()) {
            y += 5f
            text(if (ar) "نطاق العمل" else "SCOPE OF WORK", if (ar) PAGE_WIDTH-margin else margin, y, bold); y += 20f
            scope.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { index, item ->
                wrapped("${index + 1}. $item", if (ar) PAGE_WIDTH-margin else margin)
            }
        }
        y += 8f
        if (y > CONTENT_BOTTOM - 90f) nextPage()
        text(if (ar) "العرض المالي" else "FINANCIAL OFFER", if (ar) PAGE_WIDTH-margin else margin, y, bold); y += 19f
        val left = margin
        val right = PAGE_WIDTH - margin
        val c1 = if (ar) right - 8f else left + 8f
        val c2 = if (ar) 300f else 300f
        val c3 = if (ar) left + 8f else right - 8f
        val tableTop = y - 13f
        canvas.drawRect(left, tableTop, right, tableTop + 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; alpha = 38; style = Paint.Style.FILL })
        text(if (ar) "نوع الخدمة" else "SERVICE", c1, y + 5f, bold)
        text(if (ar) "تكلفة الخدمة" else "COST", c2, y + 5f, bold, Paint.Align.CENTER)
        text(if (ar) "الملاحظات" else "NOTES", c3, y + 5f, bold, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT)
        y += 30f
        val rows = JSONArray(document.linesJson)
        val vatNote = if (document.vatTreatment == "ADD_TO_TOTAL") {
            if (ar) "السعر شامل ضريبة القيمة المضافة" else "Price includes VAT"
        } else {
            if (ar) "السعر المذكور لا يشمل ضريبة القيمة المضافة ويتحملها العميل وفقاً للقانون" else "VAT excluded; payable by the client as per law"
        }
        for (i in 0 until rows.length()) {
            if (y > CONTENT_BOTTOM - 45f) {
                nextPage()
                canvas.drawRect(left, y - 13f, right, y + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; alpha = 38 })
                text(if (ar) "نوع الخدمة" else "SERVICE", c1, y + 5f, bold)
                text(if (ar) "تكلفة الخدمة" else "COST", c2, y + 5f, bold, Paint.Align.CENTER)
                text(if (ar) "الملاحظات" else "NOTES", c3, y + 5f, bold, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT)
                y += 30f
            }
            val item = rows.getJSONObject(i)
            val sqm = item.optString("type") == "SQM"
            val amount = if (sqm) item.optDouble("quantity") * item.optDouble("unitPrice") else item.optDouble("amount")
            text(item.optString("description").take(34), c1, y, body)
            text(money(amount) + if (ar) " ج.م" else " EGP", c2, y, body, Paint.Align.CENTER)
            text(vatNote.take(33), c3, y, small, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT)
            y += 25f
            canvas.drawLine(left, y - 8f, right, y - 8f, Paint(goldLine).apply { alpha = 55; strokeWidth = .5f })
        }
        y += 8f
        if (y > CONTENT_BOTTOM - 130f) nextPage()
        val summaryX = if (ar) right else left
        text((if (ar) "الإجمالي قبل الضريبة: " else "Subtotal: ") + money(document.subtotal) + if (ar) " ج.م" else " EGP", summaryX, y, body); y += 19f
        text((if (ar) "ضريبة القيمة المضافة (" else "VAT (") + number(document.vatRate) + "%): " + money(document.vatAmount) + if (ar) " ج.م" else " EGP", summaryX, y, body); y += 22f
        text((if (ar) "الإجمالي النهائي: " else "GRAND TOTAL: ") + money(document.total) + if (ar) " جنيه مصري" else " EGP", summaryX, y, bold); y += 25f
        text(if (ar) "الشروط والأحكام العامة" else "GENERAL TERMS & CONDITIONS", summaryX, y, bold); y += 18f
        val terms = mutableListOf<String>()
        terms += if (ar) "1. السعر ${if (document.vatTreatment == "ADD_TO_TOTAL") "شامل" else "غير شامل"} ضريبة القيمة المضافة." else "1. VAT is ${if (document.vatTreatment == "ADD_TO_TOTAL") "included" else "excluded"} as stated above."
        terms += if (ar) "2. جميع التعاملات بالجنيه المصري." else "2. All transactions are in Egyptian Pounds (EGP)."
        terms += if (ar) "3. عرض السعر ساري لمدة ${meta.optInt("validityDays", 30).coerceAtLeast(1)} يوم من تاريخه." else "3. This quotation is valid for ${meta.optInt("validityDays", 30).coerceAtLeast(1)} days from its date."
        listOf("executionDuration" to if (ar) "مدة التنفيذ: " else "Execution: ",
            "paymentTerms" to if (ar) "شروط السداد: " else "Payment terms: ",
            "paymentMethod" to if (ar) "طريقة الدفع: " else "Payment method: ",
            "warranty" to if (ar) "الضمان: " else "Warranty: ",
            "exclusions" to if (ar) "الأعمال غير المشمولة: " else "Exclusions: ").forEach { (key, label) ->
            val value = meta.optString(key)
            if (value.isNotBlank()) terms += "${terms.size + 1}. $label$value"
        }
        terms.forEach { wrapped(it, summaryX, 88, body) }
        y += 8f
        if (y > CONTENT_BOTTOM - 70f) nextPage()
        text(if (ar) "وتفضلوا بقبول فائق الاحترام والتقدير" else "Yours faithfully,", center, y, body, Paint.Align.CENTER); y += 35f
        text(if (ar) "مدير عام الشركة" else "General Manager", if (ar) right else left, y, body)
        y += 18f
        text(if (ar) "م/ يوسف موسى" else "Eng. Youssef Mousa", if (ar) right else left, y, body)
        text((if (ar) "صفحة " else "Page ") + pageNo, right, 800f, small, Paint.Align.RIGHT)
        pdf.finishPage(page)
        letterhead?.recycle()
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
