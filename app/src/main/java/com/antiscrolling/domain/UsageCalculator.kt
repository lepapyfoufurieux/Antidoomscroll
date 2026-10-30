package com.antiscrolling.domain

import java.time.LocalDate
import java.time.ZoneId

/** Événement d'usage d'Android, réduit à ce dont le calcul a besoin. */
data class UsageEvent(val timeMs: Long, val packageName: String, val className: String?, val type: Type) {
    enum class Type { RESUMED, PAUSED, SCREEN_OFF, UNLOCK }
}

data class DayUsage(val foregroundMs: Map<String, Long>, val unlocks: Int)

object UsageCalculator {
    /** Début et fin d'un jour en ms epoch : 23 h ou 25 h les jours de changement d'heure. */
    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Temps au premier plan par appli et déverrouillages entre [start] et [end].
     * [events] triés par date ; ceux d'avant [start] servent à savoir ce qui était déjà ouvert.
     */
    fun compute(events: List<UsageEvent>, start: Long, end: Long): DayUsage {
        val time = mutableMapOf<String, Long>()
        var unlocks = 0
        var foreground: String? = null
        val resumed = mutableSetOf<String?>() // activités ouvertes de l'appli au premier plan
        var seen = false
        var prev = minOf(start, events.firstOrNull()?.timeMs ?: start)

        fun count(until: Long) {
            val pkg = foreground ?: return
            val ms = minOf(until, end) - maxOf(prev, start)
            if (ms > 0) time.merge(pkg, ms, Long::plus)
        }

        for (e in events) {
            if (e.timeMs >= end) break
            // Premier événement = une fermeture : l'appli était ouverte avant le début des événements.
            if (!seen && e.type == UsageEvent.Type.PAUSED) foreground = e.packageName
            count(e.timeMs)
            prev = e.timeMs
            when (e.type) {
                UsageEvent.Type.RESUMED -> {
                    // Une autre appli passe devant : la précédente est fermée, même sans PAUSED.
                    if (foreground != e.packageName) resumed.clear()
                    foreground = e.packageName
                    resumed += e.className
                }
                UsageEvent.Type.PAUSED -> if (foreground == e.packageName) {
                    resumed -= e.className
                    if (resumed.isEmpty()) foreground = null
                }
                UsageEvent.Type.SCREEN_OFF -> {
                    foreground = null
                    resumed.clear()
                }
                UsageEvent.Type.UNLOCK -> if (e.timeMs >= start) unlocks++
            }
            if (e.type != UsageEvent.Type.UNLOCK) seen = true
        }
        count(end) // appli encore ouverte
        return DayUsage(time, unlocks)
    }
}
