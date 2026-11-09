package com.ansimdrive.pleos.detection

/** 감지 임계값 모음. 2차에서 튜닝한다. */
object DetectionConfig {
    // 헤맴: 같은 격자 재진입
    const val GEOHASH_PRECISION = 7               // 약 150m 격자
    const val WINDOW_MS = 10 * 60 * 1000L         // 슬라이딩 윈도우 10분
    const val REENTRY_THRESHOLD = 3               // 같은 격자에 새로 진입한 횟수

    // 헤맴 보조 조건: 멀리 갔다가 출발점으로 복귀
    const val LOOP_MIN_TRAVEL_M = 1_000.0
    const val LOOP_RETURN_RADIUS_M = 200.0

    // 억제 조건
    const val MIN_SPEED_KMH = 10f                 // 미만이면 윈도우에 넣지 않음 (신호 대기)
    const val HOME_RADIUS_M = 500.0
    const val COOLDOWN_MS = 15 * 60 * 1000L       // 트리거 후 재트리거 금지

    // 급제동 (eTAS 기준 준용)
    const val HARD_BRAKE_KMH_PER_SEC = 11f
    const val HARD_BRAKE_COOLDOWN_MS = 3_000L

    // 헤드라이트 정보가 없을 때 야간 근사 (일몰~일출)
    const val NIGHT_START_HOUR = 19
    const val NIGHT_END_HOUR = 6
}
