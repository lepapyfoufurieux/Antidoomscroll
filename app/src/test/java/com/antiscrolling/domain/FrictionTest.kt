package com.antiscrolling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FrictionTest {
    private val pkg = "com.instagram.android"
    private val rule = Rule(pkg, enabled = true, delaySec = 15)
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `delai croissant 0, 1, plafond et au-dela`() {
        val growing = rule.copy(delayIncrementSec = 30)
        assertEquals(Decision.Friction(15, listOf(Step.DELAY)), growing.friction(0))
        assertEquals(Decision.Friction(45, listOf(Step.DELAY)), growing.friction(1))
        assertEquals(Decision.Friction(MAX_DELAY_SEC, listOf(Step.DELAY)), growing.friction(4)) // 135 → 120
        assertEquals(Decision.Friction(MAX_DELAY_SEC, listOf(Step.DELAY)), growing.friction(50))
    }

    @Test
    fun `un delai de base plus long que le plafond reste tel quel`() {
        assertEquals(Decision.Friction(300, listOf(Step.DELAY)), rule.copy(delaySec = 300, delayIncrementSec = 10).friction(3))
    }

    @Test
    fun `etapes dans l'ordre attente, intention, tache, duree`() {
        val all = rule.copy(askIntent = true, task = TaskKind.MATH, askDuration = true)
        assertEquals(listOf(Step.DELAY, Step.INTENT, Step.TASK, Step.DURATION), (all.friction(0) as Decision.Friction).steps)
        val noDelay = all.copy(delaySec = 0)
        assertEquals(listOf(Step.INTENT, Step.TASK, Step.DURATION), (noDelay.friction(0) as Decision.Friction).steps)
    }

    @Test
    fun `aucune friction activee, l'appli s'ouvre librement`() {
        assertEquals(Decision.Allow, rule.copy(delaySec = 0).friction(0))
        // délai de base nul mais croissant : libre à la première ouverture, puis attente
        val growing = rule.copy(delaySec = 0, delayIncrementSec = 10)
        assertEquals(Decision.Allow, growing.friction(0))
        assertEquals(Decision.Friction(20, listOf(Step.DELAY)), growing.friction(2))
    }

    @Test
    fun `progression jusqu'a l'acces, duree choisie ou par defaut`() {
        var progress = FrictionProgress(listOf(Step.DELAY, Step.INTENT, Step.DURATION))
        assertEquals(Step.DELAY, progress.current)
        progress = progress.next().withIntent("Par habitude")
        assertTrue(progress.isLast)
        assertEquals(Step.DURATION, progress.current)
        progress = progress.withDuration(10)
        assertTrue(progress.done)
        assertNull(progress.current)
        assertEquals("Par habitude", progress.intent)
        assertEquals(10, progress.grantMinutes(5))
        assertEquals(5, FrictionProgress(listOf(Step.DELAY)).next().grantMinutes(5))
    }

    @Test
    fun `taches, recopie tolerante aux espaces et a la casse, calcul exact`() {
        val copy = Task.create(TaskKind.COPY) { 0.0 }!!
        assertTrue(copy.check("  j'ouvre cette  appli parce que je l'ai DÉCIDÉ. "))
        assertFalse(copy.check("j'ouvre cette appli"))
        val math = Task.create(TaskKind.MATH) { 0.5 } as Task.Math
        assertEquals(Task.Math(55, 55), math)
        assertTrue(math.check(" 110 "))
        assertFalse(math.check("111"))
        assertFalse(math.check("cent dix"))
        assertNull(Task.create(TaskKind.NONE) { 0.0 })
    }

    @Test
    fun `ouvertures du jour, accordees seulement, remises a zero a minuit`() {
        val now = ZonedDateTime.of(2026, 10, 29, 0, 10, 0, 0, paris)
        fun open(minutesAgo: Long, outcome: Outcome = Outcome.GRANTED, app: String = pkg) =
            OpenEvent(app, now.minusMinutes(minutesAgo).toInstant(), outcome, null, 5)
        val events = listOf(open(5), open(10), open(11), open(3, Outcome.GAVE_UP), open(2, app = "other"))
        assertEquals(2, events.opensToday(pkg, now)) // 0 h 05 et 0 h 00 ; 23 h 59 la veille ne compte pas
        assertEquals(0, emptyList<OpenEvent>().opensToday(pkg, now))
    }
}
