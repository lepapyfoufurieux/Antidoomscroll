package com.antiscrolling.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antiscrolling.AppContainer
import com.antiscrolling.data.History
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.StatsCalculator
import com.antiscrolling.ui.count
import com.antiscrolling.ui.durationLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

private class StatsData(
    val history: History,
    val unlocksToday: Int,
    val opens: List<OpenEvent>, // 7 derniers jours
    val labels: Map<String, String>,
)

@Composable
fun StatsScreen(container: AppContainer) {
    val rules by container.ruleRepository.rules.collectAsState()
    val data by produceState<StatsData?>(null) {
        val now = ZonedDateTime.now()
        container.historyBackfill.run(now) // recopie d'abord les jours manquants
        value = withContext(Dispatchers.IO) {
            val today = now.toLocalDate()
            StatsData(
                history = container.usageRepository.history(today.minusDays(14)),
                unlocksToday = container.usageRepository.today(now).unlocks,
                opens = container.eventRepository.opensSince(today.minusDays(7).atStartOfDay(now.zone).toInstant()),
                labels = container.installedApps.load().associate { it.packageName to it.label },
            )
        }
    }
    val stats = data
    val allRules = rules
    if (stats == null || allRules == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val today = ZonedDateTime.now().toLocalDate()
    // shortcut: limites et applis du profil actif seulement ; les combiner si plusieurs profils gênent la série.
    val tracked = allRules.at(ZonedDateTime.now()).values.filter { it.enabled }
    val limits = tracked.mapNotNull { rule -> rule.dailyQuotaMin?.let { rule.packageName to it } }.toMap()
    val streak = StatsCalculator.streak(stats.history.usage, limits, today)
    val week = StatsCalculator.weekOverWeek(stats.history.usage, tracked.map { it.packageName }.toSet(), today)
    val opens = StatsCalculator.opens(stats.opens)
    fun label(pkg: String) = stats.labels[pkg] ?: pkg
    fun minutes(ms: Long) = durationLabel((ms / 60_000).toInt())

    LazyColumn(Modifier.fillMaxSize()) {
        item { Title("Statistiques") }
        if (stats.history.usage.isEmpty()) {
            item {
                Line("L'historique commence au premier jour complet après l'installation. Revenez demain.")
            }
        }

        item { Section("Série") }
        item {
            Line(
                when {
                    limits.isEmpty() -> "Posez une limite par jour sur une appli pour suivre une série."
                    streak <= 1 -> "$streak jour sous vos limites"
                    else -> "$streak jours d'affilée sous vos limites"
                },
            )
        }

        item { Section("7 derniers jours, applis freinées") }
        item {
            val now = week.thisWeek.values.sum()
            val before = week.lastWeek.values.sum()
            val change = StatsCalculator.change(now, before)?.takeIf { week.lastWeekComplete }
            Line(
                "${minutes(now)} au total" + when {
                    change == null -> ""
                    change <= 0 -> ", ${-change} % de moins que la semaine d'avant"
                    else -> ", $change % de plus que la semaine d'avant"
                },
            )
        }
        week.thisWeek.entries.sortedByDescending { it.value }.forEach { (pkg, ms) ->
            item { ListItem(headlineContent = { Text(label(pkg)) }, trailingContent = { Text(minutes(ms)) }) }
        }

        item { Section("Déverrouillages") }
        item {
            val average = StatsCalculator.averageUnlocks(stats.history.unlocks, today)
            Line("${stats.unlocksToday} aujourd'hui" + (average?.let { ", $it par jour en moyenne sur 7 jours" } ?: ""))
        }

        item { Section("Ouvertures sur 7 jours") }
        val apps = (opens.granted.keys + opens.gaveUp.keys).sortedByDescending { opens.granted[it] ?: 0 }
        if (apps.isEmpty()) item { Line("Aucune ouverture freinée pour l'instant.") }
        apps.forEach { pkg ->
            item {
                ListItem(
                    headlineContent = { Text(label(pkg)) },
                    supportingContent = {
                        Text("${count(opens.granted[pkg] ?: 0, "ouverture")}, ${count(opens.gaveUp[pkg] ?: 0, "renoncement")}")
                    },
                )
            }
        }
        if (opens.intents.isNotEmpty()) {
            item { Section("Raisons données") }
            opens.intents.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                item { ListItem(headlineContent = { Text(reason) }, trailingContent = { Text("$count") }) }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp))
}

@Composable
private fun Section(text: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
