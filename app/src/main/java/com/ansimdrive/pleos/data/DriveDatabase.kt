package com.ansimdrive.pleos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** 주행 중 생긴 일 하나. */
@Entity(tableName = "trip_event")
data class TripEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val date: String,     // yyyy-MM-dd (기기 시간대)
    val type: String,     // HARD_BRAKE | WANDERING | NIGHT
    val value: Double,    // 급제동: 제동 직후 속도(km/h), 헤맴: 0, 야간: 분
) {
    companion object {
        const val HARD_BRAKE = "HARD_BRAKE"
        const val WANDERING = "WANDERING"
        const val NIGHT = "NIGHT"
    }
}

/** 하루 합계. 요약 화면과 이번 주 상태 계산에 쓴다. */
@Entity(tableName = "daily_summary")
data class DailySummary(
    @PrimaryKey val date: String,
    val distanceKm: Double = 0.0,
    val hardBrakes: Int = 0,
    val wanderings: Int = 0,
    val nightMinutes: Int = 0,
)

/** 기간 합계 (days = 기록이 있는 날 수). */
data class PeriodTotals(val hardBrakes: Int, val wanderings: Int, val nightMinutes: Int, val days: Int)

@Dao
abstract class DriveDao {
    @Insert
    protected abstract suspend fun insertEvent(event: TripEvent)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertDay(day: DailySummary)

    @Query("UPDATE daily_summary SET hardBrakes = hardBrakes + 1 WHERE date = :date")
    protected abstract suspend fun addHardBrake(date: String)

    @Query("UPDATE daily_summary SET wanderings = wanderings + 1 WHERE date = :date")
    protected abstract suspend fun addWandering(date: String)

    @Query("UPDATE daily_summary SET nightMinutes = nightMinutes + :minutes WHERE date = :date")
    protected abstract suspend fun addNightMinutes(date: String, minutes: Int)

    @Query("UPDATE daily_summary SET distanceKm = distanceKm + :km WHERE date = :date")
    protected abstract suspend fun addDistance(date: String, km: Double)

    /** 사건을 남기고 그날 합계에 더한다. */
    @Transaction
    open suspend fun record(event: TripEvent) {
        insertEvent(event)
        insertDay(DailySummary(event.date))
        when (event.type) {
            TripEvent.HARD_BRAKE -> addHardBrake(event.date)
            TripEvent.WANDERING -> addWandering(event.date)
            TripEvent.NIGHT -> addNightMinutes(event.date, event.value.toInt())
        }
    }

    @Transaction
    open suspend fun recordDistance(date: String, km: Double) {
        insertDay(DailySummary(date))
        addDistance(date, km)
    }

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    abstract suspend fun day(date: String): DailySummary?

    @Query(
        "SELECT COALESCE(SUM(hardBrakes), 0) AS hardBrakes, COALESCE(SUM(wanderings), 0) AS wanderings, " +
            "COALESCE(SUM(nightMinutes), 0) AS nightMinutes, COUNT(*) AS days " +
            "FROM daily_summary WHERE date BETWEEN :from AND :to"
    )
    abstract suspend fun totals(from: String, to: String): PeriodTotals

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putDay(day: DailySummary)
}

@Database(entities = [TripEvent::class, DailySummary::class], version = 1, exportSchema = false)
abstract class DriveDatabase : RoomDatabase() {
    abstract fun driveDao(): DriveDao

    companion object {
        @Volatile private var instance: DriveDatabase? = null

        fun get(context: Context): DriveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DriveDatabase::class.java, "drive.db")
                .build().also { instance = it }
        }
    }
}
