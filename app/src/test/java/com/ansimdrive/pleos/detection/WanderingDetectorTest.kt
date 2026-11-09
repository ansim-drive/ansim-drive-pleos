package com.ansimdrive.pleos.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WanderingDetectorTest {
    // 서로 다른 격자인 두 지점 (위도 0.0018° ≈ 200m)
    private val a = 37.5665 to 126.9780
    private val b = 37.5683 to 126.9780

    /** a, b를 번갈아 [stepSec] 간격으로 [n]번 지나가며 결과를 모은다. */
    private fun WanderingDetector.pingPong(n: Int, stepSec: Long, startMs: Long = 0): List<WanderingDetector.Reason?> =
        (0 until n).map { i ->
            val p = if (i % 2 == 0) a else b
            onLocation(p.first, p.second, startMs + i * stepSec * 1000)
        }

    @Test
    fun thirdReentryTriggers() {
        val results = WanderingDetector().pingPong(n = 6, stepSec = 20)   // 36 km/h
        // 첫 샘플(a)은 속도를 몰라 제외 → b 진입이 i=1,3,5에서 세 번째가 된다.
        assertEquals(listOf(null, null, null, null, null, WanderingDetector.Reason.REENTRY), results)
    }

    @Test
    fun stayingInSameCellCountsOnce() {
        val d = WanderingDetector()
        // 같은 격자 안에서 조금씩 움직임 (≈ 11m / 2s = 20 km/h)
        val results = (0 until 30).map { i -> d.onLocation(37.5665 + (i % 3) * 0.0001, 126.9780, i * 2000L) }
        assertEquals(List(30) { null }, results)
    }

    @Test
    fun slowSamplesAreIgnored() {
        val results = WanderingDetector().pingPong(n = 9, stepSec = 100)  // 7.2 km/h
        assertEquals(List(9) { null }, results)
    }

    @Test
    fun suppressedNearHomeAndDuringRoute() {
        assertEquals(List(9) { null }, WanderingDetector().apply { home = a }.pingPong(n = 9, stepSec = 20))
        assertEquals(List(9) { null }, WanderingDetector().apply { routeActive = true }.pingPong(n = 9, stepSec = 20))
    }

    @Test
    fun cooldownBlocksRetriggerFor15Minutes() {
        val d = WanderingDetector()
        assertEquals(WanderingDetector.Reason.REENTRY, d.pingPong(n = 6, stepSec = 20).last())
        // 쿨다운 안(약 4분 동안 계속 왕복) → 조용
        assertEquals(List(12) { null }, d.pingPong(n = 12, stepSec = 20, startMs = 140_000))
        // 15분이 지난 뒤 다시 왕복 → 다시 트리거 (이유는 무엇이든)
        val later = d.pingPong(n = 7, stepSec = 20, startMs = 16 * 60_000L)
        assertNotNull(later.filterNotNull().firstOrNull())
    }

    @Test
    fun returningToStartAfter1kmTriggersLoopBack() {
        val d = WanderingDetector()
        // 약 300m 변의 사각형을 100m 단위로 돌아 출발점으로 복귀 (36 km/h)
        val dLat = 0.0009                     // ≈ 100m
        val dLon = 0.00113                    // ≈ 100m at 37.5°
        val path = buildList {
            for (i in 0..3) add(37.5665 + i * dLat to 126.9780)
            for (i in 1..3) add(37.5665 + 3 * dLat to 126.9780 + i * dLon)
            for (i in 1..3) add(37.5665 + (3 - i) * dLat to 126.9780 + 3 * dLon)
            for (i in 1..3) add(37.5665 to 126.9780 + (3 - i) * dLon)
        }
        val results = path.mapIndexed { i, p -> d.onLocation(p.first, p.second, i * 10_000L) }
        // 1.1km 달린 뒤 출발점 100m 지점(끝에서 두 번째)에서 반경 200m 안에 들어와 트리거
        assertEquals(path.size - 2, results.indexOfFirst { it != null })
        assertEquals(WanderingDetector.Reason.LOOP_BACK, results[path.size - 2])
    }
}
