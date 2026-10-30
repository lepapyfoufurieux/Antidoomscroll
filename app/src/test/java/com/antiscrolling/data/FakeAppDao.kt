package com.antiscrolling.data

import com.antiscrolling.data.db.AppDao
import com.antiscrolling.data.db.FocusSessionEntity
import com.antiscrolling.data.db.GrantEntity
import com.antiscrolling.data.db.OpenEventEntity
import com.antiscrolling.data.db.ProfileEntity
import com.antiscrolling.data.db.ProfileScheduleEntity
import com.antiscrolling.data.db.RuleEntity
import com.antiscrolling.data.db.ScheduleEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

/** Les flows n'émettent que quand le test le décide ; la lecture des accès attend que [saved] soit complété. */
class FakeAppDao : AppDao {
    val rules = MutableSharedFlow<List<RuleEntity>>(replay = 1)
    val schedules = MutableSharedFlow<List<ScheduleEntity>>(replay = 1)
    val focus = MutableSharedFlow<List<FocusSessionEntity>>(replay = 1)
    val profiles = MutableSharedFlow<List<ProfileEntity>>(replay = 1)
    val profileSchedules = MutableSharedFlow<List<ProfileScheduleEntity>>(replay = 1)
    val saved = CompletableDeferred<List<GrantEntity>>()
    val written = mutableListOf<GrantEntity>()
    val savedOpens = CompletableDeferred<List<OpenEventEntity>>()
    val writtenOpens = mutableListOf<OpenEventEntity>()

    override fun observeRules() = rules
    override suspend fun upsertRule(rule: RuleEntity) = Unit
    override suspend fun upsertRules(rules: List<RuleEntity>) = Unit
    override suspend fun deleteRulesOf(profileId: Long) = Unit
    override suspend fun grants(): List<GrantEntity> = saved.await()
    override suspend fun upsertGrant(grant: GrantEntity) {
        written += grant
    }
    override fun observeSchedules() = schedules
    override suspend fun insertSchedule(schedule: ScheduleEntity) = Unit
    override suspend fun deleteSchedule(id: Long) = Unit
    override suspend fun insertSchedules(schedules: List<ScheduleEntity>) = Unit
    override suspend fun deleteSchedulesOf(profileId: Long) = Unit
    override fun observeProfiles() = profiles
    override suspend fun insertProfile(profile: ProfileEntity): Long = 1
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun setProfileEnabled(id: Long, enabled: Boolean) = Unit
    override fun observeProfileSchedules() = profileSchedules
    override suspend fun insertProfileSchedule(schedule: ProfileScheduleEntity) = Unit
    override suspend fun deleteProfileSchedule(id: Long) = Unit
    override suspend fun deleteProfileSchedulesOf(profileId: Long) = Unit
    override fun observeLatestFocus() = focus
    override suspend fun insertFocus(focus: FocusSessionEntity) = Unit
    override suspend fun endFocus(id: Long, endAt: Long) = Unit
    override suspend fun opensSince(since: Long): List<OpenEventEntity> = savedOpens.await().filter { it.at >= since }
    override suspend fun insertOpen(open: OpenEventEntity) {
        writtenOpens += open
    }
}
