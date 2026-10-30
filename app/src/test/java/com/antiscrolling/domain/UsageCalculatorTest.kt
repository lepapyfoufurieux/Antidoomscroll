package com.antiscrolling.domain

import com.antiscrolling.domain.UsageEvent.Type.PAUSED
import com.antiscrolling.domain.UsageEvent.Type.RESUMED
import com.antiscrolling.domain.UsageEvent.Type.SCREEN_OFF
import com.antiscrolling.domain.UsageEvent.Type.UNLOCK
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UsageCalculatorTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val day = UsageCalculator.dayBounds(LocalDate.of(2026, 10, 29), paris)
    private val start = day.first
    private val end = day.second
    private val insta = "com.instagram.android"
    private val chrome = "com.android.chrome"

    private fun min(m: Long) = m * 60_000
    private fun at(minutes: Long, pkg: String, type: UsageEvent.Type, cls: String? = "Main") =
        UsageEvent(start + min(minutes), pkg, cls, type)

    @Test
    fun `sessions normales additionnees par appli`() {
        val events = listOf(
            at(600, insta, RESUMED), at(610, insta, PAUSED),
            at(611, chrome, RESUMED), at(615, chrome, PAUSED),
            at(700, insta, RESUMED), at(705, insta, PAUSED),
        )
        assertEquals(mapOf(insta to min(15), chrome to min(4)), UsageCalculator.compute(events, start, end).foregroundMs)
    }

    @Test
    fun `plusieurs activites de la meme appli comptees une fois`() {
        val events = listOf(
            at(600, insta, RESUMED, "Feed"),
            at(602, insta, RESUMED, "Story"),
            at(603, insta, PAUSED, "Feed"),
            at(606, insta, PAUSED, "Story"),
        )
        assertEquals(mapOf(insta to min(6)), UsageCalculator.compute(events, start, end).foregroundMs)
    }

    @Test
    fun `PAUSED manquant, l'appli suivante ferme la precedente`() {
        val events = listOf(
            at(600, insta, RESUMED),
            at(610, chrome, RESUMED), at(612, chrome, PAUSED),
            at(613, insta, PAUSED), // arrivé en retard : ignoré
        )
        assertEquals(mapOf(insta to min(10), chrome to min(2)), UsageCalculator.compute(events, start, end).foregroundMs)
    }

    @Test
    fun `session commencee la veille comptee a partir de minuit`() {
        val withPreviousDay = listOf(at(-10, insta, RESUMED), at(5, insta, PAUSED))
        assertEquals(mapOf(insta to min(5)), UsageCalculator.compute(withPreviousDay, start, end).foregroundMs)

        val startingAtMidnight = listOf(at(5, insta, PAUSED), at(6, chrome, RESUMED), at(7, chrome, PAUSED))
        assertEquals(
            mapOf(insta to min(5), chrome to min(1)),
            UsageCalculator.compute(startingAtMidnight, start, end).foregroundMs,
        )
    }

    @Test
    fun `appli encore ouverte comptee jusqu'a la fin, pas au-dela`() {
        val events = listOf(at(600, insta, RESUMED))
        assertEquals(mapOf(insta to min(20)), UsageCalculator.compute(events, start, start + min(620)).foregroundMs)

        val crossingMidnight = listOf(at(23 * 60 + 50, insta, RESUMED), at(24 * 60 + 10, insta, PAUSED))
        assertEquals(mapOf(insta to min(10)), UsageCalculator.compute(crossingMidnight, start, end).foregroundMs)
    }

    @Test
    fun `ecran eteint arrete le compte`() {
        val events = listOf(
            at(600, insta, RESUMED),
            UsageEvent(start + min(604), "android", null, SCREEN_OFF),
            at(650, insta, PAUSED),
        )
        assertEquals(mapOf(insta to min(4)), UsageCalculator.compute(events, start, end).foregroundMs)
    }

    @Test
    fun `journee vide`() {
        assertEquals(DayUsage(emptyMap(), 0), UsageCalculator.compute(emptyList(), start, end))
    }

    @Test
    fun `deverrouillages comptes dans la journee seulement`() {
        val events = listOf(-5L, 10L, 300L, 24 * 60 + 1L).map { UsageEvent(start + min(it), "android", null, UNLOCK) }
        assertEquals(2, UsageCalculator.compute(events, start, end).unlocks)
    }

    @Test
    fun `jours de changement d'heure de 25 h et 23 h`() {
        val winter = UsageCalculator.dayBounds(LocalDate.of(2026, 10, 25), paris)
        val summer = UsageCalculator.dayBounds(LocalDate.of(2027, 3, 28), paris)
        assertEquals(min(25 * 60), winter.second - winter.first)
        assertEquals(min(23 * 60), summer.second - summer.first)
        assertEquals(min(24 * 60), end - start)
    }
}
