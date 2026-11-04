package com.chagok.app.trip

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import com.chagok.app.diary.DiaryEntry
import com.chagok.app.diary.DiaryStore
import com.chagok.app.diary.DiaryWriter
import com.chagok.app.diary.Josa
import com.chagok.app.diary.Moment
import com.chagok.app.navi.NaviSource
import com.chagok.app.navi.Place
import com.chagok.app.vehicle.CarSnapshot
import com.chagok.app.vehicle.CarSource

/** 주행 한 번 동안 모으는 기록 */
class TripLog(val startedAt: Long, val startDistanceKm: Float, val rearAtStart: Int?) {
    var destination: Place? = null
    var lastMoveAt: Long = startedAt
    var rainOnSince: Long? = null
    var firstRainAt: Long? = null
    var lastRainEndAt: Long? = null
    var rainMinutes = 0f
    var headlightOnAt: Long? = null      // 낮에 전조등이 켜진 시각 (터널 후보)
    var tunnelCount = 0
    var sunsetSeen = false
    var darkMinutes = 0f
    var windowOpened = false
    var tempMin: Float? = null
    var tempMax: Float? = null
    var lastBreakNoticeMin = 0
    val moments = mutableListOf<Moment>()
}

enum class Phase { IDLE, RECORDING, ARRIVED }

/**
 * 차가 쓰는 일기의 "눈".
 *
 * 1초마다 차 상태와 시계를 보고,
 *  - 주행 거리가 늘기 시작하면 → 기록 시작
 *  - 와이퍼 켜짐/꺼짐 → 비 시작/그침
 *  - "시계로 봐서 낮"인데 전조등이 켜졌다 10분 안에 꺼짐 → 터널
 *  - 주행 중 시계가 해 지는 시각을 지남 → 해가 짐
 *  - 내비 도착 알림 또는 5분 넘게 거리가 그대로 → 도착, 일기 작성
 */
class TripRecorder(
    private val car: CarSource,
    private val navi: NaviSource,
    private val store: DiaryStore,
) {
    val phase = mutableStateOf(Phase.IDLE)
    /** 지금 기록 중인 주행 (화면 표시용 숫자들) */
    val liveDistanceKm = mutableStateOf(0f)
    val liveMinutes = mutableStateOf(0)
    val liveMoments = mutableStateOf<List<Moment>>(emptyList())
    /** 방금 쓴 일기 (도착 화면에 보여 줌) */
    val justWritten = mutableStateOf<DiaryEntry?>(null)

    private var log: TripLog? = null
    private var lastDistance: Float? = null
    private var lastTripMin: Float? = null
    private var lastTickAt: Long = AppClock.now()
    private var prev: CarSnapshot = CarSnapshot()
    private var wasDaylight: Boolean? = null
    private var handledArrival: Long? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 1000)
        }
    }

    fun start() { handler.post(tick) }
    fun stop() { handler.removeCallbacks(tick) }

    /** 직접 "주행 마치기" 버튼 */
    fun finishNow() { log?.let { finish(it, AppClock.now()) } }

    /** 도착 화면 닫기 */
    fun dismissArrival() {
        if (phase.value == Phase.ARRIVED) phase.value = Phase.IDLE
    }

    private fun step() {
        val now = AppClock.now()
        val dtMin = ((now - lastTickAt) / 60000f).coerceIn(0f, 5f)
        lastTickAt = now
        val s = car.snapshot.value
        val dist = s.tripDistanceKm
        val tripMin = s.tripTimeMin

        // 움직였나? (거리 증가, 거리를 못 읽는 차면 주행 시간 증가로 판단)
        val moved = when {
            dist != null && lastDistance != null -> dist - lastDistance!! > 0.005f
            tripMin != null && lastTripMin != null -> tripMin > lastTripMin!!
            else -> false
        }
        val distanceBefore = lastDistance
        lastDistance = dist
        lastTripMin = tripMin

        val current = log
        if (current == null) {
            if (moved) begin(now, s, distanceBefore?.takeIf { dist != null && it <= dist } ?: 0f)
            prev = s
            return
        }

        if (moved) current.lastMoveAt = now
        val daylight = DayNight.isDaylight(now)
        navi.state.value.destination?.let { if (current.destination == null) current.destination = it }

        // 비 (와이퍼)
        if (s.wiperOn == true && prev.wiperOn != true) {
            current.rainOnSince = now
            if (current.firstRainAt == null) current.firstRainAt = now
            note(current, now, "☔", "비가 내리기 시작했어요")
        }
        if (s.wiperOn != true && prev.wiperOn == true) {
            current.rainOnSince = null
            current.lastRainEndAt = now
            note(current, now, "🌤", "비가 그쳤어요")
        }
        if (s.wiperOn == true) current.rainMinutes += dtMin

        // 터널: 시계로 봐서 낮인데 전조등이 켜졌다가 10분 안에 꺼짐
        if (s.headlightOn == true && prev.headlightOn != true && daylight) current.headlightOnAt = now
        if (s.headlightOn != true && prev.headlightOn == true) {
            val on = current.headlightOnAt
            if (on != null && now - on <= 10 * 60_000) {
                current.tunnelCount++
                note(current, now, "🚇", "터널을 지났어요 (${current.tunnelCount}번째)")
            }
            current.headlightOnAt = null
        }

        // 해 지는 순간 (시계 기준)
        if (wasDaylight == true && !daylight) {
            current.sunsetSeen = true
            current.headlightOnAt = null
            note(current, now, "🌇", "해가 졌어요")
        }
        if (!daylight) current.darkMinutes += dtMin
        wasDaylight = daylight

        // 창문
        if (s.openWindows.orEmpty().isNotEmpty() && prev.openWindows.orEmpty().isEmpty()) {
            if (!current.windowOpened) note(current, now, "🪟", "창문을 열고 바람을 쐬었어요")
            current.windowOpened = true
        }

        // 실내 온도
        s.cabinTempC?.let { t ->
            current.tempMin = minOf(current.tempMin ?: t, t)
            current.tempMax = maxOf(current.tempMax ?: t, t)
        }

        // 2시간마다 한 줄
        val minutes = ((now - current.startedAt) / 60000).toInt()
        if (minutes >= current.lastBreakNoticeMin + 120) {
            current.lastBreakNoticeMin = minutes
            note(current, now, "☕", "${minutes / 60}시간째 달리는 중")
        }

        liveMinutes.value = minutes
        liveDistanceKm.value = ((dist ?: 0f) - current.startDistanceKm).coerceAtLeast(0f)

        // 도착?
        val arrivedAt = navi.state.value.arrivedAt
        val naviArrived = arrivedAt != null && arrivedAt > current.startedAt && arrivedAt != handledArrival
        val stoppedLong = now - current.lastMoveAt >= 5 * 60_000 && liveDistanceKm.value > 0.5f
        if (naviArrived || stoppedLong) {
            handledArrival = arrivedAt
            finish(current, if (naviArrived) arrivedAt!! else current.lastMoveAt)
        }
        prev = s
    }

    private fun begin(now: Long, s: CarSnapshot, startDistance: Float) {
        val l = TripLog(now, startDistance, s.rearOccupants)
        l.destination = navi.state.value.destination
        log = l
        wasDaylight = DayNight.isDaylight(now)
        justWritten.value = null
        val where = l.destination?.name?.let { "${Josa.toward(it)} 출발" } ?: "출발"
        note(l, now, "🚗", where)
        phase.value = Phase.RECORDING
    }

    private fun finish(l: TripLog, endedAt: Long) {
        val distance = liveDistanceKm.value
        note(l, endedAt, "🏁", l.destination?.name?.let { "$it 도착" } ?: "도착")
        val entry = DiaryWriter.write(l, endedAt, distance)
        store.add(entry)
        justWritten.value = entry
        log = null
        liveMoments.value = emptyList()
        phase.value = Phase.ARRIVED
    }

    private fun note(l: TripLog, at: Long, icon: String, text: String) {
        l.moments += Moment(at, icon, text)
        liveMoments.value = l.moments.toList()
    }
}
