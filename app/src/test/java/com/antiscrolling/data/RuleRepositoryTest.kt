package com.antiscrolling.data

import com.antiscrolling.data.db.FocusSessionEntity
import com.antiscrolling.data.db.ProfileEntity
import com.antiscrolling.data.db.ProfileScheduleEntity
import com.antiscrolling.data.db.RuleEntity
import com.antiscrolling.data.db.ScheduleEntity
import com.antiscrolling.domain.DEFAULT_PROFILE_ID
import com.antiscrolling.domain.FocusSession
import com.antiscrolling.domain.Profile
import com.antiscrolling.domain.Schedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RuleRepositoryTest {
    private val pkg = "com.instagram.android"
    private val dao = FakeAppDao()

    @Test
    fun `regles null tant que les regles, les plages ou les profils ne sont pas lus`() = runTest {
        val repo = RuleRepository(dao, backgroundScope)
        dao.rules.emit(listOf(RuleEntity(DEFAULT_PROFILE_ID, pkg, true, 15, null, 0, false, "NONE", false, false)))
        dao.schedules.emit(listOf(ScheduleEntity(7, DEFAULT_PROFILE_ID, pkg, Schedule.EVERY_DAY, 22 * 60, 7 * 60)))
        dao.profiles.emit(emptyList())
        runCurrent()
        assertNull(repo.rules.value)
        dao.profileSchedules.emit(emptyList())
        runCurrent()
        assertEquals(listOf(Schedule(7, Schedule.EVERY_DAY, 22 * 60, 7 * 60)), repo.rules.value?.of(DEFAULT_PROFILE_ID)?.get(pkg)?.schedules)
    }

    @Test
    fun `chaque profil a ses regles, ses plages et ses horaires`() = runTest {
        val repo = RuleRepository(dao, backgroundScope)
        dao.rules.emit(
            listOf(
                RuleEntity(DEFAULT_PROFILE_ID, pkg, true, 15, null, 0, false, "NONE", false, false),
                RuleEntity(2, pkg, true, 60, 30, 0, true, "MATH", false, false),
            ),
        )
        dao.schedules.emit(listOf(ScheduleEntity(7, 2, pkg, Schedule.EVERY_DAY, 22 * 60, 7 * 60)))
        dao.profiles.emit(listOf(ProfileEntity(2, "Travail", enabled = true)))
        dao.profileSchedules.emit(listOf(ProfileScheduleEntity(4, 2, Schedule.EVERY_DAY, 9 * 60, 18 * 60)))
        runCurrent()
        val set = repo.rules.value!!
        assertEquals(listOf(Profile(2, "Travail", listOf(Schedule(4, Schedule.EVERY_DAY, 9 * 60, 18 * 60)))), set.profiles)
        assertEquals(emptyList<Schedule>(), set.of(DEFAULT_PROFILE_ID)[pkg]?.schedules)
        assertEquals(60, set.of(2)[pkg]?.delaySec)
        assertEquals(1, set.of(2)[pkg]?.schedules?.size)
    }

    @Test
    fun `focus null tant que la base n'est pas lue, puis la derniere session`() = runTest {
        val repo = RuleRepository(dao, backgroundScope)
        runCurrent()
        assertNull(repo.latestFocus.value)
        dao.focus.emit(emptyList())
        runCurrent()
        assertEquals(emptyList<FocusSession>(), repo.latestFocus.value)
        dao.focus.emit(listOf(FocusSessionEntity(3, 1_000, 2_000)))
        runCurrent()
        assertEquals(listOf(FocusSession(3, Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000))), repo.latestFocus.value)
    }
}
