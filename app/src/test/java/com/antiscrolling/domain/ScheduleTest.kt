package com.antiscrolling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ScheduleTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, paris)
    private val weekdays = DayOfWeek.entries.take(5).sumOf { Schedule.dayBit(it) }
    private val monday = Schedule.dayBit(DayOfWeek.MONDAY)

    @Test
    fun `plage de la journee, debut inclus, fin exclue`() {
        val work = Schedule(1, weekdays, 9 * 60, 17 * 60)
        assertFalse(work.contains(at(2026, 10, 29, 8, 59))) // jeudi
        assertTrue(work.contains(at(2026, 10, 29, 9, 0)))
        assertTrue(work.contains(at(2026, 10, 29, 16, 59)))
        assertFalse(work.contains(at(2026, 10, 29, 17, 0)))
    }

    @Test
    fun `jour hors du masque non bloque`() {
        val work = Schedule(1, weekdays, 9 * 60, 17 * 60)
        assertFalse(work.contains(at(2026, 10, 31, 10, 0))) // samedi
    }

    @Test
    fun `plage qui chevauche minuit rattachee au jour ou elle commence`() {
        val night = Schedule(1, monday, 22 * 60, 7 * 60)
        assertFalse(night.contains(at(2026, 10, 26, 21, 59))) // lundi
        assertTrue(night.contains(at(2026, 10, 26, 22, 0)))
        assertTrue(night.contains(at(2026, 10, 27, 0, 0))) // mardi
        assertTrue(night.contains(at(2026, 10, 27, 6, 59)))
        assertFalse(night.contains(at(2026, 10, 27, 7, 0)))
        assertFalse(night.contains(at(2026, 10, 27, 22, 30))) // mardi soir : pas dans le masque
        assertFalse(night.contains(at(2026, 10, 26, 3, 0))) // lundi matin : la nuit de dimanche n'est pas dans le masque
    }

    @Test
    fun `debut egal a la fin couvre la journee entiere`() {
        val allDay = Schedule(1, monday, 0, 0)
        assertTrue(allDay.contains(at(2026, 10, 26, 0, 0)))
        assertTrue(allDay.contains(at(2026, 10, 26, 23, 59)))
        assertFalse(allDay.contains(at(2026, 10, 27, 0, 0)))
    }

    @Test
    fun `changements d'heure suivent l'heure affichee`() {
        val early = Schedule(1, Schedule.EVERY_DAY, 2 * 60, 3 * 60)
        // 25/10/2026 : 2 h 30 existe deux fois, les deux sont dans la plage.
        val first = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 10, 25, 2, 30), paris, ZoneOffset.ofHours(2))
        assertTrue(early.contains(first))
        assertTrue(early.contains(first.withLaterOffsetAtOverlap()))
        // 28/03/2027 : 2 h → 3 h, l'heure affichée saute la plage, 3 h 00 n'en fait pas partie.
        assertFalse(early.contains(at(2027, 3, 28, 3, 0)))
        assertTrue(Schedule(1, Schedule.EVERY_DAY, 22 * 60, 7 * 60).contains(at(2027, 3, 28, 3, 0)))
    }

    @Test
    fun `focus actif de son debut inclus a sa fin exclue`() {
        val start = at(2026, 10, 29, 14, 0)
        val focus = FocusSession(1, start.toInstant(), start.plusHours(1).toInstant())
        assertFalse(focus.isActive(start.minusSeconds(1)))
        assertTrue(focus.isActive(start))
        assertTrue(focus.isActive(start.plusMinutes(59)))
        assertFalse(focus.isActive(start.plusHours(1)))
    }

    @Test
    fun `fin de la plage en cours, le jour meme, le lendemain ou a minuit`() {
        val work = Schedule(1, weekdays, 9 * 60, 17 * 60)
        assertEquals(at(2026, 10, 29, 17, 0), work.endAfter(at(2026, 10, 29, 10, 0)))
        val night = Schedule(1, Schedule.EVERY_DAY, 22 * 60, 7 * 60)
        assertEquals(at(2026, 10, 30, 7, 0), night.endAfter(at(2026, 10, 29, 23, 0)))
        assertEquals(at(2026, 10, 30, 7, 0), night.endAfter(at(2026, 10, 30, 2, 0)))
        assertEquals(at(2026, 10, 27, 0, 0), Schedule(1, monday, 0, 0).endAfter(at(2026, 10, 26, 15, 0)))
    }
}
