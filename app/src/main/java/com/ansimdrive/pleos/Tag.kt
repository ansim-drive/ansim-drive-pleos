package com.ansimdrive.pleos

/** 모든 로그 태그. `adb logcat -s AnsimDrive` */
const val TAG = "AnsimDrive"

/** 로그에 쓰는 좌표. release 빌드는 위치를 시스템 로그에 남기지 않는다. */
fun logCoords(lat: Double, lon: Double): String = if (BuildConfig.DEBUG) "$lat, $lon" else "<hidden>"
