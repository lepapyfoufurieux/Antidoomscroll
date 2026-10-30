package com.antiscrolling.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {
    private val now = ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, ZoneId.of("Europe/Paris")) // jeudi
    private val rule = Rule("com.instagram.android", enabled = true, delaySec = 15)
    private val withQuota = rule.copy(dailyQuotaMin = 30)
    private val thirtyMin = 30 * 60_000L
    private val noGrant = RuleEngine.Context(usedTodayMs = 0, grantUntil = null, focus = null)
    private val grantInFive = noGrant.copy(grantUntil = now.plusMinutes(5).toInstant())
    private val focusNow = FocusSession(1, now.minusMinutes(10).toInstant(), now.plusMinutes(50).toInstant())
    private val eveningPlage = Schedule(1, Schedule.EVERY_DAY, 20 * 60, 22 * 60)

    @Test
    fun `appli non suivie ou desactivee autorisee`() {
        assertEquals(Decision.Allow, RuleEngine.decide(null, now, noGrant))
        assertEquals(Decision.Allow, RuleEngine.decide(rule.copy(enabled = false), now, noGrant))
        assertEquals(Decision.Allow, RuleEngine.decide(withQuota.copy(enabled = false), now, noGrant.copy(usedTodayMs = thirtyMin)))
    }

    @Test
    fun `appli suivie sans acces donne la friction`() {
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), RuleEngine.decide(rule, now, noGrant))
    }

    @Test
    fun `acces en cours leve la friction, acces expire non`() {
        val expired = noGrant.copy(grantUntil = now.minusSeconds(1).toInstant())
        val endsNow = noGrant.copy(grantUntil = now.toInstant())
        assertEquals(Decision.Allow, RuleEngine.decide(rule, now, grantInFive))
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), RuleEngine.decide(rule, now, expired))
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), RuleEngine.decide(rule, now, endsNow))
    }

    @Test
    fun `quota atteint pile bloque, juste avant non`() {
        assertEquals(Decision.Block(Reason.QUOTA), RuleEngine.decide(withQuota, now, noGrant.copy(usedTodayMs = thirtyMin)))
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), RuleEngine.decide(withQuota, now, noGrant.copy(usedTodayMs = thirtyMin - 1)))
    }

    @Test
    fun `un acces ne leve pas le quota`() {
        assertEquals(Decision.Block(Reason.QUOTA), RuleEngine.decide(withQuota, now, grantInFive.copy(usedTodayMs = thirtyMin)))
        assertEquals(Decision.Allow, RuleEngine.decide(withQuota, now, grantInFive.copy(usedTodayMs = thirtyMin - 1)))
    }

    @Test
    fun `sans limite, le temps du jour ne bloque jamais`() {
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), RuleEngine.decide(rule, now, noGrant.copy(usedTodayMs = 24 * 60 * 60_000L)))
    }

    @Test
    fun `plage en cours bloque, meme avec un acces`() {
        val inPlage = rule.copy(schedules = listOf(eveningPlage))
        assertEquals(Decision.Block(Reason.SCHEDULE), RuleEngine.decide(inPlage, now, noGrant))
        assertEquals(Decision.Block(Reason.SCHEDULE), RuleEngine.decide(inPlage, now, grantInFive))
        assertEquals(Decision.Allow, RuleEngine.decide(inPlage, now.plusHours(1), grantInFive.copy(grantUntil = now.plusHours(2).toInstant())))
    }

    @Test
    fun `focus actif bloque les applis suivies, avant la plage et le quota`() {
        val everything = withQuota.copy(schedules = listOf(eveningPlage))
        val ctx = grantInFive.copy(usedTodayMs = thirtyMin, focus = focusNow)
        assertEquals(Decision.Block(Reason.FOCUS), RuleEngine.decide(everything, now, ctx))
        assertEquals(Decision.Block(Reason.FOCUS), RuleEngine.decide(rule, now, noGrant.copy(focus = focusNow)))
        assertEquals(Decision.Allow, RuleEngine.decide(null, now, noGrant.copy(focus = focusNow)))
        assertEquals(Decision.Allow, RuleEngine.decide(rule.copy(enabled = false), now, noGrant.copy(focus = focusNow)))
    }

    @Test
    fun `focus termine ne bloque plus`() {
        val ended = focusNow.copy(endAt = now.toInstant())
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), RuleEngine.decide(rule, now, noGrant.copy(focus = ended)))
    }

    @Test
    fun `fin du blocage selon la raison, rien pour une friction`() {
        assertEquals(focusNow.endAt.atZone(now.zone), RuleEngine.blockedUntil(rule, now, noGrant.copy(focus = focusNow)))
        assertEquals(now.withHour(22), RuleEngine.blockedUntil(rule.copy(schedules = listOf(eveningPlage)), now, noGrant))
        assertEquals(now.plusDays(1).toLocalDate().atStartOfDay(now.zone), RuleEngine.blockedUntil(withQuota, now, noGrant.copy(usedTodayMs = thirtyMin)))
        assertEquals(null, RuleEngine.blockedUntil(rule, now, noGrant))
        assertEquals(null, RuleEngine.blockedUntil(null, now, noGrant.copy(focus = focusNow)))
    }

    @Test
    fun `plusieurs plages en cours, la plus tardive l'emporte`() {
        val late = Schedule(2, Schedule.EVERY_DAY, 18 * 60, 23 * 60)
        assertEquals(now.withHour(23), RuleEngine.blockedUntil(rule.copy(schedules = listOf(eveningPlage, late)), now, noGrant))
    }

    @Test
    fun `le delai croissant passe par le contexte`() {
        val growing = rule.copy(delayIncrementSec = 10)
        assertEquals(Decision.Friction(35, listOf(Step.DELAY)), RuleEngine.decide(growing, now, noGrant.copy(opensToday = 2)))
    }
}
