package com.ansimdrive.pleos.data

import com.ansimdrive.pleos.detection.DetectionConfig

/** 한 주 동안의 운전 습관 지표 합계. */
data class WeekTotals(val hardBrakes: Int, val wanderings: Int, val nightMinutes: Int)

/** 이번 주 상태. 진단이 아니라 운전 습관 변화를 날씨처럼 보여 준다. */
enum class WeeklyStatus(val icon: String, val label: String) {
    CLEAR("☀", "맑음"),
    CLOUDY("☁", "흐림"),
    RAIN("☂", "비");

    companion object {
        /** 급제동·헤맴·야간 중 전주보다 늘어난 지표 수로 정한다. 전주 기록이 없으면 맑음. */
        fun of(thisWeek: WeekTotals, lastWeek: WeekTotals?): WeeklyStatus {
            if (lastWeek == null) return CLEAR
            val worse = listOf(
                thisWeek.hardBrakes > lastWeek.hardBrakes,
                thisWeek.wanderings > lastWeek.wanderings,
                thisWeek.nightMinutes > lastWeek.nightMinutes,
            ).count { it }
            return when {
                worse >= DetectionConfig.WEEKLY_RAIN_AT -> RAIN
                worse >= DetectionConfig.WEEKLY_CLOUDY_AT -> CLOUDY
                else -> CLEAR
            }
        }
    }
}
