package com.antiscrolling.ui.rules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antiscrolling.AppContainer
import com.antiscrolling.domain.Schedule
import com.antiscrolling.ui.hhmm
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Plages de blocage d'une appli dans un profil : liste, suppression, ajout. */
@Composable
fun SchedulesScreen(container: AppContainer, profileId: Long, packageName: String, label: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val rules by container.ruleRepository.rules.collectAsState()
    val schedules = rules?.of(profileId)?.get(packageName)?.schedules.orEmpty()
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Text("Plages de blocage", style = MaterialTheme.typography.headlineSmall)
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            "Pendant une plage, l'appli est bloquée, sans pause ni accès possible.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ScheduleEditor(
            schedules,
            onDelete = { scope.launch { container.ruleRepository.deleteSchedule(it.id) } },
            onAdd = { scope.launch { container.ruleRepository.addSchedule(profileId, packageName, it) } },
        )
    }
}

/** Liste de plages avec suppression, puis formulaire d'ajout : jours, début, fin. À placer dans une Column. */
@Composable
fun ScheduleEditor(schedules: List<Schedule>, onDelete: (Schedule) -> Unit, onAdd: (Schedule) -> Unit) {
    var days by rememberSaveable { mutableIntStateOf(Schedule.EVERY_DAY) }
    var start by rememberSaveable { mutableIntStateOf(22 * 60) }
    var end by rememberSaveable { mutableIntStateOf(7 * 60) }
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) } // true : début, false : fin

    if (schedules.isEmpty()) Text("Aucune plage.", style = MaterialTheme.typography.bodyMedium)
    schedules.forEach { schedule ->
        ListItem(
            headlineContent = { Text(scheduleLabel(schedule)) },
            trailingContent = {
                IconButton(onClick = { onDelete(schedule) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer la plage")
                }
            },
        )
    }

    HorizontalDivider()
    Text("Nouvelle plage", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            val bit = Schedule.dayBit(day)
            val name = day.getDisplayName(TextStyle.FULL, Locale.FRENCH)
            FilterChip(
                selected = days and bit != 0,
                onClick = { days = days xor bit },
                label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.FRENCH)) },
                modifier = Modifier.weight(1f).semantics { contentDescription = name },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) { Text("De ${hhmm(start)}") }
        OutlinedButton(onClick = { picking = false }, modifier = Modifier.weight(1f)) { Text("à ${hhmm(end)}") }
    }
    Button(
        onClick = { onAdd(Schedule(0, days, start, end)) },
        enabled = days != 0,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Ajouter") }

    picking?.let { isStart ->
        TimeDialog(
            initialMin = if (isStart) start else end,
            onPick = { minutes ->
                if (isStart) start = minutes else end = minutes
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMin: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialMin / 60, initialMin % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

fun scheduleLabel(schedule: Schedule): String {
    val days = if (schedule.daysMask == Schedule.EVERY_DAY) {
        "Tous les jours"
    } else {
        DayOfWeek.entries.filter { schedule.daysMask and Schedule.dayBit(it) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.FRENCH) }
            .replaceFirstChar { it.uppercase() }
    }
    val hours = when {
        schedule.startMin == schedule.endMin -> "toute la journée"
        schedule.endMin < schedule.startMin -> "${hhmm(schedule.startMin)} → ${hhmm(schedule.endMin)} le lendemain"
        else -> "${hhmm(schedule.startMin)} → ${hhmm(schedule.endMin)}"
    }
    return "$days, $hours"
}
