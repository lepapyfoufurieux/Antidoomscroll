package com.antiscrolling

import android.content.Context
import androidx.room.Room
import com.antiscrolling.data.EventRepository
import com.antiscrolling.data.HistoryBackfill
import com.antiscrolling.data.InstalledApps
import com.antiscrolling.data.RuleRepository
import com.antiscrolling.data.UsageRepository
import com.antiscrolling.data.db.AppDatabase
import com.antiscrolling.notif.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "antiscrolling.db")
        .fallbackToDestructiveMigration()
        .build()

    val ruleRepository = RuleRepository(db.dao(), scope)
    val eventRepository = EventRepository(db.dao(), scope)
    val installedApps = InstalledApps(context)
    val usageRepository = UsageRepository(context, db.historyDao())
    val historyBackfill = HistoryBackfill(usageRepository, db.historyDao())
    val notifier = Notifier(context)
}
