package com.antiscrolling.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

private const val MINUTE_MS = 60_000L
private const val WARN_BEFORE_MIN = 5
const val MAX_DELAY_SEC = 120 // plafond du délai croissant (sauf délai de base plus long)

data class Rule(
    val packageName: String,
    val enabled: Boolean,
    val delaySec: Int,
    val dailyQuotaMin: Int? = null, // null : pas de limite
    val schedules: List<Schedule> = emptyList(),
    val delayIncrementSec: Int = 0, // ajouté au délai à chaque ouverture du jour
    val askIntent: Boolean = false,
    val task: TaskKind = TaskKind.NONE,
    val askDuration: Boolean = false,
    val hideNotifications: Boolean = false,
) {
    fun quotaReached(usedMs: Long): Boolean = dailyQuotaMin != null && usedMs >= dailyQuotaMin * MINUTE_MS

    /** Temps restant avant la limite, jamais négatif ; `null` sans limite. */
    fun remainingMs(usedMs: Long): Long? = dailyQuotaMin?.let { maxOf(0, it * MINUTE_MS - usedMs) }

    /** Frictions activées, dans l'ordre : attente, intention, tâche, durée. Aucune : l'appli s'ouvre librement. */
    fun friction(opensToday: Int): Decision {
        val delay = minOf(delaySec + opensToday * delayIncrementSec, maxOf(delaySec, MAX_DELAY_SEC))
        val steps = buildList {
            if (delay > 0) add(Step.DELAY)
            if (askIntent) add(Step.INTENT)
            if (task != TaskKind.NONE) add(Step.TASK)
            if (askDuration) add(Step.DURATION)
        }
        return if (steps.isEmpty()) Decision.Allow else Decision.Friction(delay, steps)
    }

    /** Dans les 5 dernières minutes avant la limite. Jamais pour une limite de 5 min ou moins : elle commencerait par l'alerte. */
    fun quotaSoon(usedMs: Long): Boolean =
        dailyQuotaMin != null && dailyQuotaMin > WARN_BEFORE_MIN &&
            usedMs >= (dailyQuotaMin - WARN_BEFORE_MIN) * MINUTE_MS && !quotaReached(usedMs)
}

/**
 * Plage de blocage, en heure locale : [startMin] inclus, [endMin] exclu, en minutes depuis minuit.
 * Si [endMin] <= [startMin], la plage finit le lendemain ; égales, elle couvre la journée entière.
 * [daysMask] : bit 0 = lundi … bit 6 = dimanche, le jour où la plage commence.
 */
data class Schedule(val id: Long, val daysMask: Int, val startMin: Int, val endMin: Int) {
    fun contains(now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val today = daysMask and dayBit(now.dayOfWeek) != 0
        val yesterday = daysMask and dayBit(now.dayOfWeek.minus(1)) != 0
        return when {
            startMin == endMin -> today
            startMin < endMin -> today && minute >= startMin && minute < endMin
            else -> (today && minute >= startMin) || (yesterday && minute < endMin)
        }
    }

    /** Fin de la plage en cours, à appeler quand [contains] est vrai. Journée entière : minuit suivant. */
    fun endAfter(now: ZonedDateTime): ZonedDateTime {
        val minute = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        val endDay = when {
            startMin == endMin -> return today.plusDays(1).atStartOfDay(now.zone)
            startMin < endMin || minute < endMin -> today
            else -> today.plusDays(1)
        }
        return ZonedDateTime.of(endDay, LocalTime.of(endMin / 60, endMin % 60), now.zone)
    }

    companion object {
        const val EVERY_DAY = 0b1111111
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

/** Mode focus : bloque toutes les applis suivies de [startAt] (inclus) à [endAt] (exclu). */
data class FocusSession(val id: Long, val startAt: Instant, val endAt: Instant) {
    fun isActive(now: ZonedDateTime): Boolean = !now.toInstant().isBefore(startAt) && now.toInstant().isBefore(endAt)
}
