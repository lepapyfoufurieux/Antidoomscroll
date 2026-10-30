package com.antiscrolling.ui.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.antiscrolling.AppContainer
import com.antiscrolling.domain.DayUsage
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.StatsCalculator
import com.antiscrolling.ui.AppIcon
import com.antiscrolling.ui.count
import com.antiscrolling.ui.rules.ruleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@Composable
fun HomeScreen(container: AppContainer) {
    val labels by produceState<Map<String, String>?>(null) {
        value = withContext(Dispatchers.IO) { container.installedApps.load().associate { it.packageName to it.label } }
    }
    // Recalculé au retour dans l'appli puis chaque minute, en pause quand elle n'est pas affichée.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    @SuppressLint("ProduceStateDoesNotAssignValue") // assignée dans repeatOnLifecycle, que lint ne suit pas
    val today by produceState<DayUsage?>(null, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = withContext(Dispatchers.IO) { container.usageRepository.today(ZonedDateTime.now()) }
                delay(60_000)
            }
        }
    }
    // Aussi ici : si l'accès à l'utilisation arrive après le démarrage du service, ne pas attendre le prochain.
    LaunchedEffect(Unit) { container.historyBackfill.run(ZonedDateTime.now()) }

    val rules by container.ruleRepository.rules.collectAsState()
    val recentOpens by container.eventRepository.recentOpens.collectAsState()

    val names = labels
    val usage = today
    val allRules = rules
    if (names == null || usage == null || allRules == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Seules les applis lançables : ni le lanceur, ni les services système, ni Antiscrolling.
    val apps = usage.foregroundMs.filterKeys { it in names }
    val now = ZonedDateTime.now()
    val profileId = allRules.activeProfileId(now)
    val tracked = allRules.of(profileId).values.filter { it.enabled && it.packageName in names }.associateBy { it.packageName }
    // Les applis freinées restent affichées même sans usage, pour voir leurs options.
    val shown = (apps.filterValues { it >= 60_000 }.keys + tracked.keys)
        .map { it to (apps[it] ?: 0L) }.sortedByDescending { it.second }
    val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
    val opensToday = recentOpens.orEmpty().filter { !it.at.isBefore(startOfDay) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Aujourd'hui",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
            )
            Text(
                "Profil : ${allRules.name(profileId)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
            Text(
                formatDuration(apps.values.sum()),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(16.dp),
            )
            if (shown.isEmpty()) {
                Text(
                    "Aucune appli utilisée plus d'une minute aujourd'hui.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(shown, key = { it.first }) { (pkg, ms) ->
            val rule = tracked[pkg]
            ListItem(
                leadingContent = { AppIcon(pkg) },
                headlineContent = { Text(names.getValue(pkg)) },
                supportingContent = rule?.let { { Text(details(ruleSummary(it), opensToday.filter { o -> o.packageName == pkg })) } },
                trailingContent = { Text(formatDuration(ms), style = MaterialTheme.typography.bodyLarge) },
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

/** Options actives, puis ouvertures, renoncements et raisons du jour (comptés par l'écran de frein). */
private fun details(summary: String, opens: List<OpenEvent>): String {
    val stats = StatsCalculator.opens(opens)
    val granted = stats.granted.values.sum()
    val gaveUp = stats.gaveUp.values.sum()
    val reasons = stats.intents.entries.sortedByDescending { it.value }.joinToString(", ") { (r, n) -> if (n > 1) "$r ×$n" else r }
    return buildList {
        add(summary)
        add("${count(granted, "ouverture")}, ${count(gaveUp, "renoncement")}")
        if (reasons.isNotEmpty()) add("Raisons : $reasons")
    }.joinToString("\n")
}
