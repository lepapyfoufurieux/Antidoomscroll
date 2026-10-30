package com.antiscrolling.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.antiscrolling.container
import com.antiscrolling.domain.RuleEngine
import com.antiscrolling.domain.opensToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Met en attente les notifications d'une appli bloquée (focus, plage, limite) jusqu'à la fin du blocage,
 * au lieu de les supprimer. Ne lit que le package de la notification.
 */
class NotificationBlockerService : NotificationListenerService() {
    private val scope = MainScope()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (sbn.isOngoing || pkg == packageName) return // musique, appels, navigation : on ne touche pas
        scope.launch {
            val c = container
            val now = ZonedDateTime.now()
            val rule = c.ruleRepository.rules.filterNotNull().first().at(now)[pkg] ?: return@launch
            if (!rule.enabled || !rule.hideNotifications) return@launch
            val used = if (rule.dailyQuotaMin == null) 0L else {
                withContext(Dispatchers.IO) { c.usageRepository.today(now).foregroundMs[pkg] ?: 0L }
            }
            val ctx = RuleEngine.Context(
                used,
                c.eventRepository.grants.filterNotNull().first()[pkg],
                c.ruleRepository.latestFocus.filterNotNull().first().firstOrNull(),
                c.eventRepository.recentOpens.filterNotNull().first().opensToday(pkg, now),
            )
            // Remise en attente à son retour si un autre blocage a pris le relais.
            val until = RuleEngine.blockedUntil(rule, now, ctx) ?: return@launch
            snoozeNotification(sbn.key, Duration.between(now, until).toMillis())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Paramètres › Notifications › Accès aux notifications. */
        fun isEnabled(context: Context): Boolean {
            val self = ComponentName(context, NotificationBlockerService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}
