package com.antiscrolling.data

import com.antiscrolling.data.db.GrantEntity
import com.antiscrolling.data.db.OpenEventEntity
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryTest {
    private val pkg = "com.instagram.android"
    private val until = Instant.parse("2026-10-29T20:05:00Z")
    private val dao = FakeAppDao()

    @Test
    fun `acces null tant que la base n'est pas lue, puis relus`() = runTest {
        val repo = EventRepository(dao, backgroundScope)
        runCurrent()
        assertNull(repo.grants.value)
        dao.saved.complete(listOf(GrantEntity(pkg, until.toEpochMilli())))
        runCurrent()
        assertEquals(mapOf(pkg to until), repo.grants.value)
    }

    @Test
    fun `acces visible tout de suite, avant l'ecriture en base`() = runTest {
        dao.saved.complete(emptyList())
        dao.savedOpens.complete(emptyList()) // sinon runTest attendrait la lecture des ouvertures
        val repo = EventRepository(dao, this)
        runCurrent()
        repo.grant(pkg, until)
        assertEquals(mapOf(pkg to until), repo.grants.value)
        assertEquals(emptyList<GrantEntity>(), dao.written)
        runCurrent()
        assertEquals(listOf(GrantEntity(pkg, until.toEpochMilli())), dao.written)
    }

    @Test
    fun `acces accorde pendant la lecture de la base n'est pas ecrase`() = runTest {
        val repo = EventRepository(dao, backgroundScope)
        runCurrent()
        repo.grant(pkg, until)
        dao.saved.complete(listOf(GrantEntity(pkg, until.minusSeconds(600).toEpochMilli())))
        runCurrent()
        assertEquals(mapOf(pkg to until), repo.grants.value)
    }

    @Test
    fun `ouvertures null tant que la base n'est pas lue, recentes seulement`() = runTest {
        val since = Instant.parse("2026-10-28T00:00:00Z")
        val repo = EventRepository(dao, backgroundScope) { since }
        runCurrent()
        assertNull(repo.recentOpens.value)
        dao.savedOpens.complete(
            listOf(
                OpenEventEntity(1, pkg, since.minusSeconds(1).toEpochMilli(), "GRANTED", null, 5),
                OpenEventEntity(2, pkg, since.toEpochMilli(), "GAVE_UP", "Par habitude", null),
            ),
        )
        runCurrent()
        assertEquals(listOf(OpenEvent(pkg, since, Outcome.GAVE_UP, "Par habitude", null)), repo.recentOpens.value)
    }

    @Test
    fun `ouverture visible tout de suite, avant l'ecriture en base`() = runTest {
        dao.saved.complete(emptyList())
        dao.savedOpens.complete(emptyList())
        val repo = EventRepository(dao, this) { until }
        runCurrent()
        val open = OpenEvent(pkg, until, Outcome.GRANTED, null, 5)
        repo.recordOpen(open)
        assertEquals(listOf(open), repo.recentOpens.value)
        assertEquals(emptyList<OpenEventEntity>(), dao.writtenOpens)
        runCurrent()
        assertEquals(1, dao.writtenOpens.size)
    }
}
