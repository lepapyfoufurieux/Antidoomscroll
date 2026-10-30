package com.antiscrolling.data

import com.antiscrolling.data.db.AppDao
import com.antiscrolling.data.db.FocusSessionEntity
import com.antiscrolling.data.db.ProfileEntity
import com.antiscrolling.data.db.ProfileScheduleEntity
import com.antiscrolling.data.db.RuleEntity
import com.antiscrolling.data.db.ScheduleEntity
import com.antiscrolling.domain.DEFAULT_PROFILE_ID
import com.antiscrolling.domain.FocusSession
import com.antiscrolling.domain.Profile
import com.antiscrolling.domain.Rule
import com.antiscrolling.domain.RuleSet
import com.antiscrolling.domain.Schedule
import com.antiscrolling.domain.TaskKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

const val DEFAULT_DELAY_SEC = 15

class RuleRepository(private val dao: AppDao, scope: CoroutineScope) {
    /** Règles de chaque profil, avec leurs plages, et les profils. `null` tant que la base n'a pas été lue (démarrage à froid). */
    val rules: StateFlow<RuleSet?> = combine(
        dao.observeRules(), dao.observeSchedules(), dao.observeProfiles(), dao.observeProfileSchedules(),
    ) { rules, schedules, profiles, profileSchedules ->
        val byRule = schedules.groupBy({ it.profileId to it.packageName }) { Schedule(it.id, it.daysMask, it.startMin, it.endMin) }
        val byProfile = profileSchedules.groupBy({ it.profileId }) { Schedule(it.id, it.daysMask, it.startMin, it.endMin) }
        RuleSet(
            profiles.map { Profile(it.id, it.name, byProfile[it.id].orEmpty(), it.enabled) },
            rules.groupBy { it.profileId }.mapValues { (_, list) ->
                list.associate {
                    it.packageName to Rule(
                        it.packageName, it.enabled, it.delaySec, it.dailyQuotaMin, byRule[it.profileId to it.packageName].orEmpty(),
                        it.delayIncrementSec, it.askIntent, TaskKind.valueOf(it.task), it.askDuration, it.hideNotifications,
                    )
                }
            },
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Dernière session de focus, active ou non : liste vide si jamais lancé, `null` tant que la base n'a pas été lue. */
    val latestFocus: StateFlow<List<FocusSession>?> = dao.observeLatestFocus()
        .map { list -> list.map { FocusSession(it.id, Instant.ofEpochMilli(it.startAt), Instant.ofEpochMilli(it.endAt)) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setEnabled(profileId: Long, packageName: String, enabled: Boolean) {
        val rule = rules.value?.of(profileId)?.get(packageName) ?: Rule(packageName, enabled, DEFAULT_DELAY_SEC)
        dao.upsertRule(entity(profileId, rule.copy(enabled = enabled)))
    }

    /** Modifie les réglages d'une appli suivie (limite, frictions…). Sans effet sur une appli non suivie. */
    suspend fun update(profileId: Long, packageName: String, change: (Rule) -> Rule) {
        val rule = rules.value?.of(profileId)?.get(packageName) ?: return
        dao.upsertRule(entity(profileId, change(rule)))
    }

    suspend fun addSchedule(profileId: Long, packageName: String, schedule: Schedule) =
        dao.insertSchedule(ScheduleEntity(0, profileId, packageName, schedule.daysMask, schedule.startMin, schedule.endMin))

    suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    /** Nouveau profil, désactivé, avec une copie des réglages du profil par défaut : il ne s'applique pas pendant la copie. */
    suspend fun addProfile(name: String): Long {
        val id = dao.insertProfile(ProfileEntity(0, name, enabled = false))
        val defaults = rules.value?.of(DEFAULT_PROFILE_ID).orEmpty().values
        dao.upsertRules(defaults.map { entity(id, it) })
        dao.insertSchedules(
            defaults.flatMap { rule -> rule.schedules.map { ScheduleEntity(0, id, rule.packageName, it.daysMask, it.startMin, it.endMin) } },
        )
        return id
    }

    /** Horaires d'abord : le profil cesse d'être actif avant que ses règles disparaissent. */
    suspend fun deleteProfile(id: Long) {
        dao.deleteProfileSchedulesOf(id)
        dao.deleteProfile(id)
        dao.deleteRulesOf(id)
        dao.deleteSchedulesOf(id)
    }

    suspend fun setProfileEnabled(id: Long, enabled: Boolean) = dao.setProfileEnabled(id, enabled)

    suspend fun addProfileSchedule(profileId: Long, schedule: Schedule) =
        dao.insertProfileSchedule(ProfileScheduleEntity(0, profileId, schedule.daysMask, schedule.startMin, schedule.endMin))

    suspend fun deleteProfileSchedule(id: Long) = dao.deleteProfileSchedule(id)

    suspend fun startFocus(from: Instant, until: Instant) =
        dao.insertFocus(FocusSessionEntity(0, from.toEpochMilli(), until.toEpochMilli()))

    suspend fun stopFocus(session: FocusSession, at: Instant) = dao.endFocus(session.id, at.toEpochMilli())

    private fun entity(profileId: Long, rule: Rule) = RuleEntity(
        profileId, rule.packageName, rule.enabled, rule.delaySec, rule.dailyQuotaMin, rule.delayIncrementSec,
        rule.askIntent, rule.task.name, rule.askDuration, rule.hideNotifications,
    )
}
