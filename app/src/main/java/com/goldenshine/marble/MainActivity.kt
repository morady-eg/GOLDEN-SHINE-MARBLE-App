package com.goldenshine.marble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider

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

@Composable
private fun GoldenShineApp() {
    var selected by remember { mutableIntStateOf(0) }
    val labels = listOf("الرئيسية", "المستندات", "الأرشيف", "المزيد")
    MaterialTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Ink,
                bottomBar = {
                    NavigationBar(containerColor = Panel, contentColor = Gold) {
                        val icons = listOf(Icons.Default.Home, Icons.Default.Description, Icons.Default.Archive, Icons.Default.MoreHoriz)
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selected = index },
                                icon = { androidx.compose.material3.Icon(icons[index], contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { padding ->
                when (selected) {
                    0 -> HomeScreen(Modifier.padding(padding))
                    1 -> SimpleSection("المستندات", "إدارة عروض الأسعار وعقود المقاولات")
                    2 -> SimpleSection("الأرشيف", "ستظهر هنا المستندات المحفوظة")
                    else -> SimpleSection("المزيد", "إعدادات الشركة والضريبة والنسخ الاحتياطي")
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Ink).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("GOLDEN SHINE", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("MARBLE", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("إدارة أعمال الرخام باحترافية", color = Muted, fontSize = 14.sp)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("مرحباً بك", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("منظومة إدارة عروض الأسعار وعقود المقاولات", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Text("الإصدار 1.0.0", color = Gold, fontSize = 12.sp)
                }
            }
        }
        item { SectionTile("عروض الأسعار", "إنشاء وتسعير عروض العملاء") }
        item { SectionTile("عقود المقاولات", "إعداد العقود ومتابعة قيمتها") }
        item { SectionTile("الأرشيف", "البحث في المستندات السابقة") }
        item {
            Text("ملاحظة: يتم تجهيز محرك التسعير والضريبة أولاً، ثم ربط المستندات بالحفظ الدائم وتصدير PDF.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTile(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Muted, fontSize = 13.sp)
            }
            Text("‹", color = Gold, fontSize = 28.sp)
        }
    }
}

@Composable
private fun SimpleSection(title: String, description: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Muted)
        }
    }
}
