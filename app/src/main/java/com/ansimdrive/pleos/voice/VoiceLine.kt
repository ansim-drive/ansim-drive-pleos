package com.ansimdrive.pleos.voice

import androidx.annotation.StringRes
import com.ansimdrive.pleos.R

/**
 * 앱이 말하는 문장 목록. 각 문장은 TTS용 문자열과 녹음 음성 파일 이름을 가진다.
 * 녹음 파일: res/raw/<rawName>.(m4a|mp3|ogg|wav) — 없으면 TTS, 그것도 없으면 화면 문구만. (docs/voice-lines.md)
 */
enum class VoiceLine(@StringRes val text: Int, val rawName: String) {
    ASK_ROUTE_HOME(R.string.ask_route_home, "voice_ask_route_home"),
    ROUTING_HOME(R.string.routing_home, "voice_routing_home"),
    NO_HOME(R.string.no_home, "voice_no_home"),
    DECLINED(R.string.declined, "voice_declined"),
    DEPART_DARK(R.string.depart_dark, "voice_depart_dark"),
    DEPART_RAIN(R.string.depart_rain, "voice_depart_rain"),
    DEPART_DARK_RAIN(R.string.depart_dark_rain, "voice_depart_dark_rain"),
}
