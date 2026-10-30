package com.antiscrolling.data

import android.content.Context
import android.content.Intent

data class InstalledApp(val packageName: String, val label: String)

class InstalledApps(private val context: Context) {
    private val pm = context.packageManager
    private var cached: List<InstalledApp>? = null
    private var sequence = 0

    /**
     * Relue seulement si une appli a été installée, mise à jour ou supprimée depuis la dernière lecture.
     * shortcut: les noms ne suivent pas un changement de langue du téléphone ; vider le cache sur ACTION_LOCALE_CHANGED si ça gêne.
     */
    @Synchronized
    fun load(): List<InstalledApp> {
        val changes = pm.getChangedPackages(sequence)
        cached?.let { if (changes == null) return it }
        changes?.let { sequence = it.sequenceNumber }
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .also { cached = it }
    }

    fun label(packageName: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
}
