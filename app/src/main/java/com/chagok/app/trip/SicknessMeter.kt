package com.chagok.app.trip

import com.chagok.app.diary.DiaryWriter
import com.chagok.app.vehicle.CarSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/** 멀미 단계 */
enum class SickLevel(val word: String) { CALM("편안"), CAUTION("주의"), DANGER("위험") }

/** 멀미 지수 한 번 계산한 결과 */
data class SicknessReading(
    val score: Int = 0,
    val level: SickLevel = SickLevel.CALM,
    /** 왜 올라갔는지 (짧은 말, 큰 것부터) */
    val reasons: List<String> = emptyList(),
    /** 같은 이유를 일기에 쓸 짧은 이름 (예: 굽잇길, 답답한 공기) */
    val causes: List<String> = emptyList(),
    /** 운전자가 할 수 있는 일 한 줄 */
    val tip: String? = null,
)

/**
 * 뒷좌석 멀미 예보.
 *
 * 멀미는 "몸이 느끼는 흔들림"과 "답답한 공기", "오래 탄 시간"이 겹칠 때 심해집니다.
 * 이 중 차가 이미 알고 있는 것만으로 0~100 점수를 매깁니다 (모두 조회 API).
 *
 *  ┌ 굽잇길   최근 10분 핸들 각도 평균(절댓값)   SteeringWheel.getCurrentSteeringWheelAngle   최대 35점
 *  ├ 가다 서다 최근 10분 중 브레이크 밟은 비율     Brake.getCurrentBrakePedalStatus             최대 25점
 *  ├ 답답한 공기 내기순환 + 창문 모두 닫힘          HVAC.getHvacRecirculationState, Window      15점
 *  ├ 더운 실내 25℃ 넘으면 1도에 4점              HVAC.getHvacTemperatureCurrent               최대 15점
 *  └ 오래 탐   45분 넘으면 3분에 1점              Odometer.getTripCurrentDrivingTime           최대 15점
 *
 * 35점 이상 "주의", 60점 이상 "위험". 숫자는 시연하며 맞춰 갈 수 있게 위에 모아 두었습니다.
 * 평균 각도·비율을 쓰기 때문에 값을 1초마다 읽든 1분마다 읽든 점수가 비슷하게 나옵니다.
 */
class SicknessMeter {
    companion object {
        const val WINDOW_MIN = 10            // 최근 몇 분을 볼지
        const val CURVE_FULL_DEG = 90f       // 평균 핸들 각도가 이만큼이면 굽잇길 만점
        const val CURVE_MAX = 35f
        const val BRAKE_FULL_RATIO = 0.5f    // 시간의 절반을 브레이크 밟고 있으면 만점
        const val BRAKE_MAX = 25f
        const val STUFFY = 15f
        const val HOT_FROM_C = 25f
        const val HOT_MAX = 15f
        const val LONG_FROM_MIN = 45
        const val LONG_MAX = 15f
        const val CAUTION_AT = 35
        const val DANGER_AT = 60
    }

    private class Sample(val at: Long, val steer: Float?, val brake: Boolean?)
    private val samples = ArrayDeque<Sample>()

    fun reset() = samples.clear()

    fun update(now: Long, s: CarSnapshot, tripMinutes: Int): SicknessReading {
        samples.addLast(Sample(now, s.steeringAngleDeg, s.brakeOn))
        while (samples.isNotEmpty() && now - samples.first().at > WINDOW_MIN * 60_000L) samples.removeFirst()

        val steers = samples.mapNotNull { it.steer }
        val brakes = samples.mapNotNull { it.brake }
        val avgSteer = if (steers.isEmpty()) 0f else steers.map { abs(it) }.average().toFloat()
        val brakeRatio = if (brakes.isEmpty()) 0f else brakes.count { it } / brakes.size.toFloat()

        val curve = (avgSteer / CURVE_FULL_DEG * CURVE_MAX).coerceAtMost(CURVE_MAX)
        val stopGo = (brakeRatio / BRAKE_FULL_RATIO * BRAKE_MAX).coerceAtMost(BRAKE_MAX)
        val stuffy = if (s.recirculationOn == true && s.openWindows.orEmpty().isEmpty()) STUFFY else 0f
        val hot = s.cabinTempC?.let { ((it - HOT_FROM_C) * 4f).coerceIn(0f, HOT_MAX) } ?: 0f
        val long = ((tripMinutes - LONG_FROM_MIN) / 3f).coerceIn(0f, LONG_MAX)

        val score = (curve + stopGo + stuffy + hot + long).roundToInt().coerceIn(0, 100)
        val level = when {
            score >= DANGER_AT -> SickLevel.DANGER
            score >= CAUTION_AT -> SickLevel.CAUTION
            else -> SickLevel.CALM
        }

        // 이유와 할 일 (점수가 큰 요인부터)
        class Part(val points: Float, val reason: String, val cause: String, val tip: String)
        val parts = listOf(
            Part(curve, "굽잇길이 이어져요", "굽잇길", "커브에서 속도를 줄이고 부드럽게 돌아 주세요"),
            Part(stopGo, "가다 서다가 잦아요", "잦은 정체", "앞차와 거리를 두고 천천히 멈춰 주세요"),
            Part(stuffy, "내기순환에 창문도 닫혀 있어요", "답답한 공기", "외기 순환으로 바꾸거나 창문을 조금 열어 주세요"),
            Part(hot, "차 안이 더워요 (${s.cabinTempC?.let { "%.0f".format(it) }}℃)", "더운 실내", "온도를 2도쯤 낮춰 주세요"),
            Part(long, "${DiaryWriter.durationText(tripMinutes)}째 타고 있어요", "긴 탑승 시간", "가까운 휴게소에서 잠깐 쉬어 가세요"),
        ).filter { it.points >= 5f }.sortedByDescending { it.points }

        return SicknessReading(
            score = score,
            level = level,
            reasons = parts.map { it.reason },
            causes = parts.map { it.cause },
            tip = parts.firstOrNull()?.tip,
        )
    }
}
