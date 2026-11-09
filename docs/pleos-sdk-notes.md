# Pleos SDK 노트

document.pleos.ai의 API 레퍼런스 페이지는 JS 렌더링이라 자동 수집이 안 된다. 아래 내용은 공식 샘플 저장소와 Maven에 배포된 AAR(`javap`로 시그니처 확인)에서 직접 확인한 것이다. (확인일: 2026-11-09)

- 샘플: https://github.com/Pleos-Playground/pleos-sample-vehicle-app (959981d), https://github.com/Pleos-Playground/pleos-sample-navi-app (76ed592)
- 로컬 사본: `~/dev/pleos-samples/` (이 저장소에는 넣지 않는다)

## 1. Gradle

`settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}
```

`app/build.gradle.kts`

```kotlin
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:NaviHelper:3.0.5")   // navi 샘플 libs.versions.toml 기준
```

- 저장소 인증 없음. 전이 의존성(`vehicle-aidl`, `vehicle-data`, `navi-aidl`, `navi-data`, gson, kotlin-reflect, coroutines)은 자동으로 받아진다.
- 기준 버전(vehicle 샘플): JDK 17, Gradle wrapper 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compileSdk/targetSdk 35. 우리 앱 minSdk는 28.

## 2. 권한

Pleos 권한은 `protectionLevel=normal` → 매니페스트 선언만으로 부여된다. AAOS `CAR_SPEED`만 `dangerous`라 런타임 요청이 필요하다 (에뮬레이터 `dumpsys package`로 확인).

| 용도 | 권한 | 보호수준 | 근거 |
|---|---|---|---|
| 브레이크 페달 | `pleos.car.permission.CAR_DRIVE` | normal | `Brake`의 `@RequiresPermission` |
| 라이트(로우빔) | `pleos.car.permission.CAR_LIGHTS` | normal | `Light`의 `@RequiresPermission` |
| 위치·북마크·경로상태 | `pleos.car.permission.NAVI_ROUTE` | normal | `getCurrentLocationInfo/getBookmarkInfo/getRouteStateInfo` |
| 경로 요청 | `pleos.car.permission.NAVI_ROUTE_SEARCH` | normal | `requestRoute` |
| 속도 | `android.car.permission.CAR_SPEED` | **dangerous** | AAOS `PERF_VEHICLE_SPEED` |
| 기어 | `android.car.permission.CAR_POWERTRAIN` | normal | AAOS `GEAR_SELECTION` |

- Vehicle SDK에는 속도·기어 API가 없다 → AAOS Car API 사용 (Pleos "표준 우선" 정책과 일치). `pleos.car.permission.CAR_SPEED/CAR_POWERTRAIN`은 불필요.
- 권한 누락 시 다이얼로그가 아니라 `onFailed(Exception)`으로 온다.

## 3. Vehicle SDK (`ai.pleos.playground.vehicle`)

```kotlin
val vehicle = Vehicle(context.applicationContext)
vehicle.initialize()            // 예외 없이 끝나도 서비스 연결이 보장되지는 않음 → 값을 한 번 읽어 로그로 확인
val brake = vehicle.getBrake()
val light = vehicle.getLight()
// ...
vehicle.release()               // 비동기 콜백이 끝난 뒤에 호출
```

호출 패턴: `checkXxxCapabilities` → `getCurrentXxx` → `registerXxx(listener)` / `unregisterXxx(같은 listener)` → `release()`. 결과는 모두 비동기 콜백(`onComplete`, `onFailed`).

**Brake** (`api.Brake`)

```kotlin
fun checkBrakePedalStatusCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)
fun getCurrentBrakePedalStatus(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)
fun registerBrakePedalStatus(listener: BrakePedalStatusListener)
fun unregisterBrakePedalStatus(listener: BrakePedalStatusListener)

interface BrakePedalStatusListener : VehicleListener {
    fun onBrakePedalStatusUpdated(status: Boolean?)
}
interface VehicleListener { fun onFailed(e: Exception) }
```

**Light** (`api.Light`) — 헤드라이트는 로우빔으로 본다.

```kotlin
fun checkLowBeamLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)
fun getCurrentLowBeamLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)
fun registerLowBeamLightState(listener: LightStateListener)
fun unregisterLowBeamLightState(listener: LightStateListener)

interface LightStateListener : VehicleListener {
    fun onLightStateUpdated(state: LightState?)
}
enum class LightState { OFF, ON, DAYTIME_RUNNING }   // constant.control.LightState
```

**Odometer** (`api.Odometer`, 2차 후보): `getTripCurrentDrivingDistance(onComplete: (Float?) -> Unit, onFailed)` 등.

## 4. NaviHelper SDK (`ai.pleos.playground.navi`)

```kotlin
val helper = NaviHelper(context)          // navi.helper.NaviHelper
helper.initialize()                        // 또는 initialize(onSuccess: () -> Unit, onFailed: (Throwable) -> Unit)
helper.addListener(listener)               // NaviHelperEventListener — 모든 메서드에 기본 구현 있음
// ...
helper.removeListener(listener)
helper.release()
```

조회 API는 반환값이 없고 결과가 리스너로 온다.

| 호출 | 결과 콜백 |
|---|---|
| `getCurrentLocationInfo()` | `onCurrentLocationInfo(CurrentLocationInfo)` |
| `getBookmarkInfo()` | `onBookmarkInfo(List<BookmarkInfo>)` |
| `getRouteStateInfo()` | `onRouteStateInfo(RouteStateInfo)` |
| `requestRoute(RouteInfo)` | `onRouteStarted(RouteStartInfo)` / `onError(NaviErrorCode)` |
| (자동) | `onNavigationStatus(NavigationStatus)` — `INACTIVE, INITIALIZING, FREE_DRIVE, ROUTE_DRIVE` |

데이터 클래스 (`navi.data`, 주요 필드만):

```kotlin
CurrentLocationInfo(latitude: Double, longitude: Double, heading: Double, address: String?, routeId: String?, currentRouteIndex: Int?)
BookmarkInfo(latitude: Double, longitude: Double, displayLatitude: Double?, displayLongitude: Double?,
             name: String?, poiId: String?, poiSubId: String?, address: String?, category: BookmarkType?, poiProvider: String?)
// BookmarkType: HOME, OFFICE, EV_CHARGING_STATION, DEFAULT  → 집 = category == HOME
RouteStateInfo(termsAgreement: Boolean, naviMode: NaviMode, isRouting: Boolean, routeOption: RouteOption, ...)
// NaviMode: NOT_WORKING, SAFE_GUIDING, ROUTE_GUIDING, SIMULATION_GUIDING
// 경로 안내 중 판정 = naviMode == ROUTE_GUIDING (isRouting은 안내 중에도 false로 옴, 에뮬레이터 확인)
```

`RouteInfo` 생성 (navi 샘플 그대로, 나머지 인자는 기본값):

```kotlin
RouteInfo(
    latitude = 37.48544722,
    longitude = 127.03636666,
    poiId = "36khp37hh8gqcm5nqhe5",
    poiName = "42dot",
    poiSubId = "0",
    address = "서울시 강남구 남부순환로 2621"
)
```

## 5. 매니페스트

특별한 meta-data·Client ID 불필요 (Client ID/Secret은 Pleos TTS/STT 등 서버 연동 SDK 전용). 일반 `MAIN`/`LAUNCHER` intent-filter로 런처에 뜬다. NaviHelper AAR이 `<queries>`(`ai.pleos.playground.intent.action.NAVI_START`)를 자체 병합한다.

에뮬레이터에서 확인한 동작:
- HOME 북마크를 등록하지 않으면 `category=HOME`, 좌표 `(0.0, 0.0)`인 빈 항목이 온다 → 미등록으로 처리.
- `requestRoute`는 `poiId=""`(좌표만)로도 동작한다. 지도 앱이 앞으로 나오고 경로 안내가 자동 시작된다.

## 6. 에뮬레이터 (Pleos Connect v2.1.0)

- Virtual Console: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ . Motion 도메인에서 Gear(P/R/N/D), Brake Pedal(0–100%) 변경 가능. Exterior Lights Mode(Off/Auto/Tail/Low beam).
- Virtual Console은 HTTP API로도 조작된다 (브라우저 세션이 살아 있어야 함: `POST /browser/session?client=<id>`를 1초마다):
  `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`, `PUT /vehicle/controls?speed=<km/h>&accelerator=<%>&brake=<%>`.
  속도·브레이크 변경이 `PERF_VEHICLE_SPEED`와 Vehicle SDK Brake에 반영되는 것을 확인했다.
- 에뮬레이터는 user 빌드라 `adb shell cmd car_service` 값 주입이 막혀 있다.
- **TTS**: 유일한 엔진(`ai.pleos.playground.caas` SpeechService)이 intent-filter에 `CATEGORY_DEFAULT`가 없어 Android 표준 `TextToSpeech`가 엔진을 찾지 못한다 (`engines=[]`, init -1).
- **STT**: `ai.umos.ambientai` RecognitionService가 잡히지만 `startListening` 후 콜백이 오지 않는다 (마이크 미연결 상태에서 확인).
- 주행 중(D)에는 시스템 권한 다이얼로그가 차단된다 → 권한은 P에서 받는다. 앱 Activity는 `distractionOptimized` 메타데이터로 주행 중에도 표시된다.
- 백그라운드로 가면 앱 프로세스가 동결(freezer)되어 Car API 콜백이 쌓였다가 앞으로 나올 때 한꺼번에 전달된다.
- 설치된 서비스: `ai.pleos.playground.service.vehicle`, `ai.pleos.playground.service.navi`, 지도 앱 `ai.umos.maps.android.navigation.app`.
