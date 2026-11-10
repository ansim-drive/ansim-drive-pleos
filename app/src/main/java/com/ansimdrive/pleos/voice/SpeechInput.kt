package com.ansimdrive.pleos.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ansimdrive.pleos.TAG

/** 짧은 대답 하나를 듣는 STT 엔진. 결과는 메인 스레드에서 오며, 실패·무응답이면 빈 목록이다. */
interface SpeechInput {
    val name: String
    fun listen(onHeard: (List<String>) -> Unit)
    fun cancel()
    fun release()
}

/**
 * 실차용: Android 표준 SpeechRecognizer.
 * Pleos 에뮬레이터의 인식 서비스(AmbRecognitionService)는 콜백을 보내지 않는다 (docs/day2-stt.md).
 */
class SystemSpeechInput(private val context: Context) : SpeechInput {
    override val name = "SpeechRecognizer"
    private var recognizer: SpeechRecognizer? = null

    override fun listen(onHeard: (List<String>) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "STT unavailable")
            onHeard(emptyList())
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                onHeard(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            }

            override fun onError(error: Int) {
                Log.w(TAG, "STT error $error")
                onHeard(emptyList())
            }

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "STT listening")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    override fun cancel() {
        recognizer?.cancel()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}
