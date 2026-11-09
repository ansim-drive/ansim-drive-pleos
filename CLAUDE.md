# CLAUDE.md — 안심 드라이브 (Pleos Connect 앱)

이 파일은 Claude Code가 이 저장소에서 작업할 때 항상 따라야 하는 규칙이다. 사용자는 Android/Kotlin 경험이 거의 없고 최종 확인만 한다. 그러므로 **설명은 짧게, 실행은 끝까지, 검증은 스스로** 한다.

## 1. 프로젝트 한 줄

70대 이상 고령 운전자가 낯선 길에서 헤맬 때 차량이 먼저 말을 거는 음성 동반자 앱. 현대차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작. 1차 접수 마감 **2026-11-15 23:59**. 1차 제출물은 기획서 + 에뮬레이터 시연 영상 + 이 GitHub 저장소.

## 2. 절대 규칙 (어기면 실격 위험)

- **개인정보·실명 금지**: 코드, 주석, README, 커밋 메시지, 패키지명, 문자열 어디에도 개발자 실명·학교·학번·이메일을 쓰지 않는다. 패키지명은 `com.ansimdrive.pleos`로 고정.
- **비밀값 커밋 금지**: Pleos Client ID/Secret, 서명 키(.jks/.keystore), 키 비밀번호는 `local.properties`나 `keystore.properties`에만 두고 `.gitignore`에 포함한다. 커밋 전 `git diff --cached`로 비밀값이 없는지 확인한다.
- **커밋 작성자**: 이 저장소는 로컬 git config(`user.name=ansim-drive`, noreply 이메일)를 쓴다. `--global` 설정을 바꾸지 않는다. 커밋 전 `git config user.name`이 `ansim-drive`인지 확인한다.
- **의학적 표현 금지**: 앱 문구·코드 주석에 치매, 인지저하 등 진단성 단어를 쓰지 않는다. "운전 습관 변화"로만 표현한다.

## 3. 기술 스택 (변경 금지)

| 항목 | 값 |
|---|---|
| 언어 | Kotlin |
| minSdk | 28 (Pleos 요구사항, 낮추지 말 것) |
| targetSdk / compileSdk | 35 (Pleos 샘플 기준) |
| Gradle / AGP / Kotlin / JDK | wrapper 8.10.2 / 8.8.2 / 2.0.0 / 17 (Pleos 샘플 기준) |
| UI | Android View + ViewBinding (Compose 금지 — 에뮬레이터 호환성·디버깅 단순화) |
| 테스트 기기 | Pleos Connect Emulator v2.1.0 (AVD, Automotive, Cold boot) |
| 차량 데이터 | Pleos Vehicle SDK(Brake, Light 등) + AAOS 표준 Car API(`VehiclePropertyIds.PERF_VEHICLE_SPEED`, `GEAR_SELECTION`) |
| 위치·경로 | Pleos NaviHelper SDK (`getCurrentLocationInfo`, `getBookmarkInfo`, `requestRoute`) |
| 음성 | **Android 표준** `android.speech.tts.TextToSpeech`, `android.speech.SpeechRecognizer` (Pleos TTS/STT SDK는 2026-11-09 기준 "준비 중"이라 사용 불가) |
| 저장 | Room (로컬 SQLite). 서버 없음. |
| 서버·네트워크 | 1차 범위에 없음. 날씨는 더미 데이터. |

**사용하지 않는 것**: Pleos FusedLocation SDK(미제공), Pleos ADAS SDK(미제공), Pleos TTS/STT/LLM SDK(1차 미사용), Jetpack Compose, 외부 서버.

## 4. 공식 문서 (코드 쓰기 전에 반드시 읽을 것)

Pleos SDK는 학습 데이터에 없을 가능성이 높다. **추측으로 API를 쓰지 말고** 아래 문서를 WebFetch로 읽은 뒤 작성한다. 문서에 없는 메서드·클래스는 만들어 내지 않는다.

> 문서 사이트의 API 레퍼런스는 JS 렌더링이라 WebFetch로 읽히지 않는다. 확인된 SDK 사용법은 `docs/pleos-sdk-notes.md`에 정리돼 있으니 그것을 먼저 보고, 세부 API는 공식 샘플 저장소(`~/dev/pleos-samples/`)와 AAR 시그니처로 확인한다.

- 개발 환경/에뮬레이터: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- 프로젝트·SDK 접근: https://document.pleos.ai/en/docs/pleos-only/create-my-project
- Virtual Console(차량 상태 시뮬레이터): https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- API 호환성 표: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- SDK/API 개요(표준 우선 정책): https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api
- Vehicle SDK: https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro
- NaviHelper SDK: https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro
- 튜토리얼/샘플 앱: https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app

SDK 의존성 추가 방법(Maven 저장소 URL, artifact 좌표)은 위 튜토리얼/샘플 앱 문서에서 확인한다. 문서를 못 찾으면 사용자에게 Pleos Playground 콘솔에서 SDK 다운로드 안내를 확인해 달라고 요청한다.

## 5. 1차 시연 시나리오 (이것만 동작하면 된다)

1. 앱 실행 → 기어 D 감지(Virtual Console에서 변경) → 위치 추적 시작, 화면은 최소화(어두운 배경 + "운전 중" 큰 글씨 1줄)
2. 위치가 같은 150m 격자(geohash 7)에 10분 내 3회 진입 → 헤맴 판정
3. TTS: "같은 길을 몇 번 지나셨어요. 집으로 안내할까요?"
4. STT로 "네/응/그래" 인식 → NaviHelper `requestRoute(집 좌표)` 호출. STT 실패 시 큰 버튼 '네 / 아니요'로 대체
5. 기어 P 감지 → 큰 글씨 요약 화면(오늘 주행거리·급제동 횟수·밤 운전 여부, 더미 데이터 허용)

**위치 소스 우선순위**: ① NaviHelper `getCurrentLocationInfo` ② Android `LocationManager`(GPS_PROVIDER, 에뮬레이터 Extended Controls 위치) ③ 둘 다 에뮬레이터에서 값이 안 바뀌면 **데모 모드**: `assets/demo_route.json`의 좌표 시퀀스를 1초 간격으로 재생. 데모 모드는 설정 화면 토글이 아니라 빌드 플래그(`BuildConfig.DEMO_MODE`)로 켠다. 어떤 소스를 쓰는지 로그로 남긴다.

## 6. 헤맴 감지 사양

- 위치 → geohash precision 7 문자열로 변환(외부 라이브러리 없이 직접 구현, 40줄 이내)
- 슬라이딩 윈도우 10분. 같은 geohash에 **새로 진입**한 횟수가 3 이상이면 트리거 (같은 격자에 머무는 건 1회로 센다 — 신호 대기 오탐 방지)
- 보조 조건: 누적 1km 이상 주행 후 출발점 200m 이내 복귀
- 억제 조건: 내비 경로 활성 중(`getRouteStateInfo`), 집 반경 500m 이내, 속도 10km/h 미만 구간은 윈도우에 넣지 않음
- 트리거 후 15분간 재트리거 금지
- 임계값은 전부 `DetectionConfig` 상수 한 곳에 모은다 (2차에서 튜닝)

## 7. 급제동·야간 정의 (기록 기능, 1차는 로그만)

- 급제동: 한국교통안전공단 eTAS 기준 준용 — 브레이크 ON 상태에서 초당 11km/h 이상 감속. 속도는 `PERF_VEHICLE_SPEED`(m/s → ×3.6), 브레이크는 Vehicle SDK Brake API
- 야간: Vehicle SDK Light API 헤드라이트 ON. 미지원이면 시각(일몰~일출 근사)

## 8. 코드 구조

```
app/src/main/java/com/ansimdrive/pleos/
  MainActivity.kt              # 기어 상태에 따라 DrivingFragment / ParkedFragment 전환
  ui/DrivingFragment.kt        # 주행 중: 최소 화면
  ui/ParkedFragment.kt         # 정차 중: 큰 글씨 요약
  vehicle/CarPropertyRepo.kt   # AAOS Car API: 속도·기어
  vehicle/PleosVehicleRepo.kt  # Pleos Vehicle SDK: 브레이크·라이트
  location/LocationSource.kt   # 인터페이스 + NaviHelper/Android/Demo 구현 3개
  detection/Geohash.kt
  detection/WanderingDetector.kt
  detection/DetectionConfig.kt
  voice/VoiceAssistant.kt      # TTS + STT 래퍼, 콜백 기반
  navi/NaviHelperRepo.kt       # 집 좌표 조회, requestRoute
  data/                        # Room: TripEvent, DailySummary
```

## 9. 작업 방식

- 작업 시작 시 `git status`로 깨끗한지 확인. 기능 하나 끝날 때마다 커밋. 커밋 메시지는 영어 conventional commits (`feat:`, `fix:`, `chore:`, `docs:`).
- 브랜치는 `main` 하나만 쓴다 (혼자, 6일). 영상 촬영 시점에 `v0.1-submission` 태그.
- 빌드는 반드시 `./gradlew assembleDebug`로 검증한 뒤 커밋. 빌드 실패 상태로 커밋하지 않는다.
- 에뮬레이터 설치·실행은 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 후 `adb shell am start -n com.ansimdrive.pleos/.MainActivity`. 로그는 `adb logcat -s AnsimDrive`로 확인 (모든 로그 태그는 `AnsimDrive`).
- 사용자에게 보고할 때: 무엇이 되고 안 되는지 3줄 이내. 에뮬레이터에서 **직접 확인한 것**과 **추정**을 구분해서 쓴다.
- 막히면 30분 이상 혼자 시도하지 말고, 시도한 것과 에러를 정리해 사용자에게 묻는다.

## 10. README 규칙

README.md에는 앱 소개, 시연 시나리오, 빌드 방법, 폴더 구조, 사용 SDK, 알려진 제한(에뮬레이터 위치 등)을 쓴다. 작성자 이름은 쓰지 않는다. 시연 영상 링크 자리를 비워 둔다.
