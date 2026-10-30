package com.antiscrolling.service

import com.antiscrolling.domain.Decision
import com.antiscrolling.domain.FocusSession
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.Profile
import com.antiscrolling.domain.Outcome
import com.antiscrolling.domain.Reason
import com.antiscrolling.domain.Rule
import com.antiscrolling.domain.RuleSet
import com.antiscrolling.domain.Schedule
import com.antiscrolling.domain.Step
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class QuotaWatcherTest {
    private val pkg = "com.instagram.android"
    private var start = ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, ZoneId.of("Europe/Paris"))
    private val rules = MutableStateFlow<RuleSet?>(RuleSet.of(Rule(pkg, enabled = true, delaySec = 15)))
    private val grants = MutableStateFlow<Map<String, Instant>?>(emptyMap())
    private val focus = MutableStateFlow<List<FocusSession>?>(emptyList())
    private val opens = MutableStateFlow<List<OpenEvent>?>(emptyList())
    private var interactive = true
    private val decisions = mutableListOf<Decision>()
    private val alerts = mutableListOf<String>()
    private val usageReads = mutableListOf<ZonedDateTime>()
    private var usedTodayMs = 0L

    private fun TestScope.watcher() = QuotaWatcher(
        backgroundScope,
        rules,
        grants,
        focus,
        opens,
        { interactive },
        { start.plusNanos(testScheduler.currentTime * 1_000_000) },
        { _, at -> usageReads += at; usedTodayMs },
        { _, decision -> decisions += decision },
        { alerts += it },
    )

    private fun TestScope.tick() {
        advanceTimeBy(15_000)
        runCurrent()
    }

    private fun withQuota(min: Int) {
        rules.value = RuleSet.of(Rule(pkg, enabled = true, delaySec = 15, dailyQuotaMin = min))
    }

    @Test
    fun `decide tout de suite puis toutes les 15 s`() = runTest {
        watcher().watch(pkg)
        runCurrent()
        assertEquals(1, decisions.size)
        tick()
        assertEquals(2, decisions.size)
    }

    @Test
    fun `attend le chargement des regles et des acces avant de decider`() = runTest {
        rules.value = null
        grants.value = null
        watcher().watch(pkg)
        tick()
        rules.value = RuleSet.of(Rule(pkg, enabled = true, delaySec = 15))
        runCurrent()
        assertEquals(emptyList<Decision>(), decisions)
        grants.value = emptyMap()
        runCurrent()
        assertEquals(listOf(Decision.Friction(15, listOf(Step.DELAY))), decisions)
    }

    @Test
    fun `acces qui expire dans l'appli redonne la friction`() = runTest {
        grants.value = mapOf(pkg to start.plusSeconds(20).toInstant())
        watcher().watch(pkg)
        runCurrent()
        tick()
        tick()
        assertEquals(listOf(Decision.Allow, Decision.Allow, Decision.Friction(15, listOf(Step.DELAY))), decisions)
    }

    @Test
    fun `ecran eteint ne decide rien, rallume decide au passage suivant`() = runTest {
        interactive = false
        watcher().watch(pkg)
        runCurrent()
        tick()
        assertEquals(emptyList<Decision>(), decisions)
        interactive = true
        tick()
        assertEquals(1, decisions.size)
    }

    @Test
    fun `appli non suivie ou desactivee pas surveillee`() = runTest {
        val watcher = watcher()
        watcher.watch("com.other")
        runCurrent()
        rules.value = RuleSet.of(Rule(pkg, enabled = false, delaySec = 15))
        watcher.watch(pkg)
        runCurrent()
        tick()
        assertEquals(emptyList<Decision>(), decisions)
    }

    @Test
    fun `stop arrete la surveillance`() = runTest {
        val watcher = watcher()
        watcher.watch(pkg)
        runCurrent()
        watcher.stop()
        tick()
        assertEquals(1, decisions.size)
    }

    @Test
    fun `quota atteint dans l'appli bloque au passage suivant, meme avec un acces`() = runTest {
        withQuota(30)
        usedTodayMs = 30 * 60_000L - 20_000 // 20 s avant la limite
        grants.value = mapOf(pkg to start.plusMinutes(5).toInstant())
        watcher().watch(pkg)
        runCurrent()
        tick()
        tick()
        assertEquals(listOf(Decision.Allow, Decision.Allow, Decision.Block(Reason.QUOTA)), decisions)
        assertEquals(1, usageReads.size)
    }

    @Test
    fun `sans limite, UsageStats n'est pas lu`() = runTest {
        watcher().watch(pkg)
        runCurrent()
        tick()
        assertEquals(emptyList<ZonedDateTime>(), usageReads)
    }

    @Test
    fun `alerte une seule fois en entrant dans les 5 dernieres minutes`() = runTest {
        withQuota(30)
        usedTodayMs = 25 * 60_000L - 20_000
        grants.value = mapOf(pkg to start.plusMinutes(10).toInstant())
        val watcher = watcher()
        watcher.watch(pkg)
        runCurrent()
        tick()
        assertEquals(emptyList<String>(), alerts)
        tick()
        assertEquals(listOf(pkg), alerts)
        tick()
        watcher.recheck() // relit le temps, toujours dans les 5 dernières minutes
        runCurrent()
        assertEquals(listOf(pkg), alerts)
    }

    @Test
    fun `pas d'alerte ecran eteint`() = runTest {
        withQuota(30)
        usedTodayMs = 26 * 60_000L
        interactive = false
        watcher().watch(pkg)
        runCurrent()
        tick()
        assertEquals(emptyList<String>(), alerts)
    }

    @Test
    fun `apres minuit le temps du jour est relu et le quota repart`() = runTest {
        start = ZonedDateTime.of(2026, 10, 29, 23, 59, 50, 0, ZoneId.of("Europe/Paris"))
        withQuota(30)
        usedTodayMs = 30 * 60_000L
        watcher().watch(pkg)
        runCurrent()
        usedTodayMs = 0
        tick()
        assertEquals(listOf(Decision.Block(Reason.QUOTA), Decision.Friction(15, listOf(Step.DELAY))), decisions)
        assertEquals(2, usageReads.size)
    }

    @Test
    fun `l'ecran eteint ne compte pas, le temps est relu au retour`() = runTest {
        withQuota(30)
        usedTodayMs = 29 * 60_000L
        grants.value = mapOf(pkg to start.plusHours(1).toInstant())
        watcher().watch(pkg)
        runCurrent()
        interactive = false
        repeat(8) { tick() } // 2 min écran éteint : sans relecture, la limite serait dépassée
        interactive = true
        tick()
        assertEquals(listOf(Decision.Allow, Decision.Allow), decisions)
        assertEquals(2, usageReads.size)
    }

    @Test
    fun `attend aussi le chargement du focus`() = runTest {
        focus.value = null
        watcher().watch(pkg)
        runCurrent()
        assertEquals(emptyList<Decision>(), decisions)
        focus.value = listOf(FocusSession(1, start.minusMinutes(1).toInstant(), start.plusHours(1).toInstant()))
        runCurrent()
        assertEquals(listOf(Decision.Block(Reason.FOCUS)), decisions)
    }

    @Test
    fun `plage qui commence dans l'appli bloque au passage suivant`() = runTest {
        start = start.withHour(21).withMinute(59).withSecond(50) // plage à 22 h
        rules.value = RuleSet.of(Rule(pkg, true, 15, schedules = listOf(Schedule(1, Schedule.EVERY_DAY, 22 * 60, 7 * 60))))
        grants.value = mapOf(pkg to start.plusHours(1).toInstant())
        watcher().watch(pkg)
        runCurrent()
        tick()
        assertEquals(listOf(Decision.Allow, Decision.Block(Reason.SCHEDULE)), decisions)
    }

    @Test
    fun `profil qui commence dans l'appli applique ses regles au passage suivant`() = runTest {
        start = start.withHour(21).withMinute(59).withSecond(50)
        val night = Profile(1, "Nuit", listOf(Schedule(1, Schedule.EVERY_DAY, 22 * 60, 7 * 60)))
        // Libre dans le profil par défaut, freinée seulement la nuit.
        rules.value = RuleSet(listOf(night), mapOf(1L to mapOf(pkg to Rule(pkg, true, 15))))
        watcher().watch(pkg)
        runCurrent()
        assertEquals(emptyList<Decision>(), decisions)
        tick()
        assertEquals(listOf(Decision.Friction(15, listOf(Step.DELAY))), decisions)
    }

    @Test
    fun `les ouvertures du jour allongent le delai, et attendent leur chargement`() = runTest {
        rules.value = RuleSet.of(Rule(pkg, true, 15, delayIncrementSec = 10))
        opens.value = null
        watcher().watch(pkg)
        runCurrent()
        assertEquals(emptyList<Decision>(), decisions)
        opens.value = List(3) { OpenEvent(pkg, start.minusMinutes(it + 1L).toInstant(), Outcome.GRANTED, null, 5) }
        runCurrent()
        assertEquals(listOf(Decision.Friction(45, listOf(Step.DELAY))), decisions)
    }
}
