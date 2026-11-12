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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 *  - 화면 구성 요소는 가이드의 Page Navigation(위쪽 줄)·Tabs(Box 형)·Toast 를 따름.
 *    세로 창(앱 1칸)에서는 핵심 기능 위주, 가로 창(2칸)에서는 탭을 제목 옆에 두고 정보를 나란히.
 *    누르는 칸은 56dp 이상, 본문 글자 16sp 이상.
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
    inverseSurface = Color(0xFFF2F4F7), inverseOnSurface = Color(0xFF111418),
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
    inverseSurface = Color(0xFF23272E), inverseOnSurface = Color(0xFFF2F4F7),
)

/** 차량의 주간/야간 모드(시스템 설정)를 그대로 따름 */
@Composable
fun PleosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Day, content = content)
}

/**
 * 메뉴 한 칸.
 * @param suffix false = 위쪽 탭(자주 쓰는 화면), true = 오른쪽 아이콘으로 여는 화면(뒤로 가기로 닫음)
 */
class NavItem(val key: String, val label: String, val icon: ImageVector, val lockedWhileDriving: Boolean = false, val suffix: Boolean = false)

/** 화면 위 상태 칩 하나 (예: 🚗 주행 중, 📍 경상북도 · 경상도 말투) */
class StatusChip(val text: String, val highlight: Boolean = false)

/**
 * 앱 전체 틀. Pleos Connect 디자인 가이드의 구성 요소를 따릅니다.
 *  - Page Navigation: 위쪽 줄 = 앱 아이콘 + 제목 + (탭) + 오른쪽 아이콘(Suffix).
 *    오른쪽 아이콘으로 들어간 화면은 "Depth" 상태가 되어 ← 뒤로 가기 버튼이 나옵니다.
 *  - Tabs (Box 형): 자주 쓰는 화면끼리 전환. 넓으면 제목 옆에, 좁으면 제목 아래에 폭을 채워서(Fitted).
 *  - Toast: 앱 창 아래쪽, 옆·아래 여백 24, 최대 폭 792.
 * 가이드: 자주 쓰는 기능은 첫 화면·위쪽에, 여러 단계 메뉴와 드롭다운은 줄이기 → 탭 3개 + 아이콘 3개, 깊이는 한 단계만.
 * 주행 중(driving)에는 lockedWhileDriving 화면의 본문을 숨기고 "정차 후 이용" 안내만 보여 줍니다.
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
    toast: String?,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val roomy = maxWidth >= 840.dp     // 가로 화면(창 2칸): 탭을 제목 옆에
        val wide = maxWidth >= 900.dp
        val tabs = items.filter { !it.suffix }
        val suffix = items.filter { it.suffix }
        val current = items.firstOrNull { it.key == selected } ?: items.first()
        Column(Modifier.fillMaxSize()) {
            PageNavigation(title, subtitle, tabs, suffix, current, onSelect, driving, roomy, home = tabs.first().key)
            if (!current.suffix && !roomy) {
                TabsBox(tabs, current.key, onSelect, driving, fitted = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp))
            }
            ChipRow(chips)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (driving && current.lockedWhileDriving) DrivingLock(current.label) else content(wide)
            }
        }
        if (toast != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = 24.dp)) { ToastBox(toast) }
        }
    }
}

/** Pleos "Page Navigation": 앱 아이콘(또는 ← 뒤로) + 제목 + 탭 + 오른쪽 아이콘 */
@Composable
private fun PageNavigation(
    title: String, subtitle: String, tabs: List<NavItem>, suffix: List<NavItem>, current: NavItem,
    onSelect: (String) -> Unit, driving: Boolean, roomy: Boolean, home: String,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current.suffix) {
            // Depth 상태: 뒤로 가기 + 화면 이름
            IconCell(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", selected = false, dim = false) { onSelect(home) }
            Spacer(Modifier.width(16.dp))
            Text(current.label, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
        } else {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("🚘", fontSize = 28.sp) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (roomy) {
                TabsBox(tabs, current.key, onSelect, driving, fitted = false)
                Spacer(Modifier.width(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suffix.forEach { IconCell(it.icon, it.label, selected = false, dim = driving && it.lockedWhileDriving) { onSelect(it.key) } }
            }
        }
    }
}

/** 오른쪽 아이콘 버튼 (누르는 칸 64dp) */
@Composable
private fun IconCell(icon: ImageVector, label: String, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp),
            tint = if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
    }
}

/** Pleos "Tabs" Box 형: 둥근 통 안에서 고른 칸만 채워짐 */
@Composable
private fun TabsBox(tabs: List<NavItem>, selected: String, onSelect: (String) -> Unit, driving: Boolean, fitted: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { t ->
            val on = t.key == selected
            val dim = driving && t.lockedWhileDriving
            Row(
                (if (fitted) Modifier.weight(1f) else Modifier).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(t.key) }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                val fg = when {
                    on -> MaterialTheme.colorScheme.onPrimary
                    dim -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Icon(t.icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.label, fontSize = 18.sp, color = fg, maxLines = 1, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ChipRow(chips: List<StatusChip>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { c ->
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (c.highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(c.text, fontSize = 16.sp,
                    color = if (c.highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Pleos "Toast": 최대 폭 792, 글자 최대 폭 712, 세 줄까지 */
@Composable
private fun ToastBox(text: String) {
    Box(
        Modifier.widthIn(max = 792.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 40.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 18.sp, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 3,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 712.dp))
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
        Text("운전 중에는 ← 뒤로 가서 '대화'의 🎙 버튼으로 말해 주세요.", fontSize = 18.sp,
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
