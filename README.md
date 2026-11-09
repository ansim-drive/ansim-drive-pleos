# 안심 드라이브

낯선 길에서 헤매는 고령 운전자에게 차량이 먼저 말을 거는 음성 동반자 앱입니다. Pleos Connect(AAOS)용입니다.

같은 길을 반복해서 지나는 패턴을 감지하면 "같은 길을 몇 번 지나셨어요. 집으로 안내할까요?"라고 묻고, 대답하면 내비게이션에 집 경로를 요청합니다. 주행 중 화면은 최소화하고, 정차하면 오늘 운전 요약을 큰 글씨로 보여 줍니다.

> 시연 영상: (링크 예정)

## 시연 시나리오

1. 앱 실행 → 기어 **D** → 위치 추적 시작, 화면은 어두운 배경에 "운전 중" 한 줄
2. 같은 150m 격자(geohash 7)에 10분 안에 3번 진입하거나, 1km 이상 달린 뒤 출발점 200m 안으로 돌아오면 헤맴으로 판정
3. 음성 질문: "같은 길을 몇 번 지나셨어요. 집으로 안내할까요?" (TTS, 또는 **가족 목소리 안내** 녹음)
4. "네 / 응 / 그래" → NaviHelper `requestRoute(집)`. 음성 인식이 안 되면 큰 **네 / 아니요** 버튼으로 대체
5. 기어 **P** → 오늘 주행거리 · 급제동 횟수 · 밤 운전 여부 요약

## 빌드와 실행

요구사항: JDK 17, Android SDK(platform 35), Pleos Connect Emulator v2.1.0

```bash
# 일반 빌드 (위치: NaviHelper → 5초 안에 응답 없으면 GPS)
./gradlew assembleDebug

# 데모 빌드 (assets/demo_route.json 좌표를 1초 간격으로 재생, 약 77초 뒤 헤맴 감지)
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

## 가족 목소리 안내

앱이 하는 말 4문장을 가족이 녹음해 `app/src/main/res/raw/`에 넣으면 그 목소리로 안내합니다. 파일 이름과 문장은 [docs/voice-lines.md](docs/voice-lines.md)에 있습니다. 설정값으로 시스템 TTS와 녹음 음성 중 우선할 것을 고를 수 있게 만들어 두었습니다(설정 화면은 2차). TTS 엔진이 없으면 기본 설정에서도 녹음을 재생합니다.

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
  voice/VoiceAssistant.kt      Android 표준 TTS + STT, 가족 목소리 녹음 재생
  voice/VoiceLine.kt           앱이 말하는 문장 목록 (문자열 ↔ 녹음 파일)
  voice/VoiceSettings.kt       TTS / 가족 목소리 선택 설정
  navi/NaviHelperRepo.kt       집 좌표, 경로 상태, 경로 요청
  data/TripStats.kt            오늘 주행 요약
app/src/main/assets/demo_route.json
app/src/main/res/raw/          가족 목소리 녹음 파일 자리
docs/pleos-sdk-notes.md        SDK 사용법과 에뮬레이터에서 확인한 동작
docs/voice-lines.md            녹음할 문장과 파일 이름
docs/pleos-support-tts.md      TTS 엔진 문제 재현 정리 (Pleos 기술지원 문의용)
```

## 사용 SDK·API

| 용도 | API |
|---|---|
| 속도, 기어 | AAOS `CarPropertyManager` (`PERF_VEHICLE_SPEED`, `GEAR_SELECTION`) |
| 브레이크, 헤드라이트 | Pleos Vehicle SDK 2.3.2 (`Brake`, `Light`) |
| 위치, 집 좌표, 경로 | Pleos NaviHelper SDK 3.0.5 (`getCurrentLocationInfo`, `getBookmarkInfo`, `getRouteStateInfo`, `requestRoute`) |
| 음성 | Android `TextToSpeech`, `SpeechRecognizer` |

서버 통신은 없습니다.

## 알려진 제한

- **TTS 미연동**: Pleos 에뮬레이터의 TTS 엔진이 Android 표준 `TextToSpeech`로 검색되지 않아 시스템 음성으로 말하지 못합니다. 가족 목소리 녹음이 있으면 녹음을, 없으면 화면 문구만 보여 줍니다. ([재현 정리](docs/pleos-support-tts.md))
- **집 주소 수동 등록**: 지도 앱에 집을 직접 등록해야 집 경로를 요청합니다. 등록하지 않으면 "집 주소가 아직 없어요"라고 안내합니다. (데모 빌드는 가상의 집 좌표를 씁니다.)
- **권한은 P에서 먼저 실행**: 주행 중(D)에는 시스템 권한 창이 차단되므로, 첫 실행은 기어 P에서 해서 속도·위치·마이크 권한을 허용해야 합니다.

## 에뮬레이터 참고 사항

- **음성 인식**: 에뮬레이터에서 인식 결과가 오지 않아 8초 후 버튼으로 넘어갑니다.
- **위치**: NaviHelper 위치는 지도 앱 위치로 고정되어 움직이지 않으므로 헤맴 시연은 데모 빌드로 합니다.
- **백그라운드**: 내비가 앞에 나와 있는 동안 앱이 멈춰 있다가, 앱으로 돌아오면 쌓인 기어 변경이 반영되어 요약 화면이 나옵니다.
- 주행 요약은 메모리에만 있어 앱을 종료하면 사라집니다.
