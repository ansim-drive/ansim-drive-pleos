package com.ansimdrive.pleos.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.ansimdrive.pleos.TAG
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 디버그 전용: 마이크 입력이 실제로 들어오는지 3초씩 녹음해 구간별 최대 진폭을 찍는다.
 * 녹음은 files/mic_check.wav로도 저장한다 (16kHz 모노, 들어서 음질 확인용).
 * adb shell am start -S -n com.ansimdrive.pleos/.MainActivity --ei debug_mic 3
 * adb exec-out run-as com.ansimdrive.pleos --user 10 cat files/mic_check.wav > mic_check.wav
 */
object MicCheck {
    private const val RATE = 16_000
    private const val WINDOW_MS = 3_000L

    @SuppressLint("MissingPermission") // RECORD_AUDIO는 MainActivity에서 받는다
    fun run(context: Context, windows: Int) = thread(name = "MicCheck") {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, RATE),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "MicCheck: AudioRecord init failed")
            return@thread
        }
        val buf = ShortArray(RATE / 10)
        val pcm = ByteArrayOutputStream()
        rec.startRecording()
        val silenced = context.getSystemService(AudioManager::class.java).activeRecordingConfigurations
            .firstOrNull { it.clientAudioSessionId == rec.audioSessionId }?.isClientSilenced
        Log.i(TAG, "MicCheck: recording started ($windows x 3s, state=${rec.recordingState}, silenced=$silenced)")
        repeat(windows) { w ->
            var peak = 0
            var sumSq = 0.0
            var n = 0L
            val end = SystemClock.elapsedRealtime() + WINDOW_MS
            while (SystemClock.elapsedRealtime() < end) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) {
                    val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) bytes.putShort(buf[i])
                    pcm.write(bytes.array())
                }
                for (i in 0 until read) {
                    val v = abs(buf[i].toInt())
                    if (v > peak) peak = v
                    sumSq += v.toDouble() * v
                    n++
                }
            }
            Log.i(TAG, "MicCheck window ${w + 1}: peak=$peak rms=%.0f samples=$n".format(sqrt(sumSq / max(n, 1))))
        }
        rec.stop()
        rec.release()
        val wav = File(context.filesDir, "mic_check.wav")
        writeWav(wav, pcm.toByteArray())
        Log.i(TAG, "MicCheck: done, saved $wav")
    }

    private fun writeWav(file: File, data: ByteArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }
        file.outputStream().use {
            it.write(header.array())
            it.write(data)
        }
    }
}
