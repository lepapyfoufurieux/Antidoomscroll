package com.antiscrolling.domain

import java.time.LocalDate

data class WeekTotals(
    val thisWeek: Map<String, Long>, // 7 derniers jours terminés
    val lastWeek: Map<String, Long>, // les 7 d'avant
    val lastWeekComplete: Boolean, // les 7 jours d'avant sont tous dans l'historique
)

data class OpenStats(val granted: Map<String, Int>, val gaveUp: Map<String, Int>, val intents: Map<String, Int>)

object StatsCalculator {
    // Le blocage arrive au plus 15 s après la limite : une minute de marge pour ne pas casser la série pour ça.
    private const val TOLERANCE_MS = 60_000L

    /**
     * Jours d'affilée, en remontant depuis hier, où aucune appli n'a dépassé sa limite.
     * [usage] : un jour par date enregistrée (même sans usage) ; un jour absent coupe la série.
     * shortcut: compare aux limites actuelles, pas à celles de chaque jour ; garder un historique des limites si ça gêne.
     */
    fun streak(usage: Map<LocalDate, Map<String, Long>>, limitsMin: Map<String, Int>, today: LocalDate): Int {
        if (limitsMin.isEmpty()) return 0
        var day = today.minusDays(1)
        var count = 0
        while (true) {
            val apps = usage[day] ?: return count
            if (limitsMin.any { (pkg, min) -> (apps[pkg] ?: 0) > min * 60_000L + TOLERANCE_MS }) return count
            count++
            day = day.minusDays(1)
        }
    }

    /** Temps par appli de [packages] sur les 7 derniers jours terminés, et sur les 7 d'avant. */
    fun weekOverWeek(usage: Map<LocalDate, Map<String, Long>>, packages: Set<String>, today: LocalDate): WeekTotals {
        fun sum(days: List<LocalDate>) = days.mapNotNull { usage[it] }
            .flatMap { it.entries }
            .filter { it.key in packages }
            .groupingBy { it.key }
            .fold(0L) { total, entry -> total + entry.value }
        val thisWeek = (1L..7L).map { today.minusDays(it) }
        val lastWeek = (8L..14L).map { today.minusDays(it) }
        return WeekTotals(sum(thisWeek), sum(lastWeek), lastWeek.all { it in usage })
    }

    /** Évolution en %, arrondie ; `null` si rien à comparer. */
    fun change(now: Long, before: Long): Int? = if (before == 0L) null else Math.round((now - before) * 100.0 / before).toInt()

    /** Moyenne des déverrouillages sur les 7 derniers jours enregistrés ; `null` sans historique. */
    fun averageUnlocks(unlocks: Map<LocalDate, Int>, today: LocalDate): Int? {
        val days = (1L..7L).mapNotNull { unlocks[today.minusDays(it)] }
        return if (days.isEmpty()) null else Math.round(days.average()).toInt()
    }

    fun opens(events: List<OpenEvent>): OpenStats = OpenStats(
        granted = events.filter { it.outcome == Outcome.GRANTED }.groupingBy { it.packageName }.eachCount(),
        gaveUp = events.filter { it.outcome == Outcome.GAVE_UP }.groupingBy { it.packageName }.eachCount(),
        intents = events.mapNotNull { it.intent }.groupingBy { it }.eachCount(),
    )
}
