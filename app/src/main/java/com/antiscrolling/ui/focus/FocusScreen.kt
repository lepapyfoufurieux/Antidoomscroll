package com.antiscrolling.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antiscrolling.AppContainer
import com.antiscrolling.BuildConfig
import com.antiscrolling.ui.durationLabel
import com.antiscrolling.ui.hhmm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

// En debug, un focus de 2 min pour la recette.
private val FOCUS_CHOICES = (if (BuildConfig.DEBUG) listOf(2) else emptyList()) + listOf(25, 60, 120)

@Composable
fun FocusScreen(container: AppContainer) {
    val sessions by container.ruleRepository.latestFocus.collectAsState()
    val rules by container.ruleRepository.rules.collectAsState()
    val scope = rememberCoroutineScope()
    // Recalcule « en cours » quand le focus se termine pendant que l'écran est affiché.
    val tick by produceState(0) {
        while (true) {
            delay(15_000)
            value = value + 1
        }
    }
    val active = remember(sessions, tick) { sessions?.firstOrNull()?.takeIf { it.isActive(ZonedDateTime.now()) } }
    val tracked = rules?.at(ZonedDateTime.now())?.values?.count { it.enabled } ?: 0 // le focus bloque les applis du profil actif

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Mode focus", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        if (active != null) {
            Text("En cours jusqu'à ${hhmm(active.endAt)}", style = MaterialTheme.typography.headlineSmall)
            Text("Les applis suivies ($tracked) sont bloquées.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = { scope.launch { container.ruleRepository.stopFocus(active, Instant.now()) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Arrêter le focus") }
        } else {
            Text(
                if (tracked == 0) {
                    "Cochez d'abord des applis dans l'onglet Applis : le focus les bloque toutes."
                } else {
                    "Bloque les applis suivies ($tracked) pendant la durée choisie, sans pause ni accès possible."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            FOCUS_CHOICES.forEach { minutes ->
                Button(
                    onClick = {
                        val now = Instant.now()
                        scope.launch { container.ruleRepository.startFocus(now, now.plus(Duration.ofMinutes(minutes.toLong()))) }
                    },
                    enabled = tracked > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Lancer ${durationLabel(minutes)}") }
            }
        }
    }
}
