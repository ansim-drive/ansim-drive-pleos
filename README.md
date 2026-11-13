# 님과 함께

- **앱 이름**: 님과 함께
- **한 줄 소개**: 낯선 길에서 헤매는 고령 운전자에게 차량이 먼저 말을 거는 음성 동반자 앱 (Pleos Connect)
- **실행 방법**: Pleos Connect Emulator v2.1.0에 APK 설치 → 기어 **P**에서 앱 실행·권한 허용 → Virtual Console에서 기어 **D** → 약 17초 뒤 질문에 **'네'** → 지도 앱 집 경로 안내 → 기어 **P**에서 오늘 운전 요약
  ```bash
  adb install -r "apk/둠_님과함께_release.apk"
  adb shell am start -n com.ansimdrive.pleos/.MainActivity
  ```
- **APK 위치**: [`apk/둠_님과함께_release.apk`](apk/둠_님과함께_release.apk) — **이 APK는 데모 좌표 재생 빌드**입니다. 실제 위치 대신 `assets/demo_route.json` 좌표를 1초 간격으로 재생해, 에뮬레이터에서 운전하지 않아도 기어 D 후 약 17초 뒤 헤맴 감지를 볼 수 있습니다. 대답은 화면의 큰 **네 / 아니요** 버튼으로 합니다. (빌드 옵션: [릴리스 APK](#릴리스-apk))

## 소개

같은 길을 반복해서 지나는 패턴을 감지하면 "같은 길을 몇 번 지나셨어요. 집으로 안내할까요?"라고 묻고, 대답하면 내비게이션에 집 경로를 요청합니다. 주행 중 화면은 최소화하고, 정차하면 오늘 운전 요약을 큰 글씨로 보여 줍니다.

> 시연 영상: (링크 예정)

## 시연 시나리오

1. 앱 실행 → 기어 **D** → 위치 추적 시작, 화면은 어두운 배경에 "운전 중" 한 줄
2. 같은 150m 격자(geohash 7)에 10분 안에 3번 진입하거나, 1km 이상 달린 뒤 출발점 200m 안으로 돌아오면 헤맴으로 판정
3. 음성 질문: "같은 길을 몇 번 지나셨어요. 집으로 안내할까요?" (TTS, 또는 **녹음 음성 안내**)
4. "네 / 응 / 그래" → NaviHelper `requestRoute(집)`. 질문과 함께 "말씀하시거나 아래 버튼을 눌러 주세요"와 큰 **네 / 아니요** 버튼도 바로 표시
5. 기어 **P** → 오늘 주행거리 · 급제동 횟수 · 밤 운전 여부 요약

## 빌드와 실행

요구사항: JDK 17, Android SDK(platform 35), Pleos Connect Emulator v2.1.0

```bash
# 일반 빌드 (위치: NaviHelper → 5초 안에 응답 없으면 GPS)
./gradlew assembleDebug

# 데모 빌드 (assets/demo_route.json: 미니 회전교차로를 4바퀴 도는 좌표를 1초 간격으로 재생, 약 17초 뒤 헤맴 감지)
./gradlew assembleDebug -PdemoMode=true

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ansimdrive.pleos/.MainActivity
adb logcat -s AnsimDrive
```

- 시스템 JDK가 17이 아니면 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew ...`처럼 지정합니다.
- **첫 실행은 기어 P에서** 합니다. 주행 중에는 시스템 권한 창(속도·위치·마이크)이 차단됩니다.
- 기어·속도·브레이크는 Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/)에서 바꿉니다.
- 단위 테스트: `./gradlew testDebugUnitTest` (geohash, 헤맴 감지, 데모 경로 트리거 시간)
- 음성만 빠르게 확인(디버그 빌드): `adb shell am start -S -n com.ansimdrive.pleos/.MainActivity --ez debug_ask true`
- 음성 인식 엔진: debug 빌드는 기기 내 Vosk(모델 별도 설치, [docs/voice-lines.md](docs/voice-lines.md)), release는 Android 표준 인식. `-PsttEngine=vosk|system`으로 바꿀 수 있습니다. system 엔진 빌드에는 Vosk 모델·라이브러리를 넣지 않습니다.

## 릴리스 APK

`apk/둠_님과함께_release.apk`는 **데모 좌표 재생 빌드**입니다 (`-PdemoMode=true -PsttEngine=system`). 실제 위치 대신 데모 좌표를 재생하고, 음성 인식은 Android 표준 인식(에뮬레이터에서는 결과가 오지 않아 버튼으로 대답)입니다. 녹음 음성 파일은 넣지 않았으므로 질문은 화면 문구와 버튼으로 나옵니다.

```bash
./gradlew clean assembleRelease -PdemoMode=true -PsttEngine=system
cp app/build/outputs/apk/release/app-release.apk "apk/둠_님과함께_release.apk"
```

서명 정보는 저장소 루트의 `keystore.properties`(커밋하지 않음)에서 읽습니다. 파일이 없으면 서명 없는 APK가 만들어집니다.

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

debug 빌드가 설치된 에뮬레이터에는 서명이 달라 덮어쓸 수 없으니 먼저 `adb uninstall com.ansimdrive.pleos`를 실행합니다.

## 녹음 음성 안내

앱이 하는 말 7문장을 미리 녹음해 `app/src/main/res/raw/`에 넣으면 그 녹음으로 안내합니다. 파일 이름과 문장은 [docs/voice-lines.md](docs/voice-lines.md)에 있습니다. 설정값으로 시스템 TTS와 녹음 음성 중 우선할 것을 고를 수 있게 만들어 두었습니다(설정 화면은 2차). TTS 엔진이 없으면 기본 설정에서도 녹음을 재생합니다.

## 폴더 구조

```
app/src/main/java/com/ansimdrive/pleos/
  MainActivity.kt              기어에 따라 화면 전환, 감지·음성·내비 연결
  ui/DrivingFragment.kt        주행 중 최소 화면 + 질문/버튼
  ui/ParkedFragment.kt         정차 중 큰 글씨 요약
  vehicle/CarPropertyRepo.kt   AAOS Car API: 속도·기어
  vehicle/PleosVehicleRepo.kt  Pleos Vehicle SDK: 브레이크·로우빔
  vehicle/HardBrakeDetector.kt 급제동 (브레이크 ON + 초당 11km/h 이상 감속)
  location/LocationSource.kt   NaviHelper / LocationManager / 데모 위치 소스
  detection/                   Geohash, WanderingDetector, DetectionConfig(임계값 모음)
  voice/VoiceAssistant.kt      TTS·녹음 재생, 대답 판정
  voice/SpeechInput.kt         듣기 엔진 인터페이스 + Android SpeechRecognizer(실차용)
  voice/VoskSpeechInput.kt     Vosk 기기 내 인식(에뮬레이터용)
  voice/MicCheck.kt            디버그 전용 마이크 점검
  voice/VoiceLine.kt           앱이 말하는 문장 목록 (문자열 ↔ 녹음 파일)
  voice/VoiceSettings.kt       TTS / 녹음 음성 선택 설정
  navi/NaviHelperRepo.kt       집 좌표, 경로 상태, 경로 요청
  data/DriveDatabase.kt        Room: TripEvent(급제동·헤맴·야간 분), DailySummary(하루 합계)
  data/DriveLog.kt             기록 저장·요약 읽기 (이번 주 vs 지난주)
  data/WeeklyStatus.kt         이번 주 상태(맑음/흐림/비) 규칙
  data/AppSettings.kt          설정 (가족 공유 상태)
app/src/main/assets/demo_route.json
app/src/main/res/raw/          녹음 음성 파일 자리
docs/pleos-sdk-notes.md        SDK 사용법과 에뮬레이터에서 확인한 동작
docs/voice-lines.md            녹음할 문장과 파일 이름
docs/pleos-support-tts.md      TTS·STT 문제 재현 정리 (Pleos 기술지원 문의용)
docs/day2-stt.md               STT 재시도 기록
```

## 사용 SDK·권한

| 용도 | SDK·API |
|---|---|
| 속도, 기어 | AAOS `CarPropertyManager` (`PERF_VEHICLE_SPEED`, `GEAR_SELECTION`) |
| 브레이크, 헤드라이트 | Pleos Vehicle SDK 2.3.2 (`Brake`, `Light`의 로우빔) |
| 위치, 집 좌표, 경로 | Pleos NaviHelper SDK 3.0.5 (`getCurrentLocationInfo`, `getBookmarkInfo`, `getRouteStateInfo`, `requestRoute`) |
| 위치 대체 | Android `LocationManager` (GPS) — NaviHelper 응답이 없을 때 |
| 음성 출력 | Android `TextToSpeech`, 없으면 녹음 음성(`MediaPlayer`) |
| 음성 인식 | 실차: Android `SpeechRecognizer` / 에뮬레이터(debug): Vosk 0.3.75 기기 내 인식 |
| 저장 | Room 2.6.1 (로컬 SQLite) |

서버 통신은 없습니다.

| 권한 | 보호 수준 | 쓰는 곳 |
|---|---|---|
| `android.car.permission.CAR_SPEED` | 위험(실행 중 요청) | 속도 → 급제동·주행거리 |
| `android.car.permission.CAR_POWERTRAIN` | 일반 | 기어 → 화면 전환 |
| `pleos.car.permission.CAR_DRIVE` | 일반 | 브레이크 페달 → 급제동 |
| `pleos.car.permission.CAR_LIGHTS` | 일반 | 로우빔 → 야간 주행, 출발 전 한마디 |
| `pleos.car.permission.NAVI_ROUTE` | 일반 | 현재 위치, 집 북마크, 경로 안내 상태 |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | 일반 | 집으로 경로 요청 |
| `android.permission.ACCESS_FINE_LOCATION` | 위험(실행 중 요청) | GPS 위치 대체 |
| `android.permission.ACCESS_COARSE_LOCATION` | 위험(실행 중 요청) | Android 12 이상에서 FINE과 함께 요청해야 함 |
| `android.permission.RECORD_AUDIO` | 위험(실행 중 요청) | "네/아니요" 음성 인식 |

라이브러리가 자동으로 추가하는 권한: `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`(Vehicle SDK 서비스 연결), `com.ansimdrive.pleos.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`(androidx 내부용). 실행 중 요청 권한은 앱 첫 실행 때 한 번에 묻습니다(기어 P에서).

## 알려진 제한

- **TTS 미연동**: Pleos 에뮬레이터의 TTS 엔진이 Android 표준 `TextToSpeech`로 검색되지 않아 시스템 음성으로 말하지 못합니다. 녹음 음성이 있으면 녹음을, 없으면 화면 문구만 보여 줍니다. ([재현 정리](docs/pleos-support-tts.md))
- **집 주소 수동 등록**: 지도 앱에 집을 직접 등록해야 집 경로를 요청합니다. 등록하지 않으면 "집 주소가 아직 없어요"라고 안내합니다. (데모 빌드는 가상의 집 좌표를 씁니다.)
- **권한은 P에서 먼저 실행**: 주행 중(D)에는 시스템 권한 창이 차단되므로, 첫 실행은 기어 P에서 해서 속도·위치·마이크 권한을 허용해야 합니다.
- **시연용 안내 음성**: 시연 영상의 안내 음성은 macOS 음성 합성으로 만든 임시 파일이며 배포용이 아님. 실차에서는 Pleos TTS SDK 사용. (생성: `scripts/gen_voice.sh`)
- **오프라인 STT(Vosk)**: 오프라인 STT(Vosk) 실험 구현. 잡음을 응답으로 오인식하는 문제가 있어 1차 시연에서는 버튼 응답 사용. (촬영 빌드: `-PsttEngine=system`, 개선은 2차)
- **음성 녹음 파일 미포함**: 음성 녹음 파일은 개인정보 보호를 위해 저장소에 포함하지 않음. 로컬 빌드 시 [docs/voice-lines.md](docs/voice-lines.md)에 따라 추가. (녹음이 없으면 화면 문구로 안내합니다.)

## 에뮬레이터 참고 사항

- **음성 인식**: 에뮬레이터에서 인식 결과가 오지 않습니다. 음성 인식은 4초 뒤 끝나고, 버튼으로 대답합니다.
- **위치**: NaviHelper 위치는 지도 앱 위치로 고정되어 움직이지 않으므로 헤맴 시연은 데모 빌드로 합니다.
- **백그라운드**: 내비가 앞에 나와 있는 동안 앱이 멈춰 있다가, 앱으로 돌아오면 쌓인 기어 변경이 반영되어 요약 화면이 나옵니다.
