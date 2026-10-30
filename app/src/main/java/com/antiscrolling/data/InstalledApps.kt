package com.antiscrolling.data

import android.content.Context
import android.content.Intent

data class InstalledApp(val packageName: String, val label: String)

class InstalledApps(private val context: Context) {
    private val pm = context.packageManager

    fun load(): List<InstalledApp> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

    fun label(packageName: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
}
