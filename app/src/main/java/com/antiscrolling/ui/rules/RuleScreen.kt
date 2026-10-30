package com.antiscrolling.ui.rules

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.AppContainer
import com.antiscrolling.BuildConfig
import com.antiscrolling.domain.Rule
import com.antiscrolling.domain.Schedule
import com.antiscrolling.domain.TaskKind
import com.antiscrolling.service.NotificationBlockerService
import com.antiscrolling.ui.ChoiceGrid
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonIcon
import com.antiscrolling.ui.NeonSwitch
import com.antiscrolling.ui.Tag
import com.antiscrolling.ui.durationLabel
import com.antiscrolling.ui.glow
import com.antiscrolling.ui.hhmm
import com.antiscrolling.ui.nearLimit
import com.antiscrolling.ui.neonFrame
import com.antiscrolling.widget.TimeLeftWidget
import kotlinx.coroutines.launch

// En debug, des limites courtes pour la recette : alerte à 2 min d'usage, blocage à 7.
private val QUOTA_CHOICES = listOf(null) + (if (BuildConfig.DEBUG) listOf(2, 7) else emptyList()) +
    listOf(15, 30, 45, 60, 90, 120)
private val DELAY_CHOICES = listOf(0, 5, 15, 30, 60)
private val INCREMENT_CHOICES = listOf(0, 5, 10, 20, 30)

private enum class Editing { QUOTA, TASK }

/** Réglages d'une appli suivie : blocages durs, frictions, notifications, widget. */
@Composable
fun RuleScreen(
    container: AppContainer,
    profileId: Long,
    packageName: String,
    label: String,
    usedTodayMs: Long,
    onSchedules: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rules by container.ruleRepository.rules.collectAsState()
    val rule = rules?.of(profileId)?.get(packageName) ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Refus sans conséquence : la limite bloque quand même, seule l'alerte manque.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }
    var widgetPackage by rememberSaveable { mutableStateOf(container.prefs.getString(TimeLeftWidget.PREF_PACKAGE, null)) }
    fun update(change: (Rule) -> Rule) = scope.launch { container.ruleRepository.update(profileId, packageName, change) }
    val accent = if (nearLimit(rule, usedTodayMs)) Neon.Magenta else Neon.Cyan

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).neonFrame(Neon.Cyan.copy(alpha = 0.5f), Color.Transparent).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Neon.Cyan) }
            NeonIcon(packageName, accent, 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label.uppercase(),
                    color = if (accent == Neon.Cyan) Neon.Title else accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    style = TextStyle(shadow = glow(accent)),
                )
                Text("PROFIL ▸ ${rules?.name(profileId).orEmpty().uppercase()}", color = Neon.Yellow, fontFamily = Neon.Mono, fontSize = 11.sp)
            }
        }

        Tag("Blocage")
        Column(Modifier.fillMaxWidth().neonFrame(Neon.Cyan.copy(alpha = 0.5f))) {
            Setting("Limite par jour", rule.dailyQuotaMin?.let { durationLabel(it) } ?: "aucune") { editing = Editing.QUOTA }
            HorizontalDivider(color = Neon.Cyan.copy(alpha = 0.2f))
            Setting("Plages horaires", schedulesValue(rule.schedules), onClick = onSchedules)
        }

        Tag("À chaque ouverture")
        Column(Modifier.fillMaxWidth().neonFrame(Neon.Cyan.copy(alpha = 0.5f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pause", color = Neon.Text, fontSize = 15.sp)
            ChoiceGrid(DELAY_CHOICES, rule.delaySec, DELAY_CHOICES.size, { if (it == 0) "0" else "$it s" }) { sec ->
                update { it.copy(delaySec = sec) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Pause qui s'allonge", color = Neon.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("par ouverture du jour", color = Neon.Muted, fontSize = 12.sp)
            }
            ChoiceGrid(INCREMENT_CHOICES, rule.delayIncrementSec, INCREMENT_CHOICES.size, { if (it == 0) "non" else "+$it" }) { sec ->
                update { it.copy(delayIncrementSec = sec) }
            }
            HorizontalDivider(color = Neon.Cyan.copy(alpha = 0.2f))
            Toggle("Demander pourquoi", rule.askIntent) { on -> update { it.copy(askIntent = on) } }
            Setting("Petite tâche", taskLabel(rule.task), padded = false) { editing = Editing.TASK }
            Toggle("Choisir la durée d'accès", rule.askDuration) { on -> update { it.copy(askDuration = on) } }
        }

        Tag("Autres")
        Column(Modifier.fillMaxWidth().neonFrame(Neon.Cyan.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 4.dp)) {
            Toggle(
                "Masquer les notifications pendant un blocage",
                rule.hideNotifications,
                detail = if (rule.hideNotifications && !NotificationBlockerService.isEnabled(context)) {
                    "Accès aux notifications à autoriser dans les paramètres."
                } else {
                    null
                },
            ) { on ->
                update { it.copy(hideNotifications = on) }
                if (on && !NotificationBlockerService.isEnabled(context)) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            HorizontalDivider(color = Neon.Cyan.copy(alpha = 0.2f))
            Toggle("Afficher dans le widget", widgetPackage == packageName) { on ->
                widgetPackage = if (on) packageName else null
                container.prefs.edit().putString(TimeLeftWidget.PREF_PACKAGE, widgetPackage).apply()
                scope.launch { TimeLeftWidget.refresh(context) }
            }
        }
    }

    when (editing) {
        Editing.QUOTA -> ChoiceDialog("Limite par jour", QUOTA_CHOICES, rule.dailyQuotaMin, ::quotaLabel, { editing = null }) { minutes ->
            update { it.copy(dailyQuotaMin = minutes) }
            if (minutes != null && Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Editing.TASK -> ChoiceDialog("Petite tâche", TaskKind.entries, rule.task, ::taskLabel, { editing = null }) { kind ->
            update { it.copy(task = kind) }
        }
        null -> Unit
    }
}

@Composable
private fun Setting(title: String, value: String, padded: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = if (padded) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Neon.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$value ▸", color = Neon.Cyan, fontFamily = Neon.Mono, fontSize = 13.sp)
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Neon.Text, fontSize = 15.sp)
            detail?.let { Text(it, color = Neon.Magenta, fontSize = 12.sp) }
        }
        NeonSwitch(checked)
    }
}

/** « 22:00 → 07:00 » pour une seule plage, sinon leur nombre. */
private fun schedulesValue(schedules: List<Schedule>): String {
    val only = schedules.singleOrNull() ?: return schedulesCount(schedules.size)
    return if (only.startMin == only.endMin) "toute la journée" else "${hhmm(only.startMin)} → ${hhmm(only.endMin)}"
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    current: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                choices.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == current, role = Role.RadioButton) {
                                onPick(choice)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == current, onClick = null)
                        Text(label(choice), Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

fun quotaLabel(minutes: Int?): String = if (minutes == null) "aucune" else "${durationLabel(minutes)} par jour"

/** « Pause 15 s croissante · question · limite 30 min · 1 plage », ou « Aucune option » si rien n'est actif. */
fun ruleSummary(rule: Rule): String = buildList {
    if (rule.delaySec > 0) add("pause ${rule.delaySec} s" + if (rule.delayIncrementSec > 0) " croissante" else "")
    if (rule.askIntent) add("question")
    if (rule.task != TaskKind.NONE) add("tâche")
    if (rule.askDuration) add("durée au choix")
    rule.dailyQuotaMin?.let { add("limite ${durationLabel(it)}") }
    if (rule.schedules.isNotEmpty()) add(schedulesCount(rule.schedules.size))
    if (rule.hideNotifications) add("notifications masquées")
}.joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { "Aucune option" }

fun schedulesCount(count: Int): String = when (count) {
    0 -> "aucune"
    1 -> "1 plage"
    else -> "$count plages"
}

private fun taskLabel(kind: TaskKind): String = when (kind) {
    TaskKind.NONE -> "aucune"
    TaskKind.COPY -> "recopier une phrase"
    TaskKind.MATH -> "faire une addition"
}
