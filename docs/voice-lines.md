# 가족 목소리 안내 — 녹음 문장 목록

앱이 말하는 문장을 가족이 직접 녹음해 넣는 기능이다. 녹음 파일은 `app/src/main/res/raw/`에 아래 이름으로 넣고 다시 빌드하면 된다. 코드 수정은 필요 없다.

| 파일 이름 | 문장 | 언제 |
|---|---|---|
| `voice_ask_route_home` | 같은 길을 몇 번 지나셨어요. 집으로 안내할까요? | 헤맴을 감지했을 때 |
| `voice_routing_home` | 집으로 안내를 시작할게요. | "네" → 집 경로 요청 성공 |
| `voice_no_home` | 집 주소가 아직 없어요. 내비게이션에 집을 등록해 주세요. | "네"인데 집 주소 미등록 |
| `voice_declined` | 알겠어요. 천천히 조심해서 가세요. | "아니요" |

문장은 `app/src/main/res/values/strings.xml`과 같다. 문장을 바꾸면 두 곳을 같이 고친다.

## 파일 규칙

- 확장자: `.m4a`, `.mp3`, `.ogg`, `.wav` 중 하나. 예: `voice_ask_route_home.m4a`
- 파일 이름은 위 표와 정확히 같게 (영문 소문자·숫자·밑줄만). 같은 이름에 확장자만 다른 파일을 두 개 넣으면 빌드가 실패한다.
- 권장: 모노, 44.1kHz 또는 48kHz, 앞뒤 무음은 0.3초 이내로 자르기, 한 문장 5초 이내.
- 질문 녹음(`voice_ask_route_home`)이 끝나면 바로 음성 인식이 시작되므로 끝부분에 잡음이 없게 한다.

## 동작

설정값 `VoiceSettings.mode` (설정 화면 UI는 아직 없음, 기본값 `TTS`):

| 모드 | 순서 |
|---|---|
| `TTS` (기본) | 시스템 TTS → TTS 엔진이 없으면 녹음 → 녹음도 없으면 화면 문구만 |
| `FAMILY` | 녹음 → 그 문장 녹음이 없으면 TTS → 둘 다 없으면 화면 문구만 |

Pleos 에뮬레이터는 TTS 엔진이 표준 API로 잡히지 않아(`docs/pleos-support-tts.md`), 기본 모드에서도 녹음이 재생된다.

재생은 `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`(내비 안내음과 같은 채널)로 하고, 재생하는 동안 다른 소리를 줄이도록 오디오 포커스(transient, may duck)를 요청한다.

## 확인 방법 (디버그 빌드)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -S -n com.ansimdrive.pleos/.MainActivity --ez debug_ask true   # 2초 뒤 질문 재생
adb logcat -s AnsimDrive   # "Say ASK_ROUTE_HOME (... recording=true)" → "Recording done"
```

## 저장소에 올리기 전에

녹음 파일은 가족의 실제 목소리라 GitHub에 올리면 누구나 들을 수 있다. 공개 저장소에 넣을지, `.gitignore`에 `app/src/main/res/raw/voice_*`를 추가해 로컬 빌드에만 쓸지 정하고 넣는다.
