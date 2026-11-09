package com.ansimdrive.pleos.vehicle

import com.ansimdrive.pleos.detection.DetectionConfig

/**
 * 급제동: 브레이크 ON 상태에서 초당 11km/h 이상 감속 (eTAS 기준 준용).
 * 약 1초 간격으로 속도 차이를 본다. 같은 제동을 여러 번 세지 않도록 쿨다운을 둔다.
 */
class HardBrakeDetector {
    private var refKmh = 0f
    private var refMs = -1L
    private var lastEventMs = Long.MIN_VALUE / 2

    /** 급제동이면 true. */
    fun onSpeed(kmh: Float, timeMs: Long, brakeOn: Boolean): Boolean {
        if (refMs < 0 || timeMs < refMs) {
            refKmh = kmh; refMs = timeMs
            return false
        }
        val dtMs = timeMs - refMs
        if (dtMs < 1_000) return false

        val decelPerSec = (refKmh - kmh) * 1_000f / dtMs
        refKmh = kmh; refMs = timeMs
        if (!brakeOn || decelPerSec < DetectionConfig.HARD_BRAKE_KMH_PER_SEC) return false
        if (timeMs - lastEventMs < DetectionConfig.HARD_BRAKE_COOLDOWN_MS) return false
        lastEventMs = timeMs
        return true
    }
}
