# 녹음 음성 안내 — 녹음 문장 목록

앱이 말하는 문장 7개를 미리 녹음해 넣는 기능이다. 녹음 파일은 `app/src/main/res/raw/`에 아래 이름으로 넣고 다시 빌드하면 된다. 코드 수정은 필요 없다.

| 파일 이름 | 문장 | 언제 |
|---|---|---|
| `voice_ask_route_home` | 같은 길을 몇 번 지나셨어요. 집으로 안내할까요? | 헤맴을 감지했을 때 |
| `voice_routing_home` | 집으로 안내를 시작할게요. | "네" → 집 경로 요청 성공 |
| `voice_no_home` | 집 주소가 아직 없어요. 내비게이션에 집을 등록해 주세요. | "네"인데 집 주소 미등록 |
| `voice_declined` | 알겠어요. 천천히 조심해서 가세요. | "아니요" |
| `voice_depart_dark` | 어두워졌어요. 천천히 가세요. | P→D 출발 때 헤드라이트가 켜져 있음 |
| `voice_depart_rain` | 비가 와요. 천천히 가세요. | P→D 출발 때 날씨가 비 (1차는 더미 값 `-Pweather=rain`) |
| `voice_depart_dark_rain` | 어둡고 비가 와요. 천천히 가세요. | 위 두 조건이 모두 맞음 |

문장은 `app/src/main/res/values/strings.xml`과 같다. 문장을 바꾸면 두 곳을 같이 고친다.

## 시연용 음성 만들기 (macOS)

녹음 대신 macOS 음성 합성으로 시연용 임시 파일을 만들 수 있다. 배포용이 아니다.

```bash
scripts/gen_voice.sh             # 위 표의 7개 문장을 say -v Yuna로 합성 → res/raw/<파일명>.m4a
VOICE=Eddy scripts/gen_voice.sh  # 다른 한국어 음성
```

표의 문장을 읽어서 만들기 때문에, 문장을 바꾸면 이 표와 `strings.xml`을 고친 뒤 다시 실행한다.

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
| `RECORDED` | 녹음 → 그 문장 녹음이 없으면 TTS → 둘 다 없으면 화면 문구만 |

Pleos 에뮬레이터는 TTS 엔진이 표준 API로 잡히지 않아(`docs/pleos-support-tts.md`), 기본 모드에서도 녹음이 재생된다.

재생은 `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`(내비 안내음과 같은 채널)로 하고, 재생하는 동안 다른 소리를 줄이도록 오디오 포커스(transient, may duck)를 요청한다.

## 확인 방법 (디버그 빌드)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -S -n com.ansimdrive.pleos/.MainActivity --ez debug_ask true   # 2초 뒤 질문 재생
adb logcat -s AnsimDrive   # "Say ASK_ROUTE_HOME (... recording=true)" → "Recording done"
```

## 저장소에 올리지 않음

녹음 파일은 실제 사람 목소리라 개인정보로 다룬다. `.gitignore`에 `app/src/main/res/raw/voice_*`가 들어 있어 커밋되지 않으며, 로컬 빌드에만 포함된다. 저장소를 받아 빌드하면 녹음 없이 화면 문구로 안내한다.

## 음성 인식 모델 (Vosk, 에뮬레이터용)

Pleos 에뮬레이터의 시스템 음성 인식은 결과를 주지 않아(`docs/day2-stt.md`), 디버그 빌드는 기기 안에서 도는 Vosk로 "네/응/그래/아니요"를 듣는다. 모델은 크기(약 82MB) 때문에 저장소에 넣지 않는다 (`.gitignore`).

1. 내려받기: https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip (모델 목록: https://alphacephei.com/vosk/models)
2. 압축을 풀고 폴더 이름을 `vosk-model-ko`로 바꿔 아래처럼 넣는다:
   ```
   app/src/main/assets/vosk-model-ko/
     am/final.mdl
     conf/
     graph/
     ivector/
     README
   ```
3. 빌드: `./gradlew assembleDebug -PdemoMode=true` (debug는 기본이 Vosk). 실차용 표준 인식으로 빌드하려면 `-PsttEngine=system`.
4. 확인: `adb logcat -s AnsimDrive`에 `STT engine: Vosk`, `Vosk model ready`가 나오면 정상. 모델이 없으면 `STT engine: SpeechRecognizer`로 대신한다.

라이선스는 모델 목록 페이지에 표시된 내용을 확인한다.

