package com.chagok.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagok.app.diary.DiaryEntry
import com.chagok.app.diary.DiaryStore
import com.chagok.app.diary.DiaryWriter
import com.chagok.app.navi.NaviSource

/** 일기장 탭. 운전 중에는 잠깁니다 (주행 중 화면 보기 최소화). */
@Composable
fun DiaryScreen(store: DiaryStore, navi: NaviSource, driving: Boolean) {
    if (driving) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("🔒", fontSize = 40.sp)
            Text("운전 중에는 일기장을 볼 수 없어요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("차를 세우면 다시 열려요.", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var opened by remember { mutableStateOf<DiaryEntry?>(null) }
    val current = opened
    if (current != null) {
        DiaryDetail(current, navi, onBack = { opened = null }, onDelete = { store.delete(current); opened = null })
        return
    }
    if (store.entries.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("📔", fontSize = 40.sp)
            Text("아직 일기가 없어요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("한 번 달리고 나면 차가 첫 일기를 써 둘게요.", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(store.entries, key = { it.id }) { e ->
            Card(Modifier.clickable { opened = e }) {
                Text(DiaryWriter.dateText(e.startedAt), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(e.title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(e.body, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(e.tags.joinToString("   "), fontSize = 14.sp)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DiaryDetail(e: DiaryEntry, navi: NaviSource, onBack: () -> Unit, onDelete: () -> Unit) {
    var routeMsg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 일기 목록", fontSize = 16.sp) }
        Card { DiaryBody(e) }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dest = e.destination
            if (dest != null) {
                Button(onClick = {
                    routeMsg = if (navi.routeTo(dest)) "내비에 '${dest.name}' 길 안내를 요청했어요."
                    else "내비 연결이 없어 길 안내를 요청하지 못했어요."
                }) { Text("📍 ${dest.name} 다시 가기", fontSize = 16.sp) }
                Spacer(Modifier.padding(6.dp))
            }
            OutlinedButton(onClick = onDelete) { Text("삭제", fontSize = 16.sp) }
        }
        routeMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
