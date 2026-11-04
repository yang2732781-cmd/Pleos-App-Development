package com.chagok.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chagok.app.diary.DiaryStore
import com.chagok.app.diary.DiaryWriter
import com.chagok.app.navi.NaviSource
import com.chagok.app.navi.PleosNavi
import com.chagok.app.trip.AppClock
import com.chagok.app.trip.Phase
import com.chagok.app.trip.TripRecorder
import com.chagok.app.ui.CarDataScreen
import com.chagok.app.ui.ChagokColors
import com.chagok.app.ui.DiaryScreen
import com.chagok.app.ui.DriveScreen
import com.chagok.app.ui.SimScreen
import com.chagok.app.vehicle.CarSource
import com.chagok.app.vehicle.FakeCar
import com.chagok.app.vehicle.PleosCar

/**
 * 앱의 시작점.
 *
 * 1) Pleos SDK가 APK 안에 있고 차량 서비스에 연결되면 → 진짜 차(PleosCar + PleosNavi)
 * 2) 아니면(일반 폰, 일반 에뮬레이터) → 가짜 차(FakeCar, 시뮬레이터 탭 생김)
 * 진짜 차에 연결됐는데 6초 동안 값이 하나도 안 오면 자동으로 가짜 차로 바꿉니다.
 */
class MainActivity : ComponentActivity() {

    /** 지금 쓰는 부품 묶음 */
    private class Setup(val car: CarSource, val navi: NaviSource, val recorder: TripRecorder, val fake: FakeCar?)

    private lateinit var store: DiaryStore
    private val setup = mutableStateOf<Setup?>(null)
    private val status = mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DiaryStore(this)
        if (!tryPleos()) useFake("Pleos 차량 서비스가 없어 가짜 차로 실행 중이에요.")

        setContent {
            MaterialTheme(colorScheme = ChagokColors) {
                val s = setup.value ?: return@MaterialTheme
                App(s)
            }
        }
    }

    /** 진짜 차 연결 시도. 성공하면 true */
    private fun tryPleos(): Boolean {
        if (!PleosCar.sdkInApk()) return false
        val car = PleosCar(this)
        if (!car.connect()) return false
        val navi = PleosNavi(this)
        replace(Setup(car, navi, TripRecorder(car, navi, store), null))
        status.value = "Pleos 차량에 연결했어요. 값을 기다리는 중…"
        handler.postDelayed({
            if (setup.value?.car === car) {
                if (car.receivedAny.value) status.value = "Pleos 차량 값을 받고 있어요."
                else useFake("Pleos 차량에서 6초 동안 값이 오지 않아 가짜 차로 바꿨어요.")
            }
        }, 6000)
        return true
    }

    private fun useFake(reason: String) {
        val fake = FakeCar()
        val recorder = TripRecorder(fake, fake, store)
        fake.onDemoRestTap = recorder::addRestStop   // 자동 시연에서 휴게소 버튼을 대신 누름
        replace(Setup(fake, fake, recorder, fake))
        status.value = reason
    }

    private fun replace(next: Setup) {
        setup.value?.let { old ->
            old.recorder.stop(); old.car.stop(); old.navi.stop()
        }
        if (next.fake == null) AppClock.useRealClock()
        next.car.start(); next.navi.start(); next.recorder.start()
        setup.value = next
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        setup.value?.let { it.recorder.stop(); it.car.stop(); it.navi.stop() }
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun App(s: Setup) {
        var tab by remember { mutableIntStateOf(0) }
        // 1초마다 화면의 시계를 새로 그림
        val nowMillis by produceState(AppClock.now()) { while (true) { value = AppClock.now(); delay(1000) } }
        val car = s.car.snapshot.value
        val driving = s.recorder.phase.value == Phase.RECORDING
        val tabs = buildList {
            add("주행"); add("일기장 ${store.entries.size}"); add("차량 데이터")
            if (s.fake != null) add("시뮬레이터")
        }
        if (tab >= tabs.size) tab = 0

        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
        ) {
            val wide = maxWidth > 700.dp
            Column(Modifier.fillMaxSize()) {
                // 맨 위 줄: 앱 이름 + 데이터 출처 + 시계
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("차곡", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("  차가 쓰는 드라이브 일기", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("${s.car.label} · ${DiaryWriter.hhmm(nowMillis)}", fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 17.sp) })
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    when (tab) {
                        0 -> DriveScreen(s.recorder, car, store.entries.firstOrNull(), s.navi.state.value.destination?.name, wide)
                        1 -> DiaryScreen(store, s.navi, driving)
                        2 -> CarDataScreen(
                            car, s.car.label, s.car.isReal, status.value,
                            onUseFake = { useFake("직접 가짜 차로 바꿨어요.") },
                            onTryPleos = { if (!tryPleos()) status.value = "Pleos 차량 서비스에 연결할 수 없어요 (일반 폰/에뮬레이터)." },
                        )
                        else -> s.fake?.let { SimScreen(it, car) }
                    }
                }
            }
        }
    }
}
