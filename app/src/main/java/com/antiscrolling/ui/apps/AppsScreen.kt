package com.antiscrolling.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.antiscrolling.AppContainer
import com.antiscrolling.data.InstalledApp
import com.antiscrolling.domain.DEFAULT_PROFILE_ID
import com.antiscrolling.ui.AppIcon
import com.antiscrolling.ui.rules.RuleScreen
import com.antiscrolling.ui.rules.SchedulesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@Composable
fun AppsScreen(container: AppContainer) {
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { container.installedApps.load() }
    }
    // Temps d'utilisation sur 7 jours (historique + aujourd'hui), pour trier les plus utilisées en haut.
    val usage by produceState<Map<String, Long>?>(null) {
        value = withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now()
            val past = container.usageRepository.history(now.toLocalDate().minusDays(7)).usage.values
            (past + container.usageRepository.today(now).foregroundMs)
                .flatMap { it.entries }.groupingBy { it.key }.fold(0L) { sum, e -> sum + e.value }
        }
    }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    val rules by container.ruleRepository.rules.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // package dont on ouvre les réglages
    var schedulesFor by rememberSaveable { mutableStateOf<String?>(null) } // package dont on édite les plages
    var selected by rememberSaveable { mutableStateOf<Long?>(null) } // profil modifié ; null : le profil actif

    val list = apps
    val times = usage
    val set = rules
    if (list == null || times == null || set == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    fun label(pkg: String) = list.firstOrNull { it.packageName == pkg }?.label ?: pkg
    val profileIds = listOf(DEFAULT_PROFILE_ID) + set.profiles.map { it.id }
    // Un profil supprimé entre-temps : retour au profil actif.
    val profileId = selected?.takeIf { it in profileIds } ?: set.activeProfileId(ZonedDateTime.now())
    schedulesFor?.let { pkg ->
        SchedulesScreen(container, profileId, pkg, label(pkg), onBack = { schedulesFor = null })
        return
    }
    editing?.let { pkg ->
        RuleScreen(container, profileId, pkg, label(pkg), onSchedules = { schedulesFor = pkg }, onBack = { editing = null })
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Applis à freiner",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
            )
            if (set.profiles.isNotEmpty()) {
                Text(
                    "Réglages du profil :",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profileIds.forEach { id ->
                        FilterChip(selected = id == profileId, onClick = { selected = id }, label = { Text(set.name(id)) })
                    }
                }
            }
            TextButton(onClick = { alphabetical = !alphabetical }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (alphabetical) "Trier par temps d'utilisation" else "Trier par ordre alphabétique")
            }
        }
        // list est déjà par ordre alphabétique : le tri par temps garde cet ordre à temps égal.
        val sorted = if (alphabetical) list else list.sortedByDescending { times[it.packageName] ?: 0L }
        items(sorted, key = { it.packageName }) { app ->
            val rule = set.of(profileId)[app.packageName]
            val checked = rule?.enabled == true
            ListItem(
                leadingContent = { AppIcon(app.packageName) },
                headlineContent = { Text(app.label) },
                supportingContent = if (checked) {
                    { TextButton(onClick = { editing = app.packageName }) { Text("Options pour l'application") } }
                } else null,
                trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) {
                    scope.launch { container.ruleRepository.setEnabled(profileId, app.packageName, it) }
                },
            )
        }
    }
}
