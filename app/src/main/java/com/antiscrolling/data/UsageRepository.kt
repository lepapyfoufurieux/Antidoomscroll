package com.antiscrolling.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.antiscrolling.data.db.HistoryDao
import com.antiscrolling.domain.DayUsage
import com.antiscrolling.domain.UsageCalculator
import com.antiscrolling.domain.UsageEvent
import java.time.LocalDate
import java.time.ZonedDateTime

/** Jours recopiés par HistoryBackfill : temps par appli (vide si rien utilisé) et déverrouillages. */
data class History(val usage: Map<LocalDate, Map<String, Long>>, val unlocks: Map<LocalDate, Int>)

class UsageRepository(private val context: Context, private val historyDao: HistoryDao) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    /** Paramètres › Accès aux données d'utilisation. */
    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Événements utiles entre [from] et [to] (ms epoch), triés par date. Vide sans l'accès. */
    fun events(from: Long, to: Long): List<UsageEvent> {
        val out = mutableListOf<UsageEvent>()
        val events = usm.queryEvents(from, to) ?: return out
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) {
            // ACTIVITY_RESUMED / PAUSED valent MOVE_TO_FOREGROUND / BACKGROUND (1 et 2) sur Android 9.
            val type = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Type.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Type.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEvent.Type.UNLOCK
                else -> continue
            }
            out += UsageEvent(e.timeStamp, e.packageName, e.className, type)
        }
        return out
    }

    suspend fun history(from: LocalDate): History {
        val unlocks = historyDao.unlocksSince(from.toString()).associate { LocalDate.parse(it.date) to it.count }
        val usage = historyDao.usageSince(from.toString())
            .groupBy({ LocalDate.parse(it.date) }) { it.packageName to it.foregroundMs }
        return History(unlocks.keys.associateWith { usage[it].orEmpty().toMap() }, unlocks)
    }

    fun today(now: ZonedDateTime): DayUsage {
        val start = UsageCalculator.dayBounds(now.toLocalDate(), now.zone).first
        val nowMs = now.toInstant().toEpochMilli()
        return UsageCalculator.compute(events(start, nowMs), start, nowMs)
    }
}
