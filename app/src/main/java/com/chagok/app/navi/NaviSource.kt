package com.chagok.app.navi

import androidx.compose.runtime.State

/** 내비 목적지 (이름 + 좌표) */
data class Place(val name: String, val lat: Double? = null, val lon: Double? = null)

/** 내비에서 알고 있는 것 */
data class NaviState(
    /** 지금 안내 중인 목적지 (NaviHelper.getDestinationInfo / onRouteStarted) */
    val destination: Place? = null,
    /** 도착 알림을 받은 시각 (NaviHelper onDestinationArrived). 받을 때마다 바뀜 */
    val arrivedAt: Long? = null,
)

/**
 * 내비와 이야기하는 창구.
 *  - FakeNavi  : 시뮬레이터용
 *  - PleosNavi : Pleos NaviHelper SDK
 */
interface NaviSource {
    val state: State<NaviState>
    val label: String
    fun start()
    fun stop()

    /** 이 장소로 길 안내 요청 (NaviHelper.requestRoute). 요청을 보냈으면 true */
    fun routeTo(place: Place): Boolean
}
