package com.ansimdrive.pleos.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ansimdrive.pleos.TAG
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import kotlin.concurrent.thread

/**
 * 에뮬레이터용: Vosk 기기 내 인식. 대답 단어만 고르는 문법(grammar) 모드라 작은 모델로도 빠르다.
 * 모델은 assets/vosk-model-ko (저장소 미포함, docs/voice-lines.md). 첫 실행 때 내부 저장소로 복사한다.
 */
class VoskSpeechInput(private val context: Context) : SpeechInput {
    override val name = "Vosk"
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var service: SpeechService? = null

    init {
        thread(name = "VoskModel") {
            try {
                val dir = unpackModel()
                val loaded = Model(dir.absolutePath)
                main.post { model = loaded }
                Log.i(TAG, "Vosk model ready")
            } catch (e: Exception) {
                Log.e(TAG, "Vosk model load failed", e)
            }
        }
    }

    override fun listen(onHeard: (List<String>) -> Unit) {
        val m = model ?: run {
            Log.w(TAG, "Vosk model not ready")
            onHeard(emptyList())
            return
        }
        cancel()
        val s = try {
            val rec = if (useGrammar) Recognizer(m, SAMPLE_RATE, GRAMMAR) else Recognizer(m, SAMPLE_RATE)
            rec.setWords(true)
            SpeechService(rec, SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "Vosk start failed", e)
            onHeard(emptyList())
            return
        }
        service = s
        // 배경 소음이 있으면 발화 끝을 못 잡아 결과가 늦으므로, 정해진 시간에 멈추고 최종 결과를 받는다.
        main.postDelayed({ service?.stop() }, STOP, LISTEN_MS)
        // 콜백은 SpeechService가 메인 스레드로 보내 준다. 부분 결과는 오인식이 많아 확정에 쓰지 않는다.
        s.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                Log.v(TAG, "Vosk partial: ${hypothesis.replace("\n", " ")}")
            }
            override fun onResult(hypothesis: String) = heard(hypothesis)
            override fun onFinalResult(hypothesis: String) = heard(hypothesis)
            override fun onError(exception: Exception) {
                Log.w(TAG, "Vosk error", exception)
                onHeard(emptyList())
            }
            override fun onTimeout() = onHeard(emptyList())

            private fun heard(json: String) {
                Log.d(TAG, "Vosk result: ${json.replace("\n", " ")}")
                val text = JSONObject(json).optString("text").trim()
                if (text.isNotEmpty()) onHeard(listOf(text))
            }
        })
    }

    override fun cancel() {
        main.removeCallbacksAndMessages(STOP)
        service?.let {
            it.cancel()
            it.shutdown()
        }
        service = null
    }

    override fun release() {
        cancel()
        model?.close()
        model = null
    }

    /** assets/vosk-model-ko → filesDir/vosk-model-ko. 크기가 같으면 다시 복사하지 않는다. */
    private fun unpackModel(): File {
        val target = File(context.filesDir, MODEL_ASSET)
        val marker = File(target, ".copied")
        val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (marker.exists() && marker.readText() == stamp) return target
        target.deleteRecursively()
        copyAssets(MODEL_ASSET, target)
        marker.writeText(stamp)
        Log.i(TAG, "Vosk model copied to $target")
        return target
    }

    private fun copyAssets(path: String, dest: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        children.forEach { copyAssets("$path/$it", File(dest, it)) }
    }

    companion object {
        private const val MODEL_ASSET = "vosk-model-ko"
        private const val SAMPLE_RATE = 16_000f
        private const val LISTEN_MS = 3_000L      // VoiceAssistant의 4초 제한 안에서 최종 결과를 받도록
        private val STOP = Any()

        private val ANSWERS = listOf("네", "예", "응", "그래", "아니요", "아니")

        /**
         * 이 한국어 모델에는 [unk](모르는 말) 토큰이 없어, 대답 단어만 넣으면 어떤 소리든 대답으로 끼워 맞춘다.
         * 대답이 아닌 흔한 말(미끼 단어)을 같이 넣어 다른 말은 그쪽으로 빠지게 한다.
         */
        private val DECOYS = listOf(
            "집", "가", "주세요", "어", "음", "뭐", "왜", "몰라", "여기", "저기",
            "지금", "그냥", "좀", "길", "어디", "이거", "그거", "있어", "없어", "괜찮아",
        )
        private val GRAMMAR = JSONArray(ANSWERS + DECOYS).toString()

        fun isModelBundled(context: Context) = !context.assets.list(MODEL_ASSET).isNullOrEmpty()

        /** 실험용: false면 문법 없이 전체 어휘로 인식한다. */
        var useGrammar = true
    }
}
