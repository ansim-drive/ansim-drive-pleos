package com.ansimdrive.pleos.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ansimdrive.pleos.TAG
import java.util.Locale

/**
 * Android 표준 TTS + STT 래퍼. 모든 콜백은 메인 스레드에서 호출된다.
 * (Pleos TTS/STT SDK는 1차 범위에서 사용하지 않는다.)
 */
class VoiceAssistant(private val context: Context) {

    enum class Answer { YES, NO, UNKNOWN }

    private val main = Handler(Looper.getMainLooper())
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val pendingDone = mutableMapOf<String, () -> Unit>()

    // 엔진에 따라 초기화 콜백이 생성자 안에서 바로(동기) 불릴 수 있어 tts 필드를 콜백에서 직접 쓰지 않는다.
    private val tts: TextToSpeech = TextToSpeech(context) { status -> main.post { onTtsInit(status) } }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finish(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) {
                Log.w(TAG, "TTS error $errorCode")
                finish(utteranceId)
            }
        })
    }

    private fun onTtsInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val lang = tts.setLanguage(Locale.KOREAN)
            Log.i(TAG, "TTS ready (engine=${tts.defaultEngine}, korean=$lang)")
        } else {
            Log.e(TAG, "TTS init failed: $status (engines=${tts.engines.map { it.name }})")
        }
    }

    private fun finish(utteranceId: String) {
        main.post { pendingDone.remove(utteranceId)?.invoke() }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        Log.i(TAG, "TTS: $text")
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready, skipping")
            main.post(onDone)
            return
        }
        val id = "u${System.nanoTime()}"
        pendingDone[id] = onDone
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    /** 질문을 읽고 대답을 듣는다. 인식 실패·불명확하면 [Answer.UNKNOWN]. */
    fun ask(question: String, onAnswer: (Answer) -> Unit) {
        speak(question) { listen(onAnswer) }
    }

    private fun listen(onAnswer: (Answer) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "STT unavailable")
            onAnswer(Answer.UNKNOWN)
            return
        }
        // 인식 서비스가 아무 콜백도 주지 않는 경우가 있어(에뮬레이터) 시간 제한을 둔다.
        var answered = false
        val answerOnce = { a: Answer ->
            if (!answered) {
                answered = true
                main.removeCallbacksAndMessages(LISTEN_TIMEOUT)
                onAnswer(a)
            }
        }
        main.postDelayed({
            Log.w(TAG, "STT timeout")
            recognizer?.cancel()
            answerOnce(Answer.UNKNOWN)
        }, LISTEN_TIMEOUT, LISTEN_TIMEOUT_MS)

        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val answer = parse(heard)
                Log.i(TAG, "STT heard=$heard -> $answer")
                answerOnce(answer)
            }

            override fun onError(error: Int) {
                Log.w(TAG, "STT error $error")
                answerOnce(Answer.UNKNOWN)
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
        Log.i(TAG, "STT start")
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    fun cancel() {
        main.removeCallbacksAndMessages(LISTEN_TIMEOUT)
        recognizer?.cancel()
        if (ttsReady) tts.stop()
        pendingDone.clear()
    }

    fun release() {
        cancel()
        recognizer?.destroy()
        recognizer = null
        tts.shutdown()
    }

    companion object {
        private const val LISTEN_TIMEOUT_MS = 8_000L
        private val LISTEN_TIMEOUT = Any()
        private val NO = listOf("아니", "싫어", "괜찮아", "됐어", "안 해")
        private val YES = listOf("네", "예", "응", "그래", "좋아", "부탁", "해 줘", "해줘")

        /** "아니요"에 "네"가 없도록 부정어를 먼저 본다. */
        fun parse(heard: List<String>): Answer {
            for (text in heard) {
                if (NO.any { it in text }) return Answer.NO
                if (YES.any { it in text }) return Answer.YES
            }
            return Answer.UNKNOWN
        }
    }
}
