package com.ansimdrive.pleos.detection

import com.ansimdrive.pleos.detection.DetectionConfig as C

/**
 * 헤맴 감지. 위치를 넣으면 트리거될 때 이유를 돌려준다.
 * - 같은 격자에 10분 안에 3번 새로 진입 (머무르는 건 1번)
 * - 1km 이상 달린 뒤 출발점 200m 이내 복귀
 * 속도는 위치 변화로 계산한다 (위치 소스와 무관하게 동작).
 */
class WanderingDetector {

    enum class Reason { REENTRY, LOOP_BACK }

    /** 집 좌표. 반경 안에서는 감지하지 않는다. */
    var home: Pair<Double, Double>? = null

    /** 내비 경로 안내 중이면 감지하지 않는다. */
    var routeActive = false

    private val entries = ArrayDeque<Pair<String, Long>>()   // (격자, 진입 시각)
    private var currentCell: String? = null
    private var prev: Triple<Double, Double, Long>? = null
    private var start: Pair<Double, Double>? = null
    private var travelM = 0.0
    private var lastTriggerMs: Long? = null

    fun reset() {
        entries.clear()
        currentCell = null
        prev = null
        start = null
        travelM = 0.0
        lastTriggerMs = null
    }

    fun onLocation(lat: Double, lon: Double, timeMs: Long): Reason? {
        val p = prev
        prev = Triple(lat, lon, timeMs)
        if (start == null) start = lat to lon
        if (p == null || timeMs <= p.third) return null

        val stepM = distanceM(p.first, p.second, lat, lon)
        val kmh = stepM / ((timeMs - p.third) / 1000.0) * 3.6
        travelM += stepM

        if (routeActive || kmh < C.MIN_SPEED_KMH || nearHome(lat, lon)) return null

        val cell = Geohash.encode(lat, lon)
        if (cell != currentCell) {
            currentCell = cell
            entries.addLast(cell to timeMs)
        }
        while (entries.isNotEmpty() && timeMs - entries.first().second > C.WINDOW_MS) entries.removeFirst()

        val reason = when {
            entries.count { it.first == cell } >= C.REENTRY_THRESHOLD -> Reason.REENTRY
            travelM >= C.LOOP_MIN_TRAVEL_M &&
                start!!.let { distanceM(it.first, it.second, lat, lon) } <= C.LOOP_RETURN_RADIUS_M -> Reason.LOOP_BACK
            else -> null
        } ?: return null

        if (lastTriggerMs?.let { timeMs - it < C.COOLDOWN_MS } == true) return null
        lastTriggerMs = timeMs
        // 같은 상황으로 쿨다운 직후 다시 울리지 않도록 기준을 새로 잡는다.
        entries.clear()
        start = lat to lon
        travelM = 0.0
        return reason
    }

    private fun nearHome(lat: Double, lon: Double) =
        home?.let { distanceM(it.first, it.second, lat, lon) <= C.HOME_RADIUS_M } == true
}
