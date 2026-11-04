package com.chagok.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagok.app.diary.DiaryEntry
import com.chagok.app.diary.DiaryWriter
import com.chagok.app.trip.AppClock
import com.chagok.app.trip.DayNight
import com.chagok.app.trip.Phase
import com.chagok.app.trip.TripRecorder
import com.chagok.app.vehicle.CarSnapshot

/** 첫 탭: 지금 주행 상태 / 도착하면 일기와 내리기 전 체크 */
@Composable
fun DriveScreen(recorder: TripRecorder, car: CarSnapshot, lastEntry: DiaryEntry?, destName: String?, wide: Boolean) {
    when (recorder.phase.value) {
        Phase.IDLE -> IdleView(lastEntry, destName)
        Phase.RECORDING -> RecordingView(recorder, car, destName)
        Phase.ARRIVED -> ArrivedView(recorder, car, wide)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleView(lastEntry: DiaryEntry?, destName: String?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("🚗", fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text("출발하면 차곡이 조용히 일기를 쓰기 시작해요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            destName?.let { "내비 목적지: $it" } ?: "운전 중에는 아무것도 누르지 않아도 됩니다.",
            fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Card {
            SectionTitle("차곡이 보는 것 (모두 차량 조회 API)")
            FlowRow {
                Chip("☔ 와이퍼 → 비")
                Chip("🕰 차량 시계 → 낮·밤")
                Chip("🚇 낮에 켜진 전조등 → 터널")
                Chip("🌡 실내 온도")
                Chip("🪟 창문")
                Chip("👨‍👩‍👧 뒷좌석 탑승")
                Chip("🛣 주행 거리·시간")
                Chip("🧭 내비 목적지·도착")
            }
        }
        if (lastEntry != null) {
            Spacer(Modifier.height(16.dp))
            Card {
                SectionTitle("지난 일기")
                Text(lastEntry.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(DiaryWriter.dateText(lastEntry.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecordingView(recorder: TripRecorder, car: CarSnapshot, destName: String?) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val a by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    val now = AppClock.now()
    val daylight = DayNight.isDaylight(now)

    // 운전 중이므로 글자는 크게, 정보는 최소로
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Spacer(Modifier.width(10.dp))
            Text("일기 쓰는 중", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(destName?.let { "$it 가는 길" } ?: "드라이브 중", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${DiaryWriter.durationText(recorder.liveMinutes.value)} · ${"%.1f".format(recorder.liveDistanceKm.value)}km",
            fontSize = 28.sp, color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Row {
            Chip(if (daylight) "☀ 낮 (${DiaryWriter.hhmm(now)})" else "🌙 밤 (${DiaryWriter.hhmm(now)})")
            if (car.wiperOn == true) Chip("☔ 비 오는 중")
            car.cabinTempC?.let { Chip("🌡 ${"%.1f".format(it)}℃") }
        }
        Spacer(Modifier.height(16.dp))
        recorder.liveMoments.value.takeLast(3).forEach { m ->
            Text(
                "${DiaryWriter.hhmm(m.at)}  ${m.icon} ${m.text}",
                fontSize = 20.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = recorder::finishNow) { Text("여기서 주행 마치기", fontSize = 16.sp) }
    }
}

@Composable
private fun ArrivedView(recorder: TripRecorder, car: CarSnapshot, wide: Boolean) {
    val entry = recorder.justWritten.value ?: return
    val diary: @Composable () -> Unit = {
        Card {
            SectionTitle("📔 오늘의 일기가 저장됐어요")
            DiaryBody(entry)
        }
    }
    val check: @Composable () -> Unit = {
        Card {
            SectionTitle("🚪 내리기 전 체크")
            ArrivalChecks.of(car).forEach { c ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(if (c.ok) "✓" else "!", color = if (c.ok) Ok else Warn, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(26.dp))
                    Text(c.text, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = recorder::dismissArrival, modifier = Modifier.fillMaxWidth()) {
                Text("확인했어요", fontSize = 17.sp)
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) { diary() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { check() }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            check(); Spacer(Modifier.height(16.dp)); diary()
        }
    }
}

/** 일기 본문 (제목·태그·글·타임라인) — 도착 화면과 일기장에서 같이 씀 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryBody(entry: DiaryEntry) {
    Text(DiaryWriter.dateText(entry.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    Spacer(Modifier.height(4.dp))
    Text(entry.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    FlowRow { entry.tags.forEach { Chip(it) } }
    Spacer(Modifier.height(8.dp))
    Text(entry.body, fontSize = 18.sp, lineHeight = 29.sp)
    Spacer(Modifier.height(18.dp))
    SectionTitle("타임라인")
    entry.moments.forEach { m ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(DiaryWriter.hhmm(m.at), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
                modifier = Modifier.width(56.dp))
            Text("${m.icon}  ${m.text}", fontSize = 16.sp)
        }
    }
}

/** 내리기 전 체크 항목 (도착 후 차 상태가 바뀌면 바로 다시 계산됨) */
object ArrivalChecks {
    data class Check(val ok: Boolean, val text: String)

    fun of(s: CarSnapshot): List<Check> {
        val list = mutableListOf<Check>()
        val n = s.rearOccupants ?: 0
        if (n > 0) list += Check(false, "뒷좌석에 ${n}명이 타고 있어요. 아이·반려동물을 꼭 함께 데리고 내리세요.")
        s.openWindows?.let {
            list += if (it.isEmpty()) Check(true, "창문이 모두 닫혀 있어요")
            else Check(false, "창문이 열려 있어요: ${it.joinToString()}")
        }
        if (s.headlightOn == true) list += Check(false, "전조등이 켜져 있어요")
        if (s.cabinLightOn == true) list += Check(false, "실내등이 켜져 있어요")
        s.energyPercent?.let { if (it < 15f) list += Check(false, "연료(배터리)가 ${it.toInt()}% 남았어요. 다음 출발 전에 채워 주세요.") }
        if (s.lowWasherFluid == true) list += Check(false, "워셔액이 부족해요")
        if (s.lowTirePressure == true) list += Check(false, "타이어 공기압이 낮아요")
        if (list.none { !it.ok }) list += Check(true, "모두 괜찮아요. 즐거운 시간 보내세요!")
        return list
    }
}
