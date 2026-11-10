# 2일차 STT 재시도 (2026-11-10)

1시간 제한. 표기: ✅ 에뮬레이터에서 직접 확인 / 🔸 추정

## 0단계. 마이크 경로

디버그 전용 `MicCheck`(AudioRecord, 16kHz 모노, 3초 구간)로 측정했다. `--ei debug_mic <구간 수>`로 실행하고, 녹음은 `files/mic_check.wav`로 저장된다.

| 조건 | 결과 |
|---|---|
| 마이크 입력 | ✅ 살아 있음. 진폭이 0이 아님 (구간별 최대 9037 / 3017 / 2383, RMS 646 / 450 / 372), `silenced=false` |
| 바닥 소음 | ✅ 100ms 단위 RMS가 350~540으로 거의 일정함 (클리핑 0) |
| Mac 스피커로 `say` 재생 | ✅ 바닥 소음보다 RMS가 100 정도만 오름 → 스피커→마이크 경로로는 인식 시험이 의미 없음 |

→ 사람이 Mac 마이크에 직접 말하는 시험이 필요하다 (아래 "남은 시험").

## 1단계. Pleos 시스템 인식 서비스 (`ai.umos.ambientai/.service.AmbRecognitionService`)

- ✅ 매니페스트: `exported=false`, `android.speech.RecognitionService` + `CATEGORY_DEFAULT`, meta-data `android.speech` 있음. 앱 자체는 `CAPTURE_AUDIO_HOTWORD` 권한으로 호출어 녹음(`src:HOTWORD`)을 계속 열어 둔다.
- ✅ dex 확인: `onStartListening(Intent, Callback)`은 로그 한 줄을 찍고 바로 `return`한다. `Callback`(결과·에러 전달)을 한 번도 호출하지 않는 빈 구현이다.
- 결론: **인식 안 됨 (구조적).** `EXTRA_PARTIAL_RESULTS`, `EXTRA_CALLING_PACKAGE` 같은 옵션은 서비스가 읽지 않으므로 재시도하지 않았다. 호출어 선행 조건 같은 코드도 이 서비스에는 없다.

## 2단계. Vosk 기기 내 인식으로 교체

구현:
- `voice/SpeechInput.kt`: 듣기 엔진 인터페이스 + `SystemSpeechInput`(기존 SpeechRecognizer, 실차용)
- `voice/VoskSpeechInput.kt`: Vosk 0.3.75 + `vosk-model-small-ko-0.22`, 문법(grammar) 모드
- 선택: `BuildConfig.STT_ENGINE` — debug 기본 `vosk`, release 기본 `system`, `-PsttEngine=vosk|system`으로 변경. 모델이 assets에 없으면 `system`으로 대신한다.
- 모델은 저장소에 넣지 않는다 (`.gitignore`). 넣는 방법은 `docs/voice-lines.md`.

확인한 것:
- ✅ 모델 복사(첫 실행 약 2초) + 로딩(약 0.6초) 정상. APK 크기 약 147MB(모델 포함).
- ✅ 이 한국어 소형 모델에는 `[unk]`(모르는 말) 토큰이 없다 (`Ignoring word missing in vocabulary: '[unk]'`). 그래서 대답 단어만 넣은 문법은 어떤 소리든 대답 단어로 끼워 맞춘다. 대답이 아닌 흔한 단어 20개(미끼 단어)를 문법에 같이 넣었다.
- ✅ 배경 소음 때문에 발화 끝이 잘 안 잡혀, 3초에 멈추고 최종 결과를 받도록 했다. 부분 결과는 오인식이 많아 확정에 쓰지 않는다.

Mac 스피커 재생 시험 결과 (조건이 나빠 참고용):

| 말한 것 | 문법 모드 | 자유 인식 모드 |
|---|---|---|
| 네 | 결과 없음 | "는군요" |
| 아니요 | **"그래" → YES (오인식)** | "그녀" |
| 응 | 결과 없음 | 결과 없음 |
| 그래 | 결과 없음 | "는 링 장소는" |
| 집으로 가 주세요 | 결과 없음 | 결과 없음 |
| (무음) | 결과 없음 | "승준" |

- 판정: **보류.** 스피커 재생은 소음과 거의 구분되지 않아 정확도를 판단할 수 없다.
- 🔸 위험: 문법 모드는 "아니요"를 YES로 잘못 듣는 경우가 있었다. 시연 중 오인식이 나면 원하지 않는 경로 요청이 나갈 수 있다.

## 남은 시험

사람 목소리로 같은 표를 다시 채운다. 결과에 따라:
- 문법 모드가 정확하면 → 그대로 시연에 쓴다.
- 오인식(특히 YES)이 나오면 → 시연 빌드는 `-PsttEngine=system`(버튼으로 대답)으로 찍는다.
