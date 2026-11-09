# Standard `TextToSpeech` cannot find the TTS engine on Pleos Connect Emulator v2.1.0

## Summary

On Pleos Connect Emulator v2.1.0, a third-party app that uses the standard Android `android.speech.tts.TextToSpeech` API cannot find any TTS engine. Initialization fails immediately with `ERROR (-1)` and `TextToSpeech.getEngines()` returns an empty list.

The only TTS engine on the image is `ai.pleos.playground.caas/.service.speech.SpeechService`. Its `TTS_SERVICE` intent-filter has no `android.intent.category.DEFAULT` category. We believe this is the cause, because the framework looks up engines with `PackageManager.MATCH_DEFAULT_ONLY`.

## Environment

| Item | Value |
|---|---|
| Emulator | Pleos Connect Emulator v2.1.0 (AVD, Automotive, cold boot) |
| System image | `system-images/android-34/pleos_car_emulator/arm64-v8a` |
| Build fingerprint | `Pleos/crp_avd_arm64/crp_avd_arm64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys` |
| Android | 14 (API 34), `user` build |
| TTS engine package | `ai.pleos.playground.caas` versionName `4.2.10-coral-aosp`, versionCode `4002010` (`/system_ext/app/AmbientAiService/AmbientAiService.apk`) |
| Test app | minSdk 28, targetSdk 35, runs as user 10 |
| Host | macOS, Apple Silicon |

## Steps to reproduce

1. Declare package visibility for TTS engines in the app manifest:
   ```xml
   <queries>
       <intent>
           <action android:name="android.intent.action.TTS_SERVICE" />
       </intent>
   </queries>
   ```
2. Create a `TextToSpeech` instance and log the result:
   ```kotlin
   val tts = TextToSpeech(context) { status ->
       // status == TextToSpeech.ERROR (-1)
   }
   Log.i(TAG, "engines=${tts.engines.map { it.name }}")   // []
   ```
3. Passing the engine package explicitly, `TextToSpeech(context, listener, "ai.pleos.playground.caas")`, gives the same result (tested: `-1`, `engines=[]`). The framework checks that the requested engine is installed with the same lookup.

## Expected

`TextToSpeech` binds to the system TTS engine (`ai.pleos.playground.caas`), and `onInit` returns `SUCCESS`.

## Actual

`onInit` is called synchronously from inside the constructor with `ERROR (-1)`. No bind attempt is logged.

App log:

```
E AnsimDrive: TTS init failed: -1 (engines=[])
```

The service exists and is visible to user 10:

```
$ adb shell cmd package query-services --user 10 -a android.intent.action.TTS_SERVICE
  packageName=ai.pleos.playground.caas
  name=ai.pleos.playground.caas.service.speech.SpeechService
```

`tts_default_synth` is not set:

```
$ adb shell settings get secure tts_default_synth
null
```

## Analysis

From `aapt2 dump xmltree --file AndroidManifest.xml AmbientAiService.apk`, the service declaration is:

```
service  android:name="ai.pleos.playground.caas.service.speech.SpeechService"  android:exported=true
  intent-filter
    action  ai.pleos.playground.caas.intent.action.SPEECH_START
    action  ai.pleos.playground.caas.broadcast.LOG_LEVEL_CHANGE
    action  ai.pleos.playground.caas.broadcast.SERVER_TYPE_CHANGE
    action  ai.pleos.playground.caas.broadcast.CONNECT_RELAY_ACTIVATION
    action  ai.pleos.playground.caas.broadcast.CONNECT_RELAY_DEACTIVATION
    action  android.intent.action.TTS_SERVICE
    (no <category android:name="android.intent.category.DEFAULT" />)
```

The AOSP `TtsEngines.getEngines()` looks up engines with:

```java
pm.queryIntentServices(new Intent(Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_DEFAULT_ONLY);
```

With `MATCH_DEFAULT_ONLY`, only intent-filters that include `CATEGORY_DEFAULT` match. So the `caas` service is never returned, and `TextToSpeech` has no engine to bind to. Standard engines such as Pico TTS declare `<category android:name="android.intent.category.DEFAULT" />` in the same filter. The filter also has no `android.speech.tts` meta-data, which standard engines include.

## Questions

1. Is the `caas` `SpeechService` meant to be usable through the standard `android.speech.tts.TextToSpeech` API by third-party apps?
2. If yes, could `android.intent.category.DEFAULT` (and, if needed, the `android.speech.tts` meta-data) be added to its `TTS_SERVICE` intent-filter in a future emulator image and in vehicle builds?
3. If no, what is the recommended way for third-party apps to produce speech output on Pleos Connect today? When will the Pleos TextToSpeech SDK be available to apps?
4. Does the answer differ between the emulator and production vehicles?
