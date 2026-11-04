package com.chagok.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagok.app.vehicle.CarSnapshot

/**
 * 차량 데이터 탭: 앱이 지금 읽고 있는 조회 API 값을 그대로 보여 줍니다.
 * 심사·시연 때 "어떤 Pleos API를 쓰는지" 보여 주는 화면이자, Pleos 에뮬레이터에서 값이 잘 들어오는지 확인하는 화면입니다.
 */
@Composable
fun CarDataScreen(
    car: CarSnapshot,
    sourceLabel: String,
    isReal: Boolean,
    status: String?,
    onUseFake: () -> Unit,
    onTryPleos: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            SectionTitle("데이터 출처")
            Text(sourceLabel, fontSize = 22.sp)
            status?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                if (isReal) OutlinedButton(onClick = onUseFake) { Text("가짜 차로 바꾸기") }
                else OutlinedButton(onClick = onTryPleos) { Text("Pleos 차량 다시 연결") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card {
            SectionTitle("읽고 있는 값 (Vehicle SDK 조회 API)")
            if (car.raw.isEmpty()) Text("아직 받은 값이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            car.raw.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text(k, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(v, fontSize = 15.sp, modifier = Modifier.weight(0.8f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card {
            SectionTitle("앱이 해석한 뜻")
            Text("와이퍼: ${on(car.wiperOn, "작동(비)", "멈춤")}")
            Text("전조등: ${on(car.headlightOn, "켜짐", "꺼짐")}   실내등: ${on(car.cabinLightOn, "켜짐", "꺼짐")}")
            Text("실내 온도: ${car.cabinTempC?.let { "%.1f℃".format(it) } ?: "-"}")
            Text("열린 창문: ${car.openWindows?.ifEmpty { listOf("없음") }?.joinToString() ?: "-"}")
            Text("열린 문: ${car.openDoors?.ifEmpty { listOf("없음") }?.joinToString() ?: "-"}")
            Text("뒷좌석 탑승: ${car.rearOccupants?.let { "${it}명" } ?: "-"}")
            Text("이번 주행: ${car.tripDistanceKm?.let { "%.1fkm".format(it) } ?: "-"} / ${car.tripTimeMin?.let { "${it.toInt()}분" } ?: "-"}")
        }
    }
}

private fun on(v: Boolean?, yes: String, no: String) = when (v) { true -> yes; false -> no; null -> "-" }
