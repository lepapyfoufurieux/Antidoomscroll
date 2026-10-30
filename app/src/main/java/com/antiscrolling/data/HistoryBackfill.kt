package com.antiscrolling.data

import com.antiscrolling.data.db.DailyUnlocksEntity
import com.antiscrolling.data.db.DailyUsageEntity
import com.antiscrolling.data.db.HistoryDao
import com.antiscrolling.domain.UsageCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Recopie dans Room les jours terminés qu'Android garde encore, avant qu'il ne les efface. */
class HistoryBackfill(private val usage: UsageRepository, private val dao: HistoryDao) {
    private val mutex = Mutex()

    suspend fun run(now: ZonedDateTime) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!usage.hasAccess()) return@withLock
            val today = now.toLocalDate()
            val from = UsageCalculator.dayBounds(today.minusDays(LOOKBACK_DAYS), now.zone).first
            val to = UsageCalculator.dayBounds(today, now.zone).first
            val events = usage.events(from, to)
            val earliest = events.firstOrNull()?.let { Instant.ofEpochMilli(it.timeMs).atZone(now.zone).toLocalDate() }
            val saved = dao.savedDates().map(LocalDate::parse).toSet()
            for (day in daysToBackfill(today, earliest, saved)) {
                val (start, end) = UsageCalculator.dayBounds(day, now.zone)
                val result = UsageCalculator.compute(events, start, end)
                dao.insertDay(
                    result.foregroundMs.map { (pkg, ms) -> DailyUsageEntity(day.toString(), pkg, ms) },
                    DailyUnlocksEntity(day.toString(), result.unlocks),
                )
            }
        }
    }

    private companion object {
        // shortcut: durée de conservation d'Android non vérifiée, à ajuster si des jours manquent.
        const val LOOKBACK_DAYS = 10L
    }
}

/**
 * Jours à recopier : terminés (avant [today]), pas encore en base, et entièrement couverts
 * par les événements. Le jour de [earliest] est sauté : Android en a peut-être déjà effacé le début.
 */
fun daysToBackfill(today: LocalDate, earliest: LocalDate?, saved: Set<LocalDate>): List<LocalDate> {
    if (earliest == null) return emptyList()
    return generateSequence(earliest.plusDays(1)) { it.plusDays(1) }
        .takeWhile { it < today }
        .filter { it !in saved }
        .toList()
}
