package com.chagok.app.trip

import java.util.Calendar

/**
 * 앱 전체가 쓰는 "지금 몇 시".
 *
 * 실제 차에서는 차량 시스템 시계(= 안드로이드 시스템 시간)를 그대로 씁니다.
 * 가짜 차 시뮬레이터에서는 시연을 위해 시계를 원하는 시각으로 맞추고
 * 1초 = 1분으로 빨리 감을 수 있습니다.
 */
object AppClock {
    /** null 이면 진짜 시계. 값이 있으면 시뮬레이터 시계(밀리초) */
    @Volatile
    var simulatedMillis: Long? = null

    fun now(): Long = simulatedMillis ?: System.currentTimeMillis()

    fun useRealClock() {
        simulatedMillis = null
    }
}

/** 하루 중 언제인지 */
enum class DayPart(val word: String) {
    DAWN("새벽"), MORNING("아침"), FORENOON("오전"), AFTERNOON("오후"), EVENING("저녁"), NIGHT("밤");

    val isDark: Boolean get() = this == DAWN || this == NIGHT || this == EVENING
}

/**
 * 시계와 날짜로 낮/밤을 판단합니다. (전조등이 아니라 시계 기준)
 * 해 뜨는/지는 시각은 서울 기준 월별 평균값입니다.
 */
object DayNight {
    // 1월~12월 서울 평균 일출·일몰 시각 (시, 소수점은 분/60)
    private val sunrise = floatArrayOf(7.7f, 7.4f, 6.8f, 6.1f, 5.5f, 5.2f, 5.4f, 5.8f, 6.2f, 6.6f, 7.1f, 7.6f)
    private val sunset = floatArrayOf(17.6f, 18.1f, 18.6f, 19.1f, 19.5f, 19.9f, 19.8f, 19.3f, 18.6f, 18.0f, 17.4f, 17.3f)

    private fun hourOf(millis: Long): Pair<Float, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
        return h to c.get(Calendar.MONTH)
    }

    /** 그날 해 지는 시각 (예: 17.4 = 17시 24분) */
    fun sunsetHour(millis: Long): Float = sunset[hourOf(millis).second]

    /** 해가 떠 있는 시간이면 true */
    fun isDaylight(millis: Long): Boolean {
        val (h, m) = hourOf(millis)
        return h >= sunrise[m] && h < sunset[m]
    }

    fun dayPart(millis: Long): DayPart {
        val (h, m) = hourOf(millis)
        return when {
            h < 5f -> DayPart.DAWN
            h < sunrise[m] -> DayPart.DAWN
            h < 9f -> DayPart.MORNING
            h < 12f -> DayPart.FORENOON
            h < sunset[m] -> DayPart.AFTERNOON
            h < sunset[m] + 2.5f -> DayPart.EVENING
            else -> DayPart.NIGHT
        }
    }
}
