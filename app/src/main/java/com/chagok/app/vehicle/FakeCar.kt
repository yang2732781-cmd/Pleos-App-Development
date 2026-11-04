package com.chagok.app.vehicle

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.chagok.app.navi.NaviSource
import com.chagok.app.navi.NaviState
import com.chagok.app.navi.Place
import com.chagok.app.trip.AppClock
import com.chagok.app.trip.DayNight
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 가짜 차 + 가짜 내비 (시뮬레이터).
 *
 * 일반 안드로이드 폰·에뮬레이터에서는 Pleos 차량 서비스가 없으므로 이걸로 시연합니다.
 * 시연 영상을 짧게 찍을 수 있도록 **현실 1초 = 차 안 1분** 으로 흘러갑니다.
 * (시뮬레이터 탭의 버튼으로 비·터널·도착 같은 일을 일으킬 수 있습니다.)
 */
class FakeCar : CarSource, NaviSource {

    override val label = "가짜 차 (시뮬레이터)"
    override val isReal = false

    private val handler = Handler(Looper.getMainLooper())
    private val _snapshot = mutableStateOf(
        CarSnapshot(
            tripDistanceKm = 0f, tripTimeMin = 0f, wiperOn = false, headlightOn = false,
            cabinLightOn = false, cabinTempC = 21.5f, openWindows = emptyList(), openDoors = emptyList(),
            rearOccupants = 2, energyPercent = 62f, lowWasherFluid = false, lowTirePressure = false,
            drivingMode = "COMFORT",
        )
    )
    override val snapshot: State<CarSnapshot> = _snapshot

    private val _navi = mutableStateOf(NaviState())
    override val state: State<NaviState> = _navi

    /** 시뮬레이터 화면에서 보여 줄 상태 */
    val driving = mutableStateOf(false)
    val speedKmh = mutableStateOf(0)

    /** 고를 수 있는 가짜 목적지 */
    val places = listOf(
        Place("강릉 경포해변", 37.8055, 128.9083),
        Place("가평 남이섬", 37.7906, 127.5256),
        Place("전주 한옥마을", 35.8150, 127.1530),
        Place("우리 집", 37.5665, 126.9780),
    )

    private var running = false
    private var tunnelEndsAtMin = -1f
    private var scenario: List<Pair<Int, () -> Unit>> = emptyList()
    private var scenarioStartMin = 0f

    private val tick = object : Runnable {
        override fun run() {
            step()
            if (running) handler.postDelayed(this, 1000)
        }
    }

    override fun start() {
        if (running) return
        if (AppClock.simulatedMillis == null) AppClock.simulatedMillis = System.currentTimeMillis()
        running = true
        handler.post(tick)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    // ───────── 시뮬레이터 버튼들 ─────────

    /** 시계를 오늘 hour:minute 로 맞춤 (null = 현재 시각) */
    fun setClock(hour: Int?, minute: Int = 0) {
        AppClock.simulatedMillis = if (hour == null) System.currentTimeMillis() else
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            }.timeInMillis
    }

    /** 목적지를 정하고 출발 */
    fun depart(place: Place) {
        _navi.value = _navi.value.copy(destination = place)
        update { it.copy(openDoors = emptyList(), tripDistanceKm = 0f, tripTimeMin = 0f) }
        driving.value = true
    }

    /** 목적지 도착 → 차를 세우고 내비가 "도착" 알림 */
    fun arrive() {
        driving.value = false
        speedKmh.value = 0
        _navi.value = _navi.value.copy(arrivedAt = AppClock.now())
        scenario = emptyList()
    }

    fun setRain(on: Boolean) = update { it.copy(wiperOn = on) }

    /** 터널 진입: 전조등이 켜졌다가 약 4분(=4초) 뒤 자동으로 꺼짐 */
    fun enterTunnel() {
        tunnelEndsAtMin = minutes() + 4
        update { it.copy(headlightOn = true) }
    }

    fun setHeadlight(on: Boolean) = update { it.copy(headlightOn = on) }
    fun setCabinLight(on: Boolean) = update { it.copy(cabinLightOn = on) }
    fun setRearOccupants(n: Int) = update { it.copy(rearOccupants = n.coerceIn(0, 3)) }
    fun toggleWindow() = update {
        val open = it.openWindows.orEmpty()
        it.copy(openWindows = if (open.isEmpty()) listOf("뒷좌석 오른쪽") else emptyList())
    }
    fun setLowWasher(on: Boolean) = update { it.copy(lowWasherFluid = on) }
    fun openDriverDoor() = update { it.copy(openDoors = listOf("운전석")) }

    /**
     * 시연용 자동 시나리오 (약 2분 40초):
     * 해 지기 85분 전 출발 → 20분 뒤 비 → 터널 2번 → 해 짐(시계 기준) → 비 그침 → 도착
     * (11월이면 오후 4시쯤 출발해 저녁 6시 40분쯤 도착)
     */
    fun playDemoScenario() {
        val sunset = DayNight.sunsetHour(System.currentTimeMillis())
        val startMin = (sunset * 60).toInt() - 85
        setClock(startMin / 60, startMin % 60)
        _snapshot.value = _snapshot.value.copy(
            wiperOn = false, headlightOn = false, cabinLightOn = false, openWindows = emptyList(),
            rearOccupants = 2, cabinTempC = 21.5f,
        )
        depart(places[0])
        scenarioStartMin = minutes()
        scenario = listOf(
            21 to { setRain(true) },
            38 to { enterTunnel() },
            55 to { enterTunnel() },
            75 to { toggleWindow() },
            80 to { toggleWindow() },
            96 to { setRain(false) },
            118 to { setHeadlight(true) },   // 해가 져서 전조등 켬 (밤이라 터널로 세지 않음)
            161 to { arrive() },
        )
    }

    // ───────── 내부 동작 ─────────

    private fun minutes(): Float = (AppClock.now() / 60000.0).toFloat()

    private fun step() {
        // 1초마다 차 안 시계가 1분 흐름
        AppClock.simulatedMillis = (AppClock.simulatedMillis ?: System.currentTimeMillis()) + 60_000

        val s = _snapshot.value
        val speed = if (driving.value) (speedKmh.value + ((85 + Random.nextInt(-15, 16)) - speedKmh.value) * 0.4f).roundToInt() else 0
        speedKmh.value = speed
        val km = speed / 60f // 1분 동안 달린 거리

        // 실내 온도는 비 오면 조금 내려가고, 창문 열면 더 내려감
        val target = when {
            s.openWindows.orEmpty().isNotEmpty() -> 19f
            s.wiperOn == true -> 21f
            else -> 23f
        }
        val temp = (s.cabinTempC ?: 22f).let { it + (target - it) * 0.1f }

        var next = s.copy(
            tripDistanceKm = (s.tripDistanceKm ?: 0f) + km,
            tripTimeMin = (s.tripTimeMin ?: 0f) + if (driving.value) 1f else 0f,
            cabinTempC = (temp * 10).roundToInt() / 10f,
            energyPercent = ((s.energyPercent ?: 60f) - km * 0.08f).coerceAtLeast(0f),
        )
        if (tunnelEndsAtMin > 0 && minutes() >= tunnelEndsAtMin) {
            tunnelEndsAtMin = -1f
            next = next.copy(headlightOn = false)
        }
        _snapshot.value = next.withRaw()

        // 시나리오 진행
        if (scenario.isNotEmpty()) {
            val elapsed = (minutes() - scenarioStartMin).toInt()
            val due = scenario.filter { it.first <= elapsed }
            scenario = scenario - due.toSet()
            due.forEach { it.second() }
        }
    }

    private fun update(change: (CarSnapshot) -> CarSnapshot) {
        _snapshot.value = change(_snapshot.value).withRaw()
    }

    /** "차량 데이터" 탭에 보일 값 (실차에서 쓰는 API 이름과 같게) */
    private fun CarSnapshot.withRaw() = copy(
        raw = linkedMapOf(
            "Odometer.getTripCurrentDrivingDistance" to "%.1f km".format(tripDistanceKm ?: 0f),
            "Odometer.getTripCurrentDrivingTime" to "${(tripTimeMin ?: 0f).toInt()} 분",
            "Wiper.getCurrentWiperState" to if (wiperOn == true) "LOW (작동)" else "OFF",
            "Light.getCurrentHeadLightState" to if (headlightOn == true) "ON" else "OFF",
            "Light.getCurrentCabinLightState" to if (cabinLightOn == true) "ON" else "OFF",
            "HVAC.getHvacTemperatureCurrent" to "$cabinTempC ℃",
            "Window.getCurrentWindowPosition" to (openWindows.orEmpty().ifEmpty { listOf("모두 닫힘") }.joinToString()),
            "Door.getCurrentDoorPosition" to (openDoors.orEmpty().ifEmpty { listOf("모두 닫힘") }.joinToString()),
            "Seat.getCurrentSeatOccupancy (뒷좌석)" to "${rearOccupants ?: 0}명",
            "Fuel.getFuelPercent" to "%.0f %%".format(energyPercent ?: 0f),
            "Safety.hasLowWasherFluidWarning" to "$lowWasherFluid",
            "Tire.getLowTirePressure" to "$lowTirePressure",
            "DrivingMode.getCurrentDrivingModeState" to (drivingMode ?: "-"),
        )
    )

    override fun routeTo(place: Place): Boolean {
        depart(place)
        return true
    }
}
