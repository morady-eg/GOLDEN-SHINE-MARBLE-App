package com.goldenshine.marble

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.goldenshine.marble.data.BusinessDocumentEntity
import com.goldenshine.marble.data.DocumentRepository
import com.goldenshine.marble.data.GoldenShineDatabase
import com.goldenshine.marble.domain.DocumentKind
import com.goldenshine.marble.pdf.DocumentPdfExporter
import kotlinx.coroutines.launch

private val Ink = Color(0xFF101114)
private val Panel = Color(0xFF1B1D21)
private val Gold = Color(0xFFD6AE52)
private val Muted = Color(0xFFB8B8B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldenShineApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoldenShineApp() {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(GoldenShineDatabase.get(context)) }
    var screen by remember { mutableStateOf("home") }
    var documents by remember { mutableStateOf<List<BusinessDocumentEntity>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) { documents = repository.all() }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, secondary = Gold, background = Ink, surface = Panel,
        onPrimary = Ink, onBackground = Color.White, onSurface = Color.White
    )) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Ink,
                topBar = {
                    TopAppBar(
                        title = { Text("GOLDEN SHINE MARBLE", color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Panel) {
                        NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Text("⌂") }, label = { Text("الرئيسية") })
                        NavigationBarItem(selected = screen == "list", onClick = { screen = "list"; refresh++ }, icon = { Text("▤") }, label = { Text("المستندات") })
                        NavigationBarItem(selected = screen == "new", onClick = { screen = "new" }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("مستند جديد") })
                    }
                }
            ) { padding ->
                when (screen) {
                    "new" -> DocumentForm(Modifier.padding(padding)) { kind, client, phone, title, desc, type, qty, unit, lump, vat, rate, notes ->
                        scope.launch {
                            runCatching { repository.create(kind, client, phone, title, desc, type, qty, unit, lump, vat, rate, notes) }
                                .onSuccess { Toast.makeText(context, "تم حفظ المستند بنجاح", Toast.LENGTH_LONG).show(); refresh++; screen = "list" }
                                .onFailure { Toast.makeText(context, it.message ?: "تعذر حفظ المستند", Toast.LENGTH_LONG).show() }
                        }
                    }
                    "list" -> DocumentList(
                        Modifier.padding(padding), documents,
                        onDelete = { id -> scope.launch { repository.delete(id); refresh++ } },
                        onPdf = { doc -> runCatching { DocumentPdfExporter.save(context, doc) }
                            .onSuccess { Toast.makeText(context, "تم حفظ $it في مجلد التنزيلات", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "تعذر إنشاء PDF", Toast.LENGTH_LONG).show() } }
                    )
                    else -> HomeScreen(Modifier.padding(padding), { screen = "new" }, { screen = "list" })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, onNew: () -> Unit, onList: () -> Unit) {
    Column(modifier.fillMaxSize().background(Ink).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("GOLDEN SHINE", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Text("MARBLE", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text("إدارة عروض الأسعار وعقود المقاولات", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("مرحباً بك", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("أنشئ مستنداتك واحفظها محلياً على جهازك", color = Muted, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("إنشاء مستند جديد") }
                OutlinedButton(onClick = onList, modifier = Modifier.fillMaxWidth()) { Text("فتح الأرشيف") }
            }
        }
        Text("• ترقيم مستقل لعروض الأسعار والعقود", color = Muted)
        Text("• تسعير بالمتر المربع أو بالمقطوعية", color = Muted)
        Text("• اختيار إضافة ضريبة القيمة المضافة", color = Muted)
        Text("• حفظ محلي وتصدير PDF", color = Muted)
    }
}

@Composable
private fun DocumentForm(
    modifier: Modifier,
    onSave: (DocumentKind, String, String, String, String, String, Double, Double, Double, Boolean, Double, String) -> Unit
) {
    var kind by remember { mutableStateOf(DocumentKind.QUOTATION) }
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("SQM") }
    var quantity by remember { mutableStateOf("1") }
    var unitPrice by remember { mutableStateOf("0") }
    var lumpSum by remember { mutableStateOf("0") }
    var addVat by remember { mutableStateOf(false) }
    var vatRate by remember { mutableStateOf("14") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("إنشاء مستند", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == DocumentKind.QUOTATION, onClick = { kind = DocumentKind.QUOTATION }, label = { Text("عرض سعر") })
                FilterChip(selected = kind == DocumentKind.CONTRACT, onClick = { kind = DocumentKind.CONTRACT }, label = { Text("عقد مقاولة") })
            }
        }
        item { FormField("اسم العميل *", client) { client = it } }
        item { FormField("رقم الهاتف", phone, KeyboardType.Phone) { phone = it } }
        item { FormField("عنوان المستند / المشروع", title) { title = it } }
        item { FormField("وصف بند العمل *", description) { description = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "SQM", onClick = { type = "SQM" }, label = { Text("بالمتر المربع") })
                FilterChip(selected = type == "LUMP", onClick = { type = "LUMP" }, label = { Text("مقطوعية") })
            }
        }
        if (type == "SQM") {
            item { FormField("الكمية بالمتر", quantity, KeyboardType.Decimal) { quantity = it } }
            item { FormField("سعر المتر", unitPrice, KeyboardType.Decimal) { unitPrice = it } }
        } else item { FormField("قيمة المقطوعية", lumpSum, KeyboardType.Decimal) { lumpSum = it } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addVat, onCheckedChange = { addVat = it })
                Text("إضافة ضريبة القيمة المضافة إلى الإجمالي", color = Color.White)
            }
        }
        if (addVat) item { FormField("نسبة الضريبة %", vatRate, KeyboardType.Decimal) { vatRate = it } }
        item { FormField("ملاحظات", notes) { notes = it } }
        item {
            Button(onClick = {
                val q = quantity.toDoubleOrNull() ?: 0.0
                val u = unitPrice.toDoubleOrNull() ?: 0.0
                val l = lumpSum.toDoubleOrNull() ?: 0.0
                val rate = vatRate.toDoubleOrNull() ?: 14.0
                onSave(kind, client, phone, title, description, type, q, u, l, addVat, rate, notes)
            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ المستند") }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = keyboard != KeyboardType.Text,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold, focusedLabelColor = Gold,
            unfocusedBorderColor = Muted, focusedTextColor = Color.White, unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun DocumentList(
    modifier: Modifier,
    documents: List<BusinessDocumentEntity>,
    onDelete: (Long) -> Unit,
    onPdf: (BusinessDocumentEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = documents.filter { it.clientName.contains(query, true) || it.documentNumber.contains(query, true) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("أرشيف المستندات", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, label = { Text("بحث بالعميل أو رقم المستند") }, modifier = Modifier.fillMaxWidth())
        if (shown.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد مستندات محفوظة", color = Muted) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { doc ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(doc.documentNumber, color = Gold, fontWeight = FontWeight.Bold)
                        Text(doc.clientName, color = Color.White, fontSize = 17.sp)
                        Text(doc.title, color = Muted)
                        Text("الإجمالي: %.2f ج.م".format(doc.total), color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onPdf(doc) }) { Icon(Icons.Default.PictureAsPdf, null); Text("تصدير PDF") }
                            TextButton(onClick = { onDelete(doc.id) }) { Icon(Icons.Default.Delete, null); Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
