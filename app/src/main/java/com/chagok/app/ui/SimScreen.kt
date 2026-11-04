package com.chagok.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagok.app.diary.DiaryWriter
import com.chagok.app.trip.AppClock
import com.chagok.app.trip.DayNight
import com.chagok.app.vehicle.CarSnapshot
import com.chagok.app.vehicle.FakeCar

/**
 * 시뮬레이터 탭 (가짜 차일 때만 보임).
 * 버튼을 눌러 비·터널·도착 같은 상황을 만들어 앱이 어떻게 반응하는지 시험합니다.
 * 차 안 시계는 1초에 1분씩 흐릅니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimScreen(fake: FakeCar, car: CarSnapshot) {
    val now = AppClock.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            Text(
                "차 안 시계 ${DiaryWriter.hhmm(now)} (${if (DayNight.isDaylight(now)) "낮" else "밤"})  ·  " +
                    "${fake.speedKmh.value}km/h  ·  ${if (fake.driving.value) "주행 중" else "정차"}",
                fontSize = 18.sp,
            )
            Text("현실 1초 = 차 안 1분", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = fake::playDemoScenario, modifier = Modifier.fillMaxWidth()) {
                Text("▶ 자동 시연 (약 2분 40초: 출발 → 비 → 산길 멀미 주의 → 휴게소 → 해 짐 → 정체 → 도착)", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("1. 출발 (내비 목적지 정하기)")
            FlowRow {
                fake.places.forEach { p -> SimButton(p.name) { fake.depart(p) } }
            }
            SimButton("🏁 도착 (내비 도착 알림)", primary = true) { fake.arrive() }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("2. 길 위에서")
            FlowRow {
                SimButton(if (car.wiperOn == true) "☔ 비 그치게" else "☔ 비 오게") { fake.setRain(car.wiperOn != true) }
                SimButton("🚇 터널 지나기") { fake.enterTunnel() }
                SimButton(if (car.headlightOn == true) "전조등 끄기" else "전조등 켜기") { fake.setHeadlight(car.headlightOn != true) }
                SimButton(if (car.openWindows.orEmpty().isEmpty()) "🪟 창문 열기" else "🪟 창문 닫기") { fake.toggleWindow() }
                SimButton(if (fake.curvy.value) "🌀 굽잇길 끝" else "🌀 굽잇길 시작") { fake.setCurvy(!fake.curvy.value) }
                SimButton(if (fake.jam.value) "🚦 정체 끝" else "🚦 정체 시작") { fake.setJam(!fake.jam.value) }
                SimButton(if (car.recirculationOn == true) "🌬 외기로 바꾸기" else "🌬 내기순환 켜기") { fake.setRecirculation(car.recirculationOn != true) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("3. 차 안")
            FlowRow {
                SimButton("뒷좌석 −") { fake.setRearOccupants((car.rearOccupants ?: 0) - 1) }
                SimButton("뒷좌석 + (지금 ${car.rearOccupants ?: 0}명)") { fake.setRearOccupants((car.rearOccupants ?: 0) + 1) }
                SimButton(if (car.cabinLightOn == true) "💡 실내등 끄기" else "💡 실내등 켜기") { fake.setCabinLight(car.cabinLightOn != true) }
                SimButton(if (car.lowWasherFluid == true) "워셔액 채우기" else "워셔액 부족") { fake.setLowWasher(car.lowWasherFluid != true) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("4. 차 안 시계 맞추기 (낮/밤은 시계로 판단)")
            FlowRow {
                SimButton("지금 시각") { fake.setClock(null) }
                SimButton("오전 10시") { fake.setClock(10) }
                SimButton("오후 4시") { fake.setClock(16) }
                SimButton("밤 9시") { fake.setClock(21) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SimButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val m = Modifier.padding(end = 8.dp, bottom = 8.dp)
    if (primary) Button(onClick = onClick, modifier = m) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    else OutlinedButton(onClick = onClick, modifier = m) { Text(text, fontSize = 15.sp) }
}
