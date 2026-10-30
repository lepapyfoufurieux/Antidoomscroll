package com.antiscrolling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val insta = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private fun min(m: Int) = m * 60_000L
    private fun daysAgo(n: Long) = today.minusDays(n)

    @Test
    fun `serie, jours d'affilee sous la limite jusqu'a hier`() {
        val usage = mapOf(
            daysAgo(1) to mapOf(insta to min(20)),
            daysAgo(2) to emptyMap(), // pas ouverte : sous la limite
            daysAgo(3) to mapOf(insta to min(32)), // limite dépassée au-delà de la marge
            daysAgo(4) to mapOf(insta to min(5)),
        )
        assertEquals(2, StatsCalculator.streak(usage, mapOf(insta to 30), today))
    }

    @Test
    fun `serie, la minute de marge et le jour atteint pile comptent`() {
        val usage = mapOf(daysAgo(1) to mapOf(insta to min(31)), daysAgo(2) to mapOf(insta to min(30)))
        assertEquals(2, StatsCalculator.streak(usage, mapOf(insta to 30), today))
    }

    @Test
    fun `jour au-dela de la limite, avec la meme marge que la serie`() {
        val limits = mapOf(insta to 30)
        assertFalse(StatsCalculator.overLimit(mapOf(insta to min(31)), limits))
        assertTrue(StatsCalculator.overLimit(mapOf(insta to min(31) + 1), limits))
        assertFalse(StatsCalculator.overLimit(mapOf(tiktok to min(500)), limits)) // sans limite
        assertFalse(StatsCalculator.overLimit(emptyMap(), limits))
    }

    @Test
    fun `serie coupee par un jour absent, nulle sans limite, aujourd'hui ne compte pas`() {
        val usage = mapOf(today to emptyMap<String, Long>(), daysAgo(1) to emptyMap(), daysAgo(3) to emptyMap())
        assertEquals(1, StatsCalculator.streak(usage, mapOf(insta to 30), today))
        assertEquals(0, StatsCalculator.streak(usage, emptyMap(), today))
        assertEquals(0, StatsCalculator.streak(emptyMap(), mapOf(insta to 30), today))
    }

    @Test
    fun `serie, une seule appli au-dessus suffit a la couper`() {
        val usage = mapOf(daysAgo(1) to mapOf(insta to min(10), tiktok to min(50)))
        assertEquals(0, StatsCalculator.streak(usage, mapOf(insta to 30, tiktok to 45), today))
    }

    @Test
    fun `semaine contre semaine, applis suivies seulement`() {
        val usage = (1L..14L).associate { daysAgo(it) to mapOf(insta to min(if (it <= 7) 10 else 20), "launcher" to min(99)) }
        val week = StatsCalculator.weekOverWeek(usage, setOf(insta), today)
        assertEquals(mapOf(insta to min(70)), week.thisWeek)
        assertEquals(mapOf(insta to min(140)), week.lastWeek)
        assertTrue(week.lastWeekComplete)
        assertEquals(-50, StatsCalculator.change(min(70), min(140)))
    }

    @Test
    fun `semaine precedente incomplete, aujourd'hui exclu`() {
        val usage = mapOf(today to mapOf(insta to min(500)), daysAgo(1) to mapOf(insta to min(10)), daysAgo(9) to mapOf(insta to min(5)))
        val week = StatsCalculator.weekOverWeek(usage, setOf(insta), today)
        assertEquals(mapOf(insta to min(10)), week.thisWeek)
        assertFalse(week.lastWeekComplete)
        assertNull(StatsCalculator.change(min(10), 0))
    }

    @Test
    fun `moyenne des deverrouillages sur les jours enregistres`() {
        assertEquals(20, StatsCalculator.averageUnlocks(mapOf(daysAgo(1) to 10, daysAgo(2) to 30, daysAgo(8) to 999, today to 999), today))
        assertNull(StatsCalculator.averageUnlocks(mapOf(today to 5), today))
    }

    @Test
    fun `ouvertures, renoncements et raisons`() {
        val at = Instant.parse("2026-10-28T10:00:00Z")
        val events = listOf(
            OpenEvent(insta, at, Outcome.GRANTED, "Par habitude", 5),
            OpenEvent(insta, at, Outcome.GRANTED, null, 5),
            OpenEvent(insta, at, Outcome.GAVE_UP, "Par habitude", null),
            OpenEvent(tiktok, at, Outcome.GAVE_UP, "M'occuper", null),
        )
        assertEquals(
            OpenStats(mapOf(insta to 2), mapOf(insta to 1, tiktok to 1), mapOf("Par habitude" to 2, "M'occuper" to 1)),
            StatsCalculator.opens(events),
        )
    }
}
