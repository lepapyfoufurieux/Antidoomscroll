package com.antiscrolling.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rule", primaryKeys = ["profileId", "packageName"])
data class RuleEntity(
    val profileId: Long,
    val packageName: String,
    val enabled: Boolean,
    val delaySec: Int,
    val dailyQuotaMin: Int?,
    val delayIncrementSec: Int,
    val askIntent: Boolean,
    val task: String, // TaskKind
    val askDuration: Boolean,
    val hideNotifications: Boolean,
)

@Entity(tableName = "grant")
data class GrantEntity(
    @PrimaryKey val packageName: String,
    val until: Long, // epoch ms
)

@Dao
interface AppDao {
    @Query("SELECT * FROM rule")
    fun observeRules(): Flow<List<RuleEntity>>

    @Upsert
    suspend fun upsertRule(rule: RuleEntity)

    @Upsert
    suspend fun upsertRules(rules: List<RuleEntity>)

    @Query("DELETE FROM rule WHERE profileId = :profileId")
    suspend fun deleteRulesOf(profileId: Long)

    @Query("SELECT * FROM `grant`")
    suspend fun grants(): List<GrantEntity>

    @Upsert
    suspend fun upsertGrant(grant: GrantEntity)

    @Query("SELECT * FROM schedule")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Insert
    suspend fun insertSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Insert
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedule WHERE profileId = :profileId")
    suspend fun deleteSchedulesOf(profileId: Long)

    @Query("SELECT * FROM profile ORDER BY id")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Insert
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("UPDATE profile SET enabled = :enabled WHERE id = :id")
    suspend fun setProfileEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM profile_schedule")
    fun observeProfileSchedules(): Flow<List<ProfileScheduleEntity>>

    @Insert
    suspend fun insertProfileSchedule(schedule: ProfileScheduleEntity)

    @Query("DELETE FROM profile_schedule WHERE id = :id")
    suspend fun deleteProfileSchedule(id: Long)

    @Query("DELETE FROM profile_schedule WHERE profileId = :profileId")
    suspend fun deleteProfileSchedulesOf(profileId: Long)

    /** Tout ou rien : un processus tué en route ne laisse pas un profil sans ses règles. [rules] et [schedules] reçoivent l'id du profil. */
    @Transaction
    suspend fun insertProfileWithRules(profile: ProfileEntity, rules: List<RuleEntity>, schedules: List<ScheduleEntity>): Long {
        val id = insertProfile(profile)
        upsertRules(rules.map { it.copy(profileId = id) })
        insertSchedules(schedules.map { it.copy(profileId = id) })
        return id
    }

    /** Tout ou rien, comme la création : pas de règles orphelines. */
    @Transaction
    suspend fun deleteProfileWithRules(id: Long) {
        deleteProfileSchedulesOf(id)
        deleteProfile(id)
        deleteRulesOf(id)
        deleteSchedulesOf(id)
    }

    /** La dernière session lancée seulement (liste vide : jamais lancé). */
    @Query("SELECT * FROM focus_session ORDER BY startAt DESC LIMIT 1")
    fun observeLatestFocus(): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insertFocus(focus: FocusSessionEntity)

    @Query("UPDATE focus_session SET endAt = :endAt WHERE id = :id")
    suspend fun endFocus(id: Long, endAt: Long)

    @Query("SELECT * FROM open_event WHERE at >= :since ORDER BY at")
    suspend fun opensSince(since: Long): List<OpenEventEntity>

    @Insert
    suspend fun insertOpen(open: OpenEventEntity)
}

@Entity(tableName = "open_event", indices = [Index("at")])
data class OpenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val at: Long, // epoch ms
    val outcome: String, // Outcome
    val intent: String?,
    val grantedMin: Int?,
)

@Entity(tableName = "schedule", indices = [Index("profileId", "packageName")])
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val packageName: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
)

@Entity(tableName = "profile_schedule")
data class ProfileScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
)

@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long, // epoch ms
    val endAt: Long,
)

@Entity(tableName = "daily_usage", primaryKeys = ["date", "packageName"])
data class DailyUsageEntity(
    val date: String, // yyyy-MM-dd
    val packageName: String,
    val foregroundMs: Long,
)

@Entity(tableName = "daily_unlocks")
data class DailyUnlocksEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd ; sa présence marque le jour comme recopié
    val count: Int,
)

@Dao
interface HistoryDao {
    @Query("SELECT date FROM daily_unlocks")
    suspend fun savedDates(): List<String>

    @Query("SELECT * FROM daily_usage WHERE date >= :from")
    suspend fun usageSince(from: String): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_unlocks WHERE date >= :from")
    suspend fun unlocksSince(from: String): List<DailyUnlocksEntity>

    /** Un jour entier d'un coup : le temps par appli et la ligne qui le marque comme recopié. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(usage: List<DailyUsageEntity>, unlocks: DailyUnlocksEntity)
}

// Schéma exporté dans app/schemas : chaque changement de version a sa migration, testée (tests.md, niveau 3).
@Database(
    entities = [
        RuleEntity::class, GrantEntity::class, DailyUsageEntity::class, DailyUnlocksEntity::class,
        ScheduleEntity::class, FocusSessionEntity::class, OpenEventEntity::class,
        ProfileEntity::class, ProfileScheduleEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    abstract fun historyDao(): HistoryDao
}
