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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.antiscrolling.AppContainer
import com.antiscrolling.domain.DEFAULT_PROFILE_ID
import com.antiscrolling.domain.Profile
import com.antiscrolling.ui.rules.ScheduleEditor
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonButton
import com.antiscrolling.ui.NeonSwitch
import com.antiscrolling.ui.ScreenHeader
import com.antiscrolling.ui.Tag
import com.antiscrolling.ui.neonFrame
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Modes de vie", "Profils")
        Text(
            "Chaque profil a ses propres réglages d'applis, choisis dans l'onglet Applis. Un profil activé s'applique pendant ses " +
                "horaires, ou tout le temps s'il n'en a pas ; le reste du temps, c'est le profil « Par défaut ». " +
                "Si deux profils s'appliquent en même temps, le plus haut l'emporte.",
            color = Neon.Soft,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        ProfileCard(set.name(DEFAULT_PROFILE_ID), "Quand aucun autre profil ne s'applique", DEFAULT_PROFILE_ID == active, enabled = true, custom = false)
        set.profiles.forEach { profile ->
            ProfileCard(
                profile.name,
                schedulesSummary(profile),
                profile.id == active,
                profile.enabled,
                custom = true,
                onOpen = { opened = profile.id },
                onToggle = { on -> scope.launch { container.ruleRepository.setProfileEnabled(profile.id, on) } },
            )
        }
        NeonButton("+ Nouveau profil", { creating = true }, Modifier.fillMaxWidth().padding(top = 4.dp), color = Neon.Yellow, filled = true)
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

/** Actif en ce moment : magenta, étiquette ACTIF. Activé : bleu. Désactivé : pointillés gris. Par défaut : gris. */
@Composable
private fun ProfileCard(
    name: String,
    detail: String,
    active: Boolean,
    enabled: Boolean,
    custom: Boolean,
    onOpen: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    val color = when {
        active -> Neon.Magenta
        custom && enabled -> Neon.Cyan
        else -> Neon.Muted
    }
    val frame = when {
        active -> Modifier.neonFrame(Neon.Magenta, Neon.PanelMagenta, glow = true)
        custom && enabled -> Modifier.neonFrame(Neon.Cyan.copy(alpha = 0.85f))
        custom -> Modifier.neonFrame(Neon.Off, Color.Transparent, dashed = true)
        else -> Modifier.neonFrame(Neon.Off)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(frame)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = if (custom && !enabled) Neon.Soft else Neon.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (active) {
                    Text(
                        "ACTIF",
                        color = Neon.Bg,
                        fontFamily = Neon.Mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.background(Neon.Yellow).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Text(detail, color = if (custom) color else Neon.Muted, fontFamily = if (custom) Neon.Mono else null, fontSize = 12.sp)
        }
        onToggle?.let { NeonSwitch(enabled, color.takeIf { active } ?: Neon.Cyan, "Activer $name", it) }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Neon.Cyan) }
            ScreenHeader("Profil", profile.name)
        }
        Tag("Horaires où ce profil est actif")
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
        NeonButton("Supprimer le profil", { deleting = true }, Modifier.fillMaxWidth(), color = Neon.Magenta)
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


private fun schedulesSummary(profile: Profile): String = when {
    !profile.enabled -> "Désactivé"
    profile.schedules.isEmpty() -> "Aucun horaire : actif tout le temps"
    else -> profile.schedules.joinToString("\n") { scheduleLabel(it) }
}
