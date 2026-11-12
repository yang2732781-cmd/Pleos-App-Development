package com.carpersona.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Pleos Connect 화면 스타일을 참고한 공통 틀.
 *  - Pleos UI 가이드: 주간/야간 모드 지원, 글자·아이콘 대비 4.5:1 이상, 한 손 조작, 1~2초 안에 읽히는 화면,
 *    앱 창은 가로·세로·화면 분할(앱 2개)에 모두 맞춰야 함.
 *  - 그래서: 넓은 창 = 왼쪽 세로 메뉴(레일), 좁은 창(세로 화면·분할) = 아래쪽 메뉴.
 *    메뉴 칸은 손가락으로 누르기 쉽게 72dp 이상, 본문 글자 16sp 이상.
 *  - 색 값은 Pleos 공식 토큰(이미지로만 공개)을 그대로 쓴 것이 아니라, 어두운 차량 화면 + 파란 강조색 느낌으로 맞춘 근사값입니다.
 */

private val Night = darkColorScheme(
    primary = Color(0xFF5B9BFF), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1D3866), onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF9EE6C9), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF23272E), onSecondaryContainer = Color(0xFFF2F4F7),
    background = Color(0xFF0A0B0D), onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF131519), onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1B1E23), onSurfaceVariant = Color(0xFFA3ABB7),
    surfaceContainerHigh = Color(0xFF2A2E35),
    outline = Color(0xFF5A616C), outlineVariant = Color(0xFF353A42),
    error = Color(0xFFFF7A7A), tertiary = Color(0xFFFFC857),
)

private val Day = lightColorScheme(
    primary = Color(0xFF1F63E0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Color(0xFF00775A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9ECF1), onSecondaryContainer = Color(0xFF111418),
    background = Color(0xFFEFF1F4), onBackground = Color(0xFF111418),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF545D69),
    surfaceContainerHigh = Color(0xFFE3E7ED),
    outline = Color(0xFF8A929E), outlineVariant = Color(0xFFD3D8DF),
    error = Color(0xFFC62828), tertiary = Color(0xFF9A5B00),
)

/** 차량의 주간/야간 모드(시스템 설정)를 그대로 따름 */
@Composable
fun PleosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Day, content = content)
}

/** 메뉴 한 칸 */
class NavItem(val key: String, val label: String, val icon: ImageVector, val lockedWhileDriving: Boolean = false)

/** 화면 위 상태 칩 하나 (예: 🚗 주행 중, 📍 경상북도 · 경상도 말투) */
class StatusChip(val text: String, val highlight: Boolean = false)

/**
 * 앱 전체 틀: 메뉴 + 위쪽 제목줄 + 본문.
 * 주행 중(driving)에는 lockedWhileDriving 메뉴의 본문을 숨기고 "정차 후 이용" 안내만 보여 줍니다.
 */
@Composable
fun PleosAppShell(
    title: String,
    subtitle: String,
    chips: List<StatusChip>,
    items: List<NavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    driving: Boolean,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val rail = maxWidth >= 720.dp
        val wide = maxWidth >= 900.dp
        val current = items.firstOrNull { it.key == selected } ?: items.first()
        val body: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                Header(title, subtitle, chips, compact = !rail)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (driving && current.lockedWhileDriving) DrivingLock(current.label) else content(wide)
                }
            }
        }
        if (rail) {
            Row(Modifier.fillMaxSize()) {
                NavRail(items, current.key, onSelect, driving)
                Box(Modifier.weight(1f).fillMaxHeight()) { body() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { body() }
                BottomNav(items, current.key, onSelect, driving)
            }
        }
    }
}

@Composable
private fun NavRail(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, driving: Boolean) {
    Column(
        Modifier.width(116.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("CP", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(8.dp))
        items.forEach { NavCell(it, it.key == selected, driving, Modifier.fillMaxWidth().heightIn(min = 84.dp)) { onSelect(it.key) } }
    }
}

@Composable
private fun BottomNav(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, driving: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { NavCell(it, it.key == selected, driving, Modifier.width(92.dp).heightIn(min = 72.dp)) { onSelect(it.key) } }
    }
}

@Composable
private fun NavCell(item: NavItem, selected: Boolean, driving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dim = driving && item.lockedWhileDriving
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        dim -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = fg, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(item.label, fontSize = 15.sp, color = fg, textAlign = TextAlign.Center, maxLines = 2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Header(title: String, subtitle: String, chips: List<StatusChip>, compact: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = if (compact) 24.sp else 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(12.dp))
            Text(subtitle, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { c ->
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (c.highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(c.text, fontSize = 16.sp,
                        color = if (c.highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** 주행 중 잠금 화면: 본문 대신 짧은 안내만 */
@Composable
private fun DrivingLock(label: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🚗", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("'$label' 화면은 정차 후에 볼 수 있어요", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("운전 중에는 '대화'에서 🎙 버튼으로 말해 주세요.", fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Pleos 스타일 큰 버튼 (높이 64dp, 둥근 모서리) */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, height: Dp = 64.dp, onClick: () -> Unit) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.outline
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier.heightIn(min = height).clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1) }
}
