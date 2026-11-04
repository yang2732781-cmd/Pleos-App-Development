package com.chagok.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KFunction

/**
 * 진짜 차 (Pleos Vehicle SDK, 조회 API만 사용).
 *
 * 1초마다 필요한 값을 get 으로 읽어 CarSnapshot 을 채웁니다.
 * (register 리스너 대신 주기적 조회를 쓰는 이유: 항목이 많아도 코드가 단순하고,
 *  어떤 값이 이 차에서 지원되지 않아도 나머지는 그대로 동작하기 때문)
 *
 * 쓰는 API (모두 조회):
 *   Odometer.getTripCurrentDrivingDistance / getTripCurrentDrivingTime
 *   Wiper.getCurrentWiperState
 *   Light.getCurrentHeadLightState(이름이 조금 달라도 찾아 씀) / getCurrentCabinLightState
 *   HVAC.getHvacTemperatureCurrent
 *   Window.getCurrentWindowPosition, Door.getCurrentDoorPosition  (모든 자리)
 *   Seat.getCurrentSeatOccupancy (2열·3열)
 *   Fuel.getFuelPercent / EvBattery.getEvBatteryStateOfCharge
 *   Safety.hasLowWasherFluidWarning, Tire.getLowTirePressure
 *   DrivingMode.getCurrentDrivingModeState
 *   SteeringWheel.getCurrentSteeringWheelAngle, Brake.getCurrentBrakePedalStatus, HVAC.getHvacRecirculationState (멀미 예보)
 */
class PleosCar(context: Context) : CarSource {

    override val label = "Pleos 차량"
    override val isReal = true

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val _snapshot = mutableStateOf(CarSnapshot())
    override val snapshot: State<CarSnapshot> = _snapshot

    /** 값이 한 번이라도 들어왔는지 (연결 확인용) */
    val receivedAny = mutableStateOf(false)

    private var vehicle: Any? = null
    private var running = false

    /** 한 번 읽을 항목: 이름표, 도메인 객체, 함수, 영역 목록 */
    private class Probe(val key: String, val domain: Any, val fn: KFunction<*>, val areas: List<String?>)
    private val probes = mutableListOf<Probe>()

    /** 영역별 최신 값: key → (영역 → 값) */
    private val values = mutableMapOf<String, MutableMap<String?, Any?>>()

    companion object {
        private const val TAG = "Chagok/PleosCar"
        private const val VEHICLE_CLASS = "ai.pleos.playground.vehicle.Vehicle"

        /** APK 안에 Pleos Vehicle SDK가 들어 있으면 true */
        fun sdkInApk(): Boolean = PleosReflect.classOrNull(VEHICLE_CLASS) != null
    }

    /**
     * SDK 연결. 성공하면 true.
     * 일반 폰에서는 SDK가 없거나 차량 서비스가 없어서 false 가 됩니다.
     */
    fun connect(): Boolean {
        return try {
            val cls = Class.forName(VEHICLE_CLASS)
            val v = cls.getConstructor(Context::class.java).newInstance(appContext)
            cls.getMethod("initialize").invoke(v)
            vehicle = v
            buildProbes(v)
            Log.i(TAG, "Pleos 연결, 읽을 항목 ${probes.size}개: ${probes.map { it.key }}")
            probes.isNotEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos 연결 실패: ${t.cause?.message ?: t.message}")
            false
        }
    }

    private fun domain(v: Any, getter: String): Any? =
        runCatching { v.javaClass.getMethod(getter).invoke(v) }.getOrNull()

    private fun add(key: String, domain: Any?, name: String, areaFilter: ((String) -> Boolean)? = null, fallback: ((String) -> Boolean)? = null) {
        domain ?: return
        val fn = PleosReflect.findFunction(domain, name, fallback) ?: run {
            Log.i(TAG, "이 SDK에 $name 없음"); return
        }
        val all = PleosReflect.areaNames(fn)
        val areas: List<String?> = when {
            all.isEmpty() -> listOf(null)
            areaFilter == null -> listOf(all.first())
            else -> all.filter(areaFilter).ifEmpty { listOf(all.first()) }
        }
        probes += Probe(key, domain, fn, areas)
    }

    private fun buildProbes(v: Any) {
        probes.clear()
        val odo = domain(v, "getOdometer")
        add("tripKm", odo, "getTripCurrentDrivingDistance")
        add("tripMin", odo, "getTripCurrentDrivingTime")
        add("wiper", domain(v, "getWiper"), "getCurrentWiperState", { it.contains("FRONT") })
        val light = domain(v, "getLight")
        add("headlight", light, "getCurrentHeadLightState", null) { it.startsWith("getCurrent") && it.contains("Head", true) }
        add("cabinLight", light, "getCurrentCabinLightState")
        add("cabinTemp", domain(v, "getHVAC"), "getHvacTemperatureCurrent", { it.contains("ROW_1_LEFT") || it.contains("FRONT_LEFT") })
        add("window", domain(v, "getWindow"), "getCurrentWindowPosition", { true })
        add("door", domain(v, "getDoor"), "getCurrentDoorPosition", { true })
        add("rearSeat", domain(v, "getSeat"), "getCurrentSeatOccupancy", { it.contains("ROW_2") || it.contains("ROW_3") })
        add("fuel", domain(v, "getFuel"), "getFuelPercent")
        add("evSoc", domain(v, "getEvBattery"), "getEvBatteryStateOfCharge")
        add("washer", domain(v, "getSafety"), "hasLowWasherFluidWarning")
        add("tire", domain(v, "getTire"), "getLowTirePressure", { true })
        add("drivingMode", domain(v, "getDrivingMode"), "getCurrentDrivingModeState")
        // 멀미 예보용
        add("steering", domain(v, "getSteeringWheel"), "getCurrentSteeringWheelAngle")
        add("brake", domain(v, "getBrake"), "getCurrentBrakePedalStatus")
        add("recirc", domain(v, "getHVAC"), "getHvacRecirculationState", { it.contains("ROW_1_LEFT") || it.contains("FRONT_LEFT") })
    }

    private val poll = object : Runnable {
        override fun run() {
            for (p in probes) for (area in p.areas) {
                PleosReflect.get(p.domain, p.fn, area, onValue = { value ->
                    values.getOrPut(p.key) { mutableMapOf() }[area] = value
                    receivedAny.value = true
                    publish()
                })
            }
            if (running) handler.postDelayed(this, 1000)
        }
    }

    override fun start() {
        if (running || vehicle == null) return
        running = true
        handler.post(poll)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(poll)
        vehicle?.let { v -> runCatching { v.javaClass.getMethod("release").invoke(v) } }
        vehicle = null
    }

    // ───────── 읽은 값 → CarSnapshot ─────────

    private fun one(key: String): Any? = values[key]?.values?.firstOrNull()

    private fun openAreas(key: String): List<String>? = values[key]?.let { m ->
        m.filter { (_, v) -> PleosReflect.isOn(v) == true }.keys.map { areaKorean(it) }
    }

    private fun publish() {
        val rear = values["rearSeat"]?.values?.count { PleosReflect.isOn(it) == true }
        val raw = linkedMapOf<String, String>()
        for (p in probes) {
            val m = values[p.key] ?: continue
            val name = "${p.fn.name}"
            raw[name] = if (m.size == 1 && m.keys.first() == null) PleosReflect.text(m.values.first())
            else m.entries.joinToString { "${it.key}=${PleosReflect.text(it.value)}" }
        }
        _snapshot.value = CarSnapshot(
            tripDistanceKm = PleosReflect.number(one("tripKm")),
            tripTimeMin = PleosReflect.number(one("tripMin")),
            wiperOn = PleosReflect.isOn(one("wiper")),
            headlightOn = PleosReflect.isOn(one("headlight")),
            cabinLightOn = PleosReflect.isOn(one("cabinLight")),
            cabinTempC = PleosReflect.number(one("cabinTemp")),
            openWindows = openAreas("window"),
            openDoors = openAreas("door"),
            rearOccupants = rear,
            energyPercent = PleosReflect.number(one("fuel")) ?: PleosReflect.number(one("evSoc")),
            lowWasherFluid = PleosReflect.isOn(one("washer")),
            lowTirePressure = values["tire"]?.values?.any { PleosReflect.isOn(it) == true },
            drivingMode = one("drivingMode")?.let { PleosReflect.text(it) },
            steeringAngleDeg = PleosReflect.number(one("steering")),
            brakeOn = PleosReflect.isOn(one("brake")),
            recirculationOn = PleosReflect.isOn(one("recirc")),
            raw = raw,
        )
    }

    private fun areaKorean(area: String?): String = when {
        area == null -> "전체"
        area.contains("ROW_1_LEFT") -> "운전석"
        area.contains("ROW_1_RIGHT") -> "조수석"
        area.contains("ROW_2_LEFT") -> "뒷좌석 왼쪽"
        area.contains("ROW_2_RIGHT") -> "뒷좌석 오른쪽"
        area.contains("ROW_2") -> "뒷좌석"
        area.contains("ROOF") -> "선루프"
        area.contains("HOOD") -> "보닛"
        area.contains("REAR") || area.contains("TRUNK") -> "트렁크"
        else -> area
    }
}
