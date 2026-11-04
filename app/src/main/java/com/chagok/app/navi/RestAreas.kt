package com.chagok.app.navi

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 휴게소 목록 + "가는 방향 앞쪽에서 가장 가까운 휴게소" 찾기.
 *
 * NaviHelper 에는 장소(카테고리) 검색 API가 없어서, 주요 고속도로 휴게소를 앱에 넣어 두었습니다.
 * ⚠ 좌표는 시연용 근사값입니다. 실제 배포 전에는 한국도로공사 휴게소 공공데이터로 바꾸세요.
 */
object RestAreas {
    val all = listOf(
        Place("용인휴게소", 37.2420, 127.2120),
        Place("덕평자연휴게소", 37.2398, 127.3826),
        Place("여주휴게소", 37.3016, 127.6370),
        Place("문막휴게소", 37.3290, 127.8240),
        Place("횡성휴게소", 37.4950, 128.0350),
        Place("평창휴게소", 37.5650, 128.4290),
        Place("대관령휴게소", 37.6880, 128.7560),
        Place("가평휴게소", 37.7500, 127.4400),
        Place("내린천휴게소", 37.9180, 128.2830),
        Place("안성휴게소", 37.0300, 127.2000),
        Place("망향휴게소", 36.9300, 127.1500),
        Place("정안알밤휴게소", 36.6200, 127.1300),
        Place("여산휴게소", 36.0700, 127.0700),
    )

    /** 두 지점 사이 거리 (km) */
    fun km(a: Place, b: Place): Double {
        val r = 6371.0
        val la1 = Math.toRadians(a.lat ?: 0.0); val la2 = Math.toRadians(b.lat ?: 0.0)
        val dLa = la2 - la1; val dLo = Math.toRadians((b.lon ?: 0.0) - (a.lon ?: 0.0))
        val h = sin(dLa / 2).pow(2) + cos(la1) * cos(la2) * sin(dLo / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /**
     * 지금 위치에서 목적지 쪽으로 가는 길에 있는(= 목적지에 더 가까운) 휴게소 중 가장 가까운 곳.
     * 50km 안에 없으면 null.
     */
    fun nextOnTheWay(here: Place?, destination: Place?): Place? {
        if (here?.lat == null) return null
        val remain = destination?.lat?.let { km(here, destination) }
        return all
            .filter { remain == null || km(it, destination!!) < remain }
            .map { it to km(here, it) }
            .filter { it.second in 3.0..50.0 }
            .minByOrNull { it.second }
            ?.first
    }
}
