package com.antiscrolling.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryBackfillTest {
    private val today = LocalDate.of(2026, 10, 29)

    @Test
    fun `recopie les jours termines apres le premier jour visible`() {
        assertEquals(
            listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)),
            daysToBackfill(today, LocalDate.of(2026, 10, 25), emptySet()),
        )
    }

    @Test
    fun `saute les jours deja en base, rattrape un jour rate`() {
        val saved = setOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28))
        assertEquals(listOf(LocalDate.of(2026, 10, 27)), daysToBackfill(today, LocalDate.of(2026, 10, 25), saved))
    }

    @Test
    fun `rien sans evenement, rien si le seul jour visible est hier ou aujourd'hui`() {
        assertEquals(emptyList<LocalDate>(), daysToBackfill(today, null, emptySet()))
        assertEquals(emptyList<LocalDate>(), daysToBackfill(today, today.minusDays(1), emptySet()))
        assertEquals(emptyList<LocalDate>(), daysToBackfill(today, today, emptySet()))
    }
}
