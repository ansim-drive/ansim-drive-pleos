package com.ansimdrive.pleos.detection

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 데모 경로가 실제로 헤맴 감지를 일으키는지 (시연 영상 길이 확인용). */
class DemoRouteTest {
    @Test
    fun demoRouteTriggersWithin25Seconds() {
        val text = File("src/main/assets/demo_route.json").readText()
        val points = Regex("""\[(-?[\d.]+),(-?[\d.]+)]""").findAll(text)
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            .toList()
        val d = WanderingDetector()
        // 데모 재생과 같은 1초 간격
        var reason: WanderingDetector.Reason? = null
        val firstTrigger = points.withIndex().firstOrNull { (i, p) ->
            reason = d.onLocation(p.first, p.second, i * 1_000L)
            reason != null
        }?.index
        println("demo route: ${points.size} points, first trigger at ${firstTrigger}s ($reason)")
        assertTrue(firstTrigger != null && firstTrigger <= 25)
    }
}
