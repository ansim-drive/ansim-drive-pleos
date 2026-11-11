package com.ansimdrive.pleos.voice

import android.content.Context
import androidx.core.content.edit

/**
 * 안내 음성 설정. 설정 화면 UI는 아직 없고 저장 구조만 둔다.
 * - [Mode.TTS] (기본): 시스템 TTS. TTS 엔진이 없으면 녹음 음성으로 대체.
 * - [Mode.RECORDED]: 녹음 음성 우선. 녹음이 없는 문장만 TTS.
 */
class VoiceSettings(context: Context) {

    enum class Mode { TTS, RECORDED }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var mode: Mode
        get() = prefs.getString(KEY_MODE, null)
            ?.let { saved -> Mode.entries.firstOrNull { it.name == saved } }
            ?: Mode.TTS
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    private companion object {
        const val KEY_MODE = "voice_mode"
    }
}
