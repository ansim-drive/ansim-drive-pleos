package com.ansimdrive.pleos.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyStatusTest {
    private val base = WeekTotals(hardBrakes = 2, wanderings = 1, nightMinutes = 30)

    @Test
    fun noLastWeekIsClear() {
        assertEquals(WeeklyStatus.CLEAR, WeeklyStatus.of(WeekTotals(9, 9, 999), null))
    }

    @Test
    fun countsIndicatorsThatGotWorse() {
        assertEquals(WeeklyStatus.CLEAR, WeeklyStatus.of(base, base))                       // 같음
        assertEquals(WeeklyStatus.CLEAR, WeeklyStatus.of(WeekTotals(0, 0, 0), base))        // 좋아짐
        assertEquals(WeeklyStatus.CLOUDY, WeeklyStatus.of(base.copy(hardBrakes = 3), base)) // 1개 늘어남
        assertEquals(WeeklyStatus.RAIN, WeeklyStatus.of(base.copy(hardBrakes = 3, nightMinutes = 31), base))
        assertEquals(WeeklyStatus.RAIN, WeeklyStatus.of(WeekTotals(3, 2, 31), base))        // 3개 모두
    }
}
