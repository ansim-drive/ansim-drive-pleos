package com.ansimdrive.pleos.data

import android.content.Context

/** 앱 설정. 설정 화면 UI는 2차. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 가족 공유. 1차는 상태 표시만 하고 켜고 끄는 동작은 없다. */
    val familySharing: Boolean
        get() = prefs.getBoolean("family_sharing", false)
}
