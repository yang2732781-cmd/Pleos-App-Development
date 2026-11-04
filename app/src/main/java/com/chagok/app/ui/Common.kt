package com.chagok.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 차 화면에 어울리는 어두운 색 */
val ChagokColors = darkColorScheme(
    primary = Color(0xFFFFB86B),        // 노을빛 주황
    onPrimary = Color(0xFF2A1700),
    secondary = Color(0xFF8EC5FF),
    background = Color(0xFF0F1216),
    surface = Color(0xFF0F1216),
    surfaceVariant = Color(0xFF1C2128),
    onSurface = Color(0xFFECEFF3),
    onSurfaceVariant = Color(0xFFA9B1BC),
    error = Color(0xFFFF7A7A),
)

val Ok = Color(0xFF7DDC9A)
val Warn = Color(0xFFFFC857)

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
fun Chip(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) { Text(text, fontSize = 15.sp, color = color) }
}
