package com.antiscrolling.ui.focus

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.AppContainer
import com.antiscrolling.domain.FocusSession
import com.antiscrolling.ui.Caption
import com.antiscrolling.ui.LockOpen
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonButton
import com.antiscrolling.ui.NeonRing
import com.antiscrolling.ui.ScreenHeader
import com.antiscrolling.ui.durationLabel
import com.antiscrolling.ui.glow
import com.antiscrolling.ui.neonGlow
import com.antiscrolling.ui.hhmm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

private val FOCUS_CHOICES = listOf(5, 15, 30, 45, 60, 120)

/** [active] : le focus en cours, suivi par l'écran principal qui passe tout au magenta pendant ce temps. */
@Composable
fun FocusScreen(container: AppContainer, active: FocusSession?) {
    val rules by container.ruleRepository.rules.collectAsState()
    val scope = rememberCoroutineScope()
    val tracked = rules?.at(ZonedDateTime.now())?.values?.count { it.enabled } ?: 0 // le focus bloque les applis du profil actif

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader("Verrouillage total", "Mode focus")
        if (active != null) {
            // Compte à rebours à la seconde, seulement pendant le focus.
            @SuppressLint("ProduceStateDoesNotAssignValue") // assignée dans la boucle, faux positif de lint
            val now by produceState(Instant.now(), active) {
                while (true) {
                    value = Instant.now()
                    delay(1000)
                }
            }
            val left = Duration.between(now, active.endAt).coerceAtLeast(Duration.ZERO)
            val total = Duration.between(active.startAt, active.endAt).toMillis().coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                NeonRing(Neon.Magenta, left.toMillis().toFloat() / total, 220.dp) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Neon.Magenta, modifier = Modifier.size(36.dp))
                    Text(
                        countdown(left),
                        color = Color.White,
                        fontFamily = Neon.Mono,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = glow(Neon.Magenta)),
                    )
                    Caption("restantes", Neon.Pink)
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("En cours jusqu'à ${hhmm(active.endAt)}", color = Neon.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Les applis suivies ($tracked) sont bloquées.", color = Neon.Soft, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            NeonButton(
                "Arrêter le focus",
                { scope.launch { container.ruleRepository.stopFocus(active, Instant.now()) } },
                Modifier.fillMaxWidth(),
                color = Neon.Magenta,
            )
        } else {
            Text(
                if (tracked == 0) {
                    "Cochez d'abord des applis dans l'onglet Applis : le focus les bloque toutes."
                } else {
                    "Bloque les applis suivies ($tracked) pendant la durée choisie, sans pause ni accès possible."
                },
                color = Neon.Soft,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                // Au repos : halo discret et cadenas ouvert.
                NeonRing(Neon.Cyan, 1f, 180.dp, dashed = true, glowRadius = 24.dp, glowAlpha = 0.2f) {
                    Icon(LockOpen, contentDescription = null, tint = Neon.Cyan, modifier = Modifier.size(64.dp))
                }
            }
            Caption("Lancer pour")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FOCUS_CHOICES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            LaunchCell(durationLabel(minutes), enabled = tracked > 0, Modifier.weight(1f)) {
                                val now = Instant.now()
                                scope.launch { container.ruleRepository.startFocus(now, now.plus(Duration.ofMinutes(minutes.toLong()))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bouton de lancement de la maquette : cadre bleu, fond teinté, halo léger, durée en gras. */
@Composable
private fun LaunchCell(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (enabled) Neon.Cyan else Neon.Cyan.copy(alpha = 0.4f)
    Box(
        modifier
            .heightIn(min = 56.dp)
            .then(if (enabled) Modifier.neonGlow(Neon.Cyan, 12.dp, 0.3f) else Modifier)
            .background(Neon.Cyan.copy(alpha = if (enabled) 0.1f else 0.04f))
            .border(1.dp, color)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontFamily = Neon.Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** « 42:17 », ou « 1:05:00 » au-delà d'une heure. */
private fun countdown(left: Duration): String {
    val s = left.seconds
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}
