# 시연 타임라인 (2026-11-11, 촬영용 빌드)

에뮬레이터 콜드 부팅 → 앱 데이터 초기화 → 촬영용 빌드 설치 후 한 번에 돌린 결과다. 시각은 앱 로그(`adb logcat -v epoch -s AnsimDrive`) 기준이고, 스크린샷은 `docs/screens/`에 있다.

## 촬영용 빌드 옵션

```bash
scripts/gen_voice.sh                                         # 안내 음성 7개 생성 (res/raw, 저장소 미포함)
./gradlew clean assembleDebug -PdemoMode=true -PsttEngine=system   # 레이아웃을 바꾼 뒤에는 clean 필수
```

| 옵션 | 값 | 이유 |
|---|---|---|
| `-PdemoMode` | `true` | 데모 좌표(미니 회전교차로)를 재생해 약 17초 뒤 헤맴 감지 |
| `-PsttEngine` | `system` | 버튼으로 대답. Vosk는 잡음을 "네"로 오인식해 1차 시연에서 쓰지 않음. 에뮬레이터의 표준 인식은 결과를 주지 않아 4초 뒤 버튼으로 넘어감 |
| `-Pweather` | 생략(`clear`) | 비 문장("비가 와요…")을 찍으려면 `rain` |

## 촬영 전 초기화 순서

1. 에뮬레이터 콜드 부팅: Android Studio → Device Manager → Pleos Connect v2.1.0 → ▶ (이 AVD는 항상 콜드 부팅).
2. 연결 확인:
   ```bash
   adb devices                                   # emulator-5554  device
   adb shell getprop sys.boot_completed          # 1
   ```
3. 지도 앱에 경로 안내가 남아 있으면 끈다 (콜드 부팅 뒤에도 남아 있었음). 안내 중 화면일 때만 누른다:
   ```bash
   adb shell am start -n ai.umos.maps.android.navigation.app/.MainActivity
   adb shell input tap 1217 1226                 # "안내 종료" (2560×1440)
   ```
4. 음성 생성·빌드·앱 초기화·설치:
   ```bash
   scripts/gen_voice.sh
   ./gradlew clean assembleDebug -PdemoMode=true -PsttEngine=system
   adb shell pm clear --user 10 com.ansimdrive.pleos
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. Virtual Console: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ → Gear **P**, Exterior Lights Mode **Low beam** (출발 전 한마디용).
6. 앱 실행 (기어 P): `adb shell am start -n com.ansimdrive.pleos/.MainActivity` → 권한 창에서 허용 → 앱이 **스스로 다시 시작**되어 요약 화면이 나온다.
7. 녹화 시작 → Virtual Console에서 기어 **D** → 아래 타임라인대로 진행 → 질문에서 **'네' 버튼** → 기어 **P** → 앱 아이콘을 눌러 요약.

## 준비 (기어 P, 앱 실행을 0초로)

| 시각 | 단계 | 스크린샷 |
|---|---|---|
| 0.0초 | 앱 실행 | |
| 4.0초 | 위치·속도 권한 창 | `01-permission.png` |
| 8.9초 | 권한 4개 허용 → 0.04초 뒤 앱이 새로 시작 | |
| 15.7초 | 오늘 운전 요약 (빈 상태) | `02-parked-before.png` |

## 주행 (기어 D를 0초로)

| 시각 | 화면 | 음성 | 스크린샷 |
|---|---|---|---|
| 0.0초 | "운전 중" + 감지 아이콘 초록 | 🔊 0.0~2.7초 "어두워졌어요. 천천히 가세요." | `03-driving.png` (5.0초) |
| 17.0초 | 헤맴 감지 → 질문 + 네/아니요 버튼 | 🔊 17.0~20.8초 "같은 길을 몇 번 지나셨어요. 집으로 안내할까요?" | `04-question.png` (19.5초) |
| 20.8~24.8초 | (듣기 4초, 결과 없음 → 버튼 대기) | | |
| 26.6초 | '네' 버튼 → 집 경로 요청 | 🔊 26.6~28.6초 "집으로 안내를 시작할게요." | |
| 31.1초 | 지도 앱이 집 경로(2.4km) 안내 시작 | | `05-route.png` (34.5초) |
| 35.0초 | 기어 P → 주행 저장 (0.37km, 야간 1분) | | |
| 42.1초 | 앱으로 돌아오면 오늘 운전 요약 (0.4km, 급제동 0회, 밤 운전 있음, 이번 주 맑음) | | `06-summary.png` |

'네' 버튼은 스크립트가 듣기가 끝난 뒤 누른 시각이다. 질문이 뜬 뒤(17초)부터는 언제든 누를 수 있다.

## 앞선 리허설에서 고친 것

- 권한 창을 띄운 실행은 기어 D에서 Pleos 홈 화면이 "주행 중에는 사용할 수 없습니다"로 막았다 → 권한 결과를 받으면 앱이 새 태스크로 스스로 다시 시작하도록 고쳤고, 이번 리허설에서 막힘 없이 진행됐다.
- Vosk가 아무도 말하지 않았는데 잡음을 "네"로 들어 경로를 요청했다(2회) → 촬영은 버튼 전용 빌드로 한다.

## 촬영 뒤

- 지도 앱의 집 경로 안내가 남는다. 다음 촬영 전에 3번으로 끈다(남아 있으면 헤맴 감지가 쉬고 아이콘이 회색).
- 앱 DB에 주행 기록이 남는다. 깨끗한 요약으로 다시 찍으려면 4번의 `pm clear`부터 반복한다.

## 음성 없는 공개 APK (2026-11-13)

`apk/둠_님과함께_release.apk`는 안내 음성 파일이 없어 소리 없이 화면 글씨로 안내한다. `07-text-fallback.png` 위: 기어 D 직후 출발 전 한마디 자막(로우빔 ON), 아래: 헤맴 질문 화면.
