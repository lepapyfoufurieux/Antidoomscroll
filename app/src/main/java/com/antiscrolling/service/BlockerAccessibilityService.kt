package com.antiscrolling.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.antiscrolling.container
import com.antiscrolling.domain.Decision
import com.antiscrolling.domain.TaskKind
import com.antiscrolling.ui.block.BlockActivity
import com.antiscrolling.widget.TimeLeftWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class BlockerAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private lateinit var watcher: QuotaWatcher
    private var launcher: String? = null
    private var lastWidgetRefresh = 0L

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = watcher.recheck()
    }

    override fun onCreate() {
        super.onCreate()
        val power = getSystemService(PowerManager::class.java)
        watcher = QuotaWatcher(
            scope,
            container.ruleRepository.rules,
            container.eventRepository.grants,
            container.ruleRepository.latestFocus,
            container.eventRepository.recentOpens,
            power::isInteractive,
            ZonedDateTime::now,
            { pkg, now -> withContext(Dispatchers.IO) { container.usageRepository.today(now).foregroundMs[pkg] ?: 0L } },
            ::onDecision,
            { pkg -> container.notifier.quotaSoon(pkg, container.installedApps.label(pkg)) },
        )
        // Système uniquement : pas besoin de RECEIVER_NOT_EXPORTED.
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onServiceConnected() {
        launcher = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        container.scope.launch { container.historyBackfill.run(ZonedDateTime.now()) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        when {
            // Notre écran de blocage : l'appli bloquée reste derrière, on continue de la surveiller.
            pkg == packageName && cls == BlockActivity::class.java.name -> Unit
            // Clavier, volet de notifications, dialogues : pas une vraie Activity.
            !isActivity(pkg, cls) -> Unit
            pkg == launcher || pkg == packageName -> watcher.stop()
            else -> watcher.watch(pkg)
        }
    }

    private fun isActivity(pkg: String, cls: String) =
        runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess

    private fun onDecision(pkg: String, decision: Decision) {
        when (decision) {
            is Decision.Friction -> {
                val task = container.ruleRepository.rules.value?.at(ZonedDateTime.now())?.get(pkg)?.task ?: TaskKind.NONE
                startActivity(BlockActivity.friction(this, pkg, decision.delaySec, decision.steps, task))
            }
            is Decision.Block -> startActivity(BlockActivity.blocked(this, pkg, decision.reason))
            Decision.Allow -> Unit
        }
        // Le widget suit l'appli qu'il affiche, au plus une fois par minute.
        val now = System.currentTimeMillis()
        if (pkg == container.prefs.getString(TimeLeftWidget.PREF_PACKAGE, null) && now - lastWidgetRefresh >= 60_000) {
            lastWidgetRefresh = now
            scope.launch { TimeLeftWidget.refresh(this@BlockerAccessibilityService) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        scope.cancel()
        super.onDestroy()
    }
}
