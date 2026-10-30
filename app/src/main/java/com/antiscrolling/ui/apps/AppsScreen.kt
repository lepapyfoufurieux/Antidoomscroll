package com.antiscrolling.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.AppContainer
import com.antiscrolling.data.InstalledApp
import com.antiscrolling.domain.DEFAULT_PROFILE_ID
import com.antiscrolling.ui.Caption
import com.antiscrolling.ui.ChoiceCell
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonButton
import com.antiscrolling.ui.NeonIcon
import com.antiscrolling.ui.ScreenHeader
import com.antiscrolling.ui.nearLimit
import com.antiscrolling.ui.neonFrame
import com.antiscrolling.ui.rules.RuleScreen
import com.antiscrolling.ui.rules.SchedulesScreen
import com.antiscrolling.ui.shortDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/** Temps d'utilisation sur 7 jours (historique + aujourd'hui) pour le tri, et celui du jour pour l'alerte de limite. */
private class Usage(val week: Map<String, Long>, val today: Map<String, Long>)

@Composable
fun AppsScreen(container: AppContainer) {
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { container.installedApps.load() }
    }
    val usage by produceState<Usage?>(null) {
        value = withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now()
            val past = container.usageRepository.history(now.toLocalDate().minusDays(7)).usage.values
            val today = container.usageRepository.today(now).foregroundMs
            Usage((past + today).flatMap { it.entries }.groupingBy { it.key }.fold(0L) { sum, e -> sum + e.value }, today)
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
        RuleScreen(container, profileId, pkg, label(pkg), times.today[pkg] ?: 0L, onSchedules = { schedulesFor = pkg }, onBack = { editing = null })
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                ScreenHeader("Applis à freiner", "Applis")
                if (set.profiles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Caption("Réglages du profil")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            profileIds.forEach { id ->
                                ChoiceCell(set.name(id), id == profileId, color = Neon.Magenta, mono = false) { selected = id }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (alphabetical) "TRI : A → Z" else "TRI : TEMPS / 7 JOURS",
                        color = Neon.Muted,
                        fontFamily = Neon.Mono,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    NeonButton(if (alphabetical) "Temps" else "A → Z", { alphabetical = !alphabetical }, color = Neon.Yellow)
                }
            }
        }
        // list est déjà par ordre alphabétique : le tri par temps garde cet ordre à temps égal.
        val sorted = if (alphabetical) list else list.sortedByDescending { times.week[it.packageName] ?: 0L }
        items(sorted, key = { it.packageName }) { app ->
            val rule = set.of(profileId)[app.packageName]?.takeIf { it.enabled }
            val checked = rule != null
            val alert = nearLimit(rule, times.today[app.packageName] ?: 0L)
            val color = when {
                alert -> Neon.Magenta
                checked -> Neon.Cyan
                else -> Neon.Soft
            }
            Column(
                Modifier.fillMaxWidth().neonFrame(
                    if (checked) color.copy(alpha = 0.95f) else Neon.Off,
                    if (alert) Neon.PanelMagenta else if (checked) Neon.Panel else Color.Transparent,
                    glow = checked,
                ),
            ) {
                Row(
                    Modifier
                        .toggleable(value = checked, role = Role.Checkbox) {
                            scope.launch { container.ruleRepository.setEnabled(profileId, app.packageName, it) }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = color, checkmarkColor = Neon.Bg, uncheckedColor = Neon.Muted),
                    )
                    NeonIcon(app.packageName, if (checked) color else Neon.Muted, 36.dp)
                    Text(
                        app.label,
                        color = if (checked) color.takeIf { alert } ?: Neon.Text else Neon.Soft,
                        fontSize = 16.sp,
                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(shortDuration(times.week[app.packageName] ?: 0L), color = if (checked) color else Neon.Muted, fontFamily = Neon.Mono, fontSize = 14.sp)
                }
                if (checked) {
                    Text(
                        "Options pour l'application ▸",
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(role = Role.Button) { editing = app.packageName }
                            .heightIn(min = 44.dp)
                            .padding(start = 60.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
