package com.antiscrolling.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.antiscrolling.domain.DEFAULT_PROFILE_ID
import com.antiscrolling.domain.Profile
import com.antiscrolling.ui.rules.ScheduleEditor
import com.antiscrolling.ui.rules.scheduleLabel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Liste des profils, création, et horaires de chaque profil. Les réglages des applis d'un profil se font dans Applis. */
@Composable
fun ProfilesScreen(container: AppContainer) {
    val rules by container.ruleRepository.rules.collectAsState()
    val scope = rememberCoroutineScope()
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val set = rules
    if (set == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    set.profiles.firstOrNull { it.id == opened }?.let { profile ->
        ProfileScreen(container, profile, onBack = { opened = null })
        return
    }
    val active = set.activeProfileId(ZonedDateTime.now())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Text("Profils", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Text(
            "Chaque profil a ses propres réglages d'applis, choisis dans l'onglet Applis. Un profil activé s'applique pendant ses " +
                "horaires, ou tout le temps s'il n'en a pas ; le reste du temps, c'est le profil « Par défaut ». " +
                "Si deux profils s'appliquent en même temps, le plus haut l'emporte.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ListItem(
            headlineContent = { Text(set.name(DEFAULT_PROFILE_ID)) },
            supportingContent = { Text(status(DEFAULT_PROFILE_ID == active, "Quand aucun autre profil ne s'applique")) },
        )
        set.profiles.forEach { profile ->
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(profile.name) },
                supportingContent = { Text(status(profile.id == active, schedulesSummary(profile))) },
                trailingContent = {
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = { on -> scope.launch { container.ruleRepository.setProfileEnabled(profile.id, on) } },
                        modifier = Modifier.semantics { contentDescription = "Activer ${profile.name}" },
                    )
                },
                modifier = Modifier.clickable { opened = profile.id },
            )
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Nouveau profil") }
    }

    if (creating) {
        NameDialog(
            onConfirm = { name ->
                creating = false
                scope.launch { opened = container.ruleRepository.addProfile(name) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ProfileScreen(container: AppContainer, profile: Profile, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var deleting by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Text(profile.name, style = MaterialTheme.typography.headlineSmall)
        }
        Text("Horaires où ce profil est actif", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ses réglages d'applis se changent dans l'onglet Applis, en choisissant ce profil en haut de la liste.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ScheduleEditor(
            profile.schedules,
            onDelete = { scope.launch { container.ruleRepository.deleteProfileSchedule(it.id) } },
            onAdd = { scope.launch { container.ruleRepository.addProfileSchedule(profile.id, it) } },
        )
        HorizontalDivider()
        OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer le profil") }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            text = { Text("Supprimer « ${profile.name} » et ses réglages d'applis ?") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onBack()
                    scope.launch { container.ruleRepository.deleteProfile(profile.id) }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun NameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom (Travail, Soirée…)") }, singleLine = true)
                Text("Il reprend les réglages du profil « Par défaut ». Activez-le ensuite dans la liste.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Créer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun status(active: Boolean, detail: String) = if (active) "Actif en ce moment · $detail" else detail

private fun schedulesSummary(profile: Profile): String = when {
    !profile.enabled -> "Désactivé"
    profile.schedules.isEmpty() -> "Aucun horaire : actif tout le temps"
    else -> profile.schedules.joinToString("\n") { scheduleLabel(it) }
}
