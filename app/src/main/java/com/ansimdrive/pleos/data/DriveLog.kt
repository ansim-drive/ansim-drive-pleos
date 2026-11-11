package com.ansimdrive.pleos.data

import android.content.Context
import android.util.Log
import com.ansimdrive.pleos.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.Executors

/** 정차 요약 화면에 보여 줄 값. */
data class Summary(
    val distanceKm: Double,
    val hardBrakes: Int,
    val nightDriving: Boolean,
    val week: WeeklyStatus,
)

/**
 * 주행 기록 저장소 (Room). 쓰기·읽기를 요청한 순서대로 하나씩 처리해,
 * 정차 직후 요약을 읽을 때 앞서 남긴 기록이 빠지지 않게 한다.
 * (Room suspend 함수는 자체 스레드에서 돌아 스레드 하나로는 순서가 보장되지 않아 Mutex를 쓴다.)
 */
class DriveLog(context: Context) {
    private val dao = DriveDatabase.get(context).driveDao()
    private val db = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + db)
    private val lock = Mutex()

    fun hardBrake(kmh: Float) = record(TripEvent.HARD_BRAKE, kmh.toDouble())

    fun wandering() = record(TripEvent.WANDERING, 0.0)

    /** 주행이 끝날 때: 거리와 야간 주행 시간(분)을 더한다. */
    fun endTrip(distanceKm: Double, nightMinutes: Int) {
        scope.launch {
            lock.withLock {
                dao.recordDistance(today(), distanceKm)
                if (nightMinutes > 0) dao.record(event(TripEvent.NIGHT, nightMinutes.toDouble()))
            }
        }
    }

    /** 오늘 합계 + 이번 주 상태(이번 주 월요일~오늘 vs 지난주 월~일). */
    suspend fun summary(): Summary = withContext(db) { lock.withLock { readSummary() } }

    private suspend fun readSummary(): Summary {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val day = dao.day(today.toString())
        val thisWeek = dao.totals(monday.toString(), today.toString())
        val lastWeek = dao.totals(monday.minusDays(7).toString(), monday.minusDays(1).toString())
        return Summary(
            distanceKm = day?.distanceKm ?: 0.0,
            hardBrakes = day?.hardBrakes ?: 0,
            nightDriving = (day?.nightMinutes ?: 0) > 0,
            week = WeeklyStatus.of(thisWeek.toWeek(), lastWeek.takeIf { it.days > 0 }?.toWeek()),
        ).also { Log.i(TAG, "Summary: $it (this week=$thisWeek, last week=$lastWeek)") }
    }

    /** 디버그 전용: 지난주 월요일에 빈 기록을 넣어 전주 비교가 동작하는지 본다. */
    fun seedEmptyLastWeek() {
        scope.launch {
            lock.withLock {
                val lastMonday = LocalDate.now().with(DayOfWeek.MONDAY).minusDays(7)
                dao.putDay(DailySummary(lastMonday.toString()))
                Log.i(TAG, "Seeded empty last-week record: $lastMonday")
            }
        }
    }

    private fun record(type: String, value: Double) {
        scope.launch { lock.withLock { dao.record(event(type, value)) } }
    }

    private fun event(type: String, value: Double) =
        TripEvent(timeMs = System.currentTimeMillis(), date = today(), type = type, value = value)

    private fun today() = LocalDate.now().toString()

    private fun PeriodTotals.toWeek() = WeekTotals(hardBrakes, wanderings, nightMinutes)
}
