# 차곡 — 차가 쓰는 드라이브 일기

> 운전자는 아무것도 하지 않습니다. 차가 보고 느낀 것을 차곡차곡 모아, 도착하면 일기를 써 둡니다.

## 한 줄 소개
와이퍼·차량 시계·전조등·실내 온도·창문·뒷좌석 탑승·주행 거리·내비 목적지 같은 **차량 조회 데이터만으로**
그날의 드라이브를 사람이 쓴 것 같은 일기로 자동 기록하고, 내리기 전에 차 상태를 점검해 주는 Pleos 앱입니다.
차를 조종하는(제어) API는 하나도 쓰지 않습니다.

## 시나리오
1. **출발** — 주행 거리가 늘기 시작하면 기록 시작. 내비 목적지(NaviHelper)를 일기 제목에 씁니다.
2. **달리는 동안** — 화면은 큰 글자로 "일기 쓰는 중 · 1시간 12분 · 86km"만 보여 줍니다. 일기장은 잠깁니다.
   - 와이퍼가 움직이면 → "비가 내리기 시작했어요"
   - 차량 시계로 봐서 **낮인데** 전조등이 켜졌다 10분 안에 꺼지면 → "터널을 지났어요"
   - 차량 시계가 그날 해 지는 시각을 지나면 → "해가 졌어요" (낮/밤은 전조등이 아니라 **시계**로 판단)
   - 창문을 열면, 실내 온도가 바뀌면 → 기록
3. **도착** — 내비 도착 알림(또는 5분 이상 정차) → 일기 자동 저장 + **내리기 전 체크**
   (뒷좌석에 아이가 남았는지, 창문·전조등·실내등, 연료·워셔액·타이어 공기압)
4. **주차 후** — 일기장에서 지난 드라이브를 다시 읽고, "📍 다시 가기"로 그곳을 내비 목적지로 설정합니다.

일기 예시 (앱이 실제로 만든 문장):
> **비 내리던 해 질 녘, 강릉 경포해변 가는 길**
> 11월 4일 수요일 오후 4시, 뒷좌석에 2명을 태우고 출발했어요. 출발하고 21분쯤 지나 비가 내리기 시작했고,
> 약 1시간 15분 동안 와이퍼가 바쁘게 움직였어요. 비는 저녁 5시 36분쯤 그쳤어요. 터널을 2번 지났어요.
> 가는 길에 해가 저물어, 마지막 1시간 17분은 밤길이었어요. 창문을 열고 바깥 공기를 쐬기도 했어요.
> 차 안은 19~23도로 포근했어요. 2시간 41분 동안 215km를 달려 저녁 6시 41분에 강릉 경포해변에 도착했어요.

## 사용하는 Pleos API (모두 조회 또는 내비)
| 앱에서 하는 일 | Pleos API |
|---|---|
| 출발·도착, 거리·시간 | `Odometer.getTripCurrentDrivingDistance`, `getTripCurrentDrivingTime` |
| 비 | `Wiper.getCurrentWiperState` |
| 터널 (낮 시간대의 전조등) | `Light` 전조등 상태 + 차량 시계 |
| 실내 온도 | `HVAC.getHvacTemperatureCurrent` |
| 창문 열림 / 문 열림 | `Window.getCurrentWindowPosition`, `Door.getCurrentDoorPosition` |
| 뒷좌석 탑승 (출발 인원, 하차 확인) | `Seat.getCurrentSeatOccupancy` |
| 실내등 | `Light.getCurrentCabinLightState` |
| 연료·배터리 | `Fuel.getFuelPercent`, `EvBattery.getEvBatteryStateOfCharge` |
| 워셔액·타이어 | `Safety.hasLowWasherFluidWarning`, `Tire.getLowTirePressure` |
| 주행 모드 | `DrivingMode.getCurrentDrivingModeState` |
| 목적지 이름·좌표, 도착 알림, 다시 가기 | `NaviHelper` (`getDestinationInfo`, `onRouteStarted`, `onDestinationArrived`, `requestRoute`) |

## 안전 설계
- 주행 중에는 최소 정보만 큰 글자로, 일기장은 잠금 (주차 후 열림)
- 소리·팝업 없음, 운전자 조작 필요 없음
- 일기는 차 안에만 저장 (서버 전송 없음)

## 코드 구조
```
app/src/main/java/com/chagok/app/
├─ MainActivity.kt        시작점. 진짜 차/가짜 차 자동 선택, 탭 화면
├─ vehicle/
│   ├─ CarSnapshot.kt     차 상태 한 장면 + CarSource 인터페이스
│   ├─ FakeCar.kt         가짜 차·가짜 내비 (시뮬레이터, 1초 = 1분)
│   ├─ PleosCar.kt        Pleos Vehicle SDK 조회
│   └─ PleosReflect.kt    SDK 함수를 이름으로 부르는 도우미 (공식 샘플과 같은 방식)
├─ navi/                  NaviSource, PleosNavi(NaviHelper)
├─ trip/
│   ├─ AppClock.kt        차량 시계, 낮/밤 판단 (월별 일출·일몰)
│   └─ TripRecorder.kt    주행 감지, 비·터널·해 짐 기록, 도착 시 일기 작성
├─ diary/                 일기 저장(DiaryStore), 문장 만들기(DiaryWriter)
└─ ui/                    주행·일기장·차량 데이터·시뮬레이터 화면
```

실행 방법은 [GUIDE.md](GUIDE.md)를 보세요.
