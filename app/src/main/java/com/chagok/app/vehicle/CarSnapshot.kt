package com.chagok.app.vehicle

import androidx.compose.runtime.State

/**
 * 차 상태 한 장면(스냅샷).
 *
 * 모두 Pleos Vehicle SDK의 "조회(get)" API로 읽을 수 있는 값입니다.
 * 값을 못 읽었으면 null 로 둡니다 (예: 이 차에는 그 센서가 없음).
 * 차를 "조종"하는 값은 하나도 없습니다.
 */
data class CarSnapshot(
    /** Odometer.getTripCurrentDrivingDistance : 이번 주행 거리 (km) */
    val tripDistanceKm: Float? = null,
    /** Odometer.getTripCurrentDrivingTime : 이번 주행 시간 (분) */
    val tripTimeMin: Float? = null,
    /** Wiper.getCurrentWiperState : 와이퍼가 움직이는지 → 비 */
    val wiperOn: Boolean? = null,
    /** Light 전조등 상태 → 낮인데 켜졌다 꺼지면 터널 */
    val headlightOn: Boolean? = null,
    /** Light 실내등/독서등 */
    val cabinLightOn: Boolean? = null,
    /** HVAC.getHvacTemperatureCurrent : 실내 현재 온도 */
    val cabinTempC: Float? = null,
    /** Window.getCurrentWindowPosition : 열린 창문 이름 목록 (빈 목록 = 모두 닫힘) */
    val openWindows: List<String>? = null,
    /** Door.getCurrentDoorPosition : 열린 문 이름 목록 */
    val openDoors: List<String>? = null,
    /** Seat.getCurrentSeatOccupancy : 뒷좌석에 앉은 사람 수 */
    val rearOccupants: Int? = null,
    /** Fuel.getFuelPercent 또는 EvBattery.getEvBatteryStateOfCharge (%) */
    val energyPercent: Float? = null,
    /** Safety.hasLowWasherFluidWarning */
    val lowWasherFluid: Boolean? = null,
    /** Tire.getLowTirePressure */
    val lowTirePressure: Boolean? = null,
    /** DrivingMode.getCurrentDrivingModeState (ECO, SPORT 같은 이름) */
    val drivingMode: String? = null,
    /** SteeringWheel.getCurrentSteeringWheelAngle : 핸들 각도 (도, 왼쪽 -, 오른쪽 +) → 굽잇길 */
    val steeringAngleDeg: Float? = null,
    /** Brake.getCurrentBrakePedalStatus : 브레이크 밟는 중 → 가다 서다 */
    val brakeOn: Boolean? = null,
    /** HVAC.getHvacRecirculationState : 내기순환 켜짐 → 답답한 공기 */
    val recirculationOn: Boolean? = null,
    /** 화면의 "차량 데이터" 탭에 그대로 보여 줄 원본 값 (API 이름 → 값) */
    val raw: Map<String, String> = emptyMap(),
)

/**
 * 차에서 값을 읽어 오는 창구.
 *  - FakeCar  : 일반 폰/에뮬레이터용 가짜 차 (시뮬레이터 탭으로 조작)
 *  - PleosCar : Pleos 에뮬레이터·실차용 (Vehicle SDK 조회 API)
 * 화면과 일기 로직은 이 인터페이스만 보므로 둘 중 무엇이든 그대로 동작합니다.
 */
interface CarSource {
    val snapshot: State<CarSnapshot>
    /** 화면에 보여 줄 이름 */
    val label: String
    /** 진짜 차(Pleos)면 true */
    val isReal: Boolean
    fun start()
    fun stop()
}
