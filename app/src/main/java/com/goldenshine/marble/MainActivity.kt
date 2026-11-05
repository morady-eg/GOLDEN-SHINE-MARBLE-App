package com.goldenshine.marble

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.goldenshine.marble.data.*
import com.goldenshine.marble.domain.DocumentKind
import com.goldenshine.marble.pdf.DocumentPdfExporter
import kotlinx.coroutines.launch
import org.json.JSONArray

private val Ink = Color(0xFF101114)
private val Panel = Color(0xFF1B1D21)
private val Gold = Color(0xFFD6AE52)
private val Muted = Color(0xFFB8B8B8)

private data class LineDraft(var type: String = "SQM", var description: String = "", var quantity: String = "1", var unitPrice: String = "0", var amount: String = "0") {
    fun input() = DocumentLineInput(description, type, quantity.toDoubleOrNull() ?: 0.0, unitPrice.toDoubleOrNull() ?: 0.0, amount.toDoubleOrNull() ?: 0.0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { GoldenShineApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoldenShineApp() {
    val context = LocalContext.current
    val repo = remember { DocumentRepository(GoldenShineDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("home") }
    var docs by remember { mutableStateOf<List<BusinessDocumentEntity>>(emptyList()) }
    var editing by remember { mutableStateOf<BusinessDocumentEntity?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var backupText by remember { mutableStateOf<String?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write((backupText ?: "").toByteArray(Charsets.UTF_8)) } ?: error("تعذر فتح الملف")
        }.onSuccess { Toast.makeText(context, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(context, it.message ?: "فشل حفظ النسخة", Toast.LENGTH_LONG).show() }
        backupText = null
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("تعذر قراءة الملف")
                repo.restoreBackup(json)
            }.onSuccess { count -> refresh++; Toast.makeText(context, "تمت استعادة " + count + " مستند", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, it.message ?: "ملف النسخة غير صالح", Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(refresh) { docs = repo.all() }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, secondary = Gold, background = Ink, surface = Panel, onPrimary = Ink, onBackground = Color.White, onSurface = Color.White)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(containerColor = Ink, topBar = {
                TopAppBar(title = { Text("GOLDEN SHINE MARBLE", color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink))
            }, bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(screen == "home", { screen = "home" }, icon = { Text("⌂") }, label = { Text("الرئيسية") })
                    NavigationBarItem(screen == "list", { screen = "list"; refresh++ }, icon = { Text("▤") }, label = { Text("المستندات") })
                    NavigationBarItem(screen == "form", { editing = null; screen = "form" }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("مستند جديد") })
                }
            }) { pad ->
                when (screen) {
                    "form" -> DocumentForm(Modifier.padding(pad), editing) { id, kind, client, phone, title, lines, vat, rate, notes ->
                        scope.launch {
                            runCatching { repo.save(id, kind, client, phone, title, lines, vat, rate, notes) }
                                .onSuccess { editing = null; refresh++; screen = "list"; Toast.makeText(context, "تم حفظ المستند", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "تعذر الحفظ", Toast.LENGTH_LONG).show() }
                        }
                    }
                    "list" -> DocumentList(Modifier.padding(pad), docs,
                        onEdit = { editing = it; screen = "form" },
                        onDelete = { scope.launch { repo.delete(it); refresh++ } },
                        onPdf = { doc, lang -> runCatching { DocumentPdfExporter.save(context, doc, lang) }
                            .onSuccess { Toast.makeText(context, "تم حفظ " + it + " في التنزيلات", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "تعذر إنشاء PDF", Toast.LENGTH_LONG).show() } })
                    else -> HomeScreen(Modifier.padding(pad),
                        onNew = { editing = null; screen = "form" }, onList = { screen = "list"; refresh++ },
                        onBackup = { scope.launch { runCatching { repo.exportBackup() }.onSuccess { backupText = it; backupPicker.launch("GoldenShine-Backup.json") }.onFailure { Toast.makeText(context, it.message ?: "تعذر تجهيز النسخة", Toast.LENGTH_LONG).show() } } },
                        onRestore = { restorePicker.launch(arrayOf("application/json", "text/json", "text/plain")) })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, onNew: () -> Unit, onList: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GOLDEN SHINE", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Text("MARBLE", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text("إدارة عروض الأسعار وعقود المقاولات", color = Muted)
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("إنشاء مستند جديد") }
        OutlinedButton(onClick = onList, modifier = Modifier.fillMaxWidth()) { Text("فتح الأرشيف") }
        Divider(color = Gold.copy(alpha = .4f))
        Text("• بنود متعددة بالمتر المربع أو بالمقطوعية", color = Muted)
        Text("• تعديل المستندات مع الاحتفاظ برقمها", color = Muted)
        Text("• PDF عربي أو إنجليزي", color = Muted)
        OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("إنشاء نسخة احتياطية") }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("استعادة نسخة احتياطية") }
        Text("تحذير: الاستعادة تستبدل الأرشيف الحالي بالكامل.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DocumentForm(modifier: Modifier, doc: BusinessDocumentEntity?, save: (Long?, DocumentKind, String, String, String, List<DocumentLineInput>, Boolean, Double, String) -> Unit) {
    var kind by remember(doc?.id) { mutableStateOf(if (doc?.kind == "CONTRACT") DocumentKind.CONTRACT else DocumentKind.QUOTATION) }
    var client by remember(doc?.id) { mutableStateOf(doc?.clientName ?: "") }
    var phone by remember(doc?.id) { mutableStateOf(doc?.clientPhone ?: "") }
    var title by remember(doc?.id) { mutableStateOf(doc?.title ?: "") }
    var vatOn by remember(doc?.id) { mutableStateOf(doc?.vatTreatment == "ADD_TO_TOTAL") }
    var vatRate by remember(doc?.id) { mutableStateOf((doc?.vatRate ?: 14.0).toString()) }
    var notes by remember(doc?.id) { mutableStateOf(doc?.notes ?: "") }
    val lines = remember(doc?.id) {
        mutableStateListOf<LineDraft>().apply {
            if (doc == null) add(LineDraft()) else {
                val a = JSONArray(doc.linesJson)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(LineDraft(o.optString("type", "SQM"), o.optString("description"), o.optString("quantity", "1"), o.optString("unitPrice", "0"), o.optString("amount", "0")))
                }
                if (isEmpty()) add(LineDraft())
            }
        }
    }
    val subtotal = lines.sumOf { if (it.type == "SQM") (it.quantity.toDoubleOrNull() ?: 0.0) * (it.unitPrice.toDoubleOrNull() ?: 0.0) else it.amount.toDoubleOrNull() ?: 0.0 }
    val rate = vatRate.toDoubleOrNull() ?: 0.0
    val vat = if (vatOn) subtotal * rate / 100.0 else 0.0
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { Text(if (doc == null) "إنشاء مستند" else "تعديل " + doc.documentNumber, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == DocumentKind.QUOTATION, { kind = DocumentKind.QUOTATION }, label = { Text("عرض سعر") })
            FilterChip(kind == DocumentKind.CONTRACT, { kind = DocumentKind.CONTRACT }, label = { Text("عقد مقاولة") })
        } }
        item { Field("اسم العميل *", client) { client = it } }
        item { Field("رقم الهاتف", phone, KeyboardType.Phone) { phone = it } }
        item { Field("عنوان المستند / المشروع", title) { title = it } }
        item { Text("بنود الأعمال", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        itemsIndexed(lines) { index, line ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("بند رقم " + (index + 1), color = Gold, modifier = Modifier.weight(1f))
                        if (lines.size > 1) IconButton(onClick = { lines.removeAt(index) }) { Icon(Icons.Default.Delete, "حذف", tint = Color.Red) }
                    }
                    Field("وصف بند العمل *", line.description) { line.description = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(line.type == "SQM", { line.type = "SQM" }, label = { Text("بالمتر المربع") })
                        FilterChip(line.type == "LUMP", { line.type = "LUMP" }, label = { Text("مقطوعية") })
                    }
                    if (line.type == "SQM") {
                        Field("الكمية بالمتر", line.quantity, KeyboardType.Decimal) { line.quantity = it }
                        Field("سعر المتر", line.unitPrice, KeyboardType.Decimal) { line.unitPrice = it }
                        Text("قيمة البند: %.2f ج.م".format((line.quantity.toDoubleOrNull() ?: 0.0) * (line.unitPrice.toDoubleOrNull() ?: 0.0)), color = Muted)
                    } else Field("قيمة المقطوعية", line.amount, KeyboardType.Decimal) { line.amount = it }
                }
            }
        }
        item { OutlinedButton(onClick = { lines.add(LineDraft()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("إضافة بند آخر") } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(vatOn, { vatOn = it }); Text("إضافة ضريبة القيمة المضافة", color = Color.White) } }
        if (vatOn) item { Field("نسبة الضريبة %", vatRate, KeyboardType.Decimal) { vatRate = it } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("الإجمالي قبل الضريبة: %.2f ج.م".format(subtotal), color = Color.White)
                Text("الضريبة: %.2f ج.م".format(vat), color = Muted)
                Text("الإجمالي النهائي: %.2f ج.م".format(subtotal + vat), color = Gold, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
        } }
        item { Field("ملاحظات", notes) { notes = it } }
        item { Button(onClick = { save(doc?.id, kind, client, phone, title, lines.map { it.input() }, vatOn, rate, notes) }, modifier = Modifier.fillMaxWidth()) { Text(if (doc == null) "حفظ المستند" else "حفظ التعديلات") } }
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = keyboard != KeyboardType.Text,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, focusedLabelColor = Gold, unfocusedBorderColor = Muted, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
}

@Composable
private fun DocumentList(modifier: Modifier, docs: List<BusinessDocumentEntity>, onEdit: (BusinessDocumentEntity) -> Unit, onDelete: (Long) -> Unit, onPdf: (BusinessDocumentEntity, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = docs.filter { it.clientName.contains(query, true) || it.documentNumber.contains(query, true) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("أرشيف المستندات", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, label = { Text("بحث بالعميل أو رقم المستند") }, modifier = Modifier.fillMaxWidth())
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد مستندات محفوظة", color = Muted) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            itemsIndexed(filtered, key = { _, d -> d.id }) { _, d ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(d.documentNumber, color = Gold, fontWeight = FontWeight.Bold)
                        Text(d.clientName, color = Color.White, fontSize = 17.sp)
                        Text(d.title, color = Muted)
                        Text("عدد البنود: " + runCatching { JSONArray(d.linesJson).length() }.getOrDefault(0), color = Muted)
                        Text("الإجمالي: %.2f ج.م".format(d.total), color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            TextButton(onClick = { onEdit(d) }) { Text("تعديل") }
                            TextButton(onClick = { onPdf(d, "ar") }) { Icon(Icons.Default.PictureAsPdf, null); Text("PDF عربي") }
                            TextButton(onClick = { onPdf(d, "en") }) { Text("English PDF") }
                            TextButton(onClick = { onDelete(d.id) }) { Icon(Icons.Default.Delete, null); Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
