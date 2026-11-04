# 차곡 실행 가이드 (Android 처음이어도 따라 할 수 있게)

## 0. 준비물
- **Android Studio** (최신 버전, 무료) — https://developer.android.com/studio
- `chagok-project.zip` (이 프로젝트)

---

## 1. 프로젝트 열기
1. `chagok-project.zip`을 바탕화면 같은 곳에 **압축 해제**합니다. → `chagok` 폴더가 생깁니다.
   - 경로에 한글이 없으면 더 안전합니다. 예: `C:\dev\chagok`
2. Android Studio 실행 → 첫 화면에서 **Open** 클릭 → `chagok` 폴더 선택 → **OK**.
3. "Trust Project?" 창이 뜨면 **Trust Project** 클릭.
4. 오른쪽 아래에 진행 막대가 돌며 **Gradle Sync**가 진행됩니다 (처음엔 5~10분).
   끝나면 아래쪽 **Build** 창에 `BUILD SUCCESSFUL` 또는 초록 체크가 보입니다.

### Sync가 실패하면
- 메시지에 `nexus-playground.pleos.ai`가 보이면 Pleos SDK 저장소에 접속이 안 되는 것입니다.
  1. 왼쪽 파일 목록에서 `gradle.properties` 더블클릭
  2. 맨 아래 `pleosSdk=true`를 `pleosSdk=false`로 고침
  3. 위쪽 코끼리 아이콘(**Sync Project with Gradle Files**) 클릭
  - 이렇게 하면 SDK 없이 빌드되고, 앱은 **가짜 차** 모드로만 동작합니다. (일반 폰 시연에는 문제 없음)
- `JDK` 관련 오류면: **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** 를 `17` 이상(예: jbr-17 / jbr-21)으로 선택.

---

## 2. 일반 에뮬레이터나 휴대폰에서 실행 (가짜 차 모드)
### 에뮬레이터
1. 오른쪽 위 **Device Manager**(휴대폰 아이콘) 클릭 → **+** → **Create Virtual Device**
2. **Tablet → Pixel Tablet** 선택 (차 화면처럼 가로로 넓어서 추천) → Next
3. 시스템 이미지 **API 35** 옆 다운로드 아이콘 클릭 → 완료 후 Next → Finish
4. 위쪽 기기 목록에서 방금 만든 기기 선택 → 초록 ▶ **Run 'app'** 클릭

### 휴대폰
1. 휴대폰 **설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호**를 7번 눌러 개발자 모드 켜기
2. **설정 → 개발자 옵션 → USB 디버깅** 켜기 → USB로 PC 연결 → "허용"
3. Android Studio 위쪽 기기 목록에서 내 폰 선택 → ▶ **Run 'app'**

### 앱에서 해 볼 것 (시연 영상 그대로)
1. 맨 위 오른쪽에 `가짜 차 (시뮬레이터)`가 보이면 정상입니다.
2. **시뮬레이터** 탭 → **▶ 자동 시연** 클릭
3. **주행** 탭으로 이동 → "일기 쓰는 중"이 뜨고, 약 2분 40초 동안 비·터널·해 짐이 기록됩니다.
   - 오른쪽 **뒷좌석 멀미 예보** 카드를 보세요. 산길(굽잇길)에 들어서면 약 47초 뒤 "주의", 62초쯤 "위험"으로 올라가고
     "☕ 횡성휴게소 경유지로 추가" 버튼이 나옵니다. 자동 시연에서는 68초에 이 버튼을 대신 눌러 줘요.
   - 외기로 바꾸고 창문을 열면 지수가 내려가고, 끝 무렵 시내 정체 구간에서 "가다 서다가 잦아요"로 다시 "주의"가 떠요.
   (차 안 시계가 1초에 1분씩 흐릅니다. 시연은 그날 해 지기 85분 전에 출발하도록 시계를 맞춰요.)
4. 운전 중에 **일기장** 탭을 누르면 🔒 잠겨 있습니다.
5. 도착하면 왼쪽에 **오늘의 일기**, 오른쪽에 **내리기 전 체크**가 뜹니다.
   - 시뮬레이터 탭에서 "뒷좌석 −"를 두 번 누르거나 "창문 닫기"를 누르면 체크가 바로 초록색으로 바뀝니다.
6. **일기장** 탭 → 일기 클릭 → **📍 다시 가기** → 그 장소로 다시 출발합니다.
7. **차량 데이터** 탭 → 앱이 읽고 있는 Pleos API 이름과 값이 보입니다 (심사용 화면).

직접 조작해 보고 싶으면 시뮬레이터에서 목적지 버튼 → 비 오게 / 터널 지나기 / 🌀 굽잇길 / 🚦 정체 / 🌬 내기순환 / 시계 맞추기 → 🏁 도착 순서로 눌러 보세요.
멀미 점수 기준은 `trip/SicknessMeter.kt` 맨 위 숫자들을 바꾸면 조절됩니다.

---

## 3. Pleos 에뮬레이터에서 실행 (진짜 차 데이터)
1. Pleos Connect 에뮬레이터(Automotive, API 34)를 공식 문서대로 설치합니다.
   https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
2. `gradle.properties`가 `pleosSdk=true`인지 확인 → Sync
3. 에뮬레이터를 켜고, 기기 목록에서 선택 → ▶ Run
4. 앱 위쪽에 `Pleos 차량`이 보이면 연결된 것입니다.
   - 6초 동안 값이 안 오면 자동으로 가짜 차로 바뀌고, **차량 데이터** 탭에 이유가 적힙니다.
5. **차량 데이터** 탭에서 값이 들어오는지 확인합니다. 에뮬레이터의 차량 설정(와이퍼, 전조등, 창문 등)을 바꾸면 값이 바뀌어야 합니다.
   - ⚠ `getTripCurrentDrivingDistance`의 단위가 km가 아니라 m라면 일기 거리가 1000배로 나옵니다. 그런 경우 알려 주세요. 한 줄로 고칠 수 있어요.
6. 내비에서 목적지를 정하고 주행 시뮬레이션을 하면 기록이 시작되고, 도착 알림이 오면 일기가 써집니다.

> 코드가 SDK 함수를 "이름으로" 찾아 부르기 때문에(공식 샘플 앱과 같은 방식), SDK 함수가 이 차에서 지원되지 않아도 앱이 멈추지 않고 그 항목만 비워 둡니다.
> 어떤 값이 안 들어오는지는 Android Studio 아래쪽 **Logcat**에서 `Chagok` 으로 검색하면 보입니다.

---

## 4. APK 만들기
- **Build → Build App Bundle(s) / APK(s) → Build APK(s)** → 오른쪽 아래 알림의 **locate** 클릭 → `app-debug.apk`
- 또는 GitHub에 올리면 `.github/workflows/build-apk.yml`이 자동으로 `apk/chagok.apk`를 만들어 줍니다 (같이가 때와 같은 방식).

## 5. GitHub에 올리기
같이가와 다른 앱이므로 **새 저장소**(예: `Chagok`)를 만드는 걸 추천합니다.
github.com → 오른쪽 위 **+** → **New repository** → 이름 `Chagok` → **Create repository** →
**uploading an existing file** 링크 → 압축 푼 `chagok` 폴더 안의 내용을 모두 끌어다 놓기 → **Commit changes**.
(`.github` 폴더는 숨김 폴더라 끌어다 놓기가 안 될 수 있어요. 그때는 저장소에서 **Add file → Create new file** 로 `.github/workflows/build-apk.yml` 이름을 입력하고 내용을 붙여 넣으세요.)
