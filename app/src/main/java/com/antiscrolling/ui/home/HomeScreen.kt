package com.antiscrolling.ui.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.antiscrolling.AppContainer
import com.antiscrolling.domain.DayUsage
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.Rule
import com.antiscrolling.domain.StatsCalculator
import com.antiscrolling.ui.Caption
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonIcon
import com.antiscrolling.ui.ScreenHeader
import com.antiscrolling.ui.UsageBar
import com.antiscrolling.ui.count
import com.antiscrolling.ui.glow
import com.antiscrolling.ui.nearLimit
import com.antiscrolling.ui.neonFrame
import com.antiscrolling.ui.rules.ruleSummary
import com.antiscrolling.ui.shortDuration
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
    val total = apps.values.sum()
    val now = ZonedDateTime.now()
    val profileId = allRules.activeProfileId(now)
    val tracked = allRules.of(profileId).values.filter { it.enabled && it.packageName in names }.associateBy { it.packageName }
    // Les applis freinées restent affichées même sans usage, pour voir leurs options.
    val shown = (apps.filterValues { it >= 60_000 }.keys + tracked.keys)
        .map { it to (apps[it] ?: 0L) }.sortedByDescending { it.second }
    val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
    val opensToday = recentOpens.orEmpty().filter { !it.at.isBefore(startOfDay) }
    val dayOpens = StatsCalculator.opens(opensToday)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                ScreenHeader("Antidoomscroll", "Aujourd'hui")
                Text(
                    "PROFIL ▸ ${allRules.name(profileId).uppercase()}",
                    color = Neon.Magenta,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.neonFrame(Neon.Magenta, Neon.PanelMagenta, glow = true).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().neonFrame(Neon.Cyan.copy(alpha = 0.6f), glow = true).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Caption("Temps sur les applis")
                val minutes = total / 60_000
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (minutes < 60) "$minutes" else "${minutes / 60}:${"%02d".format(minutes % 60)}",
                        color = Color.White,
                        fontFamily = Neon.Mono,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = glow(Neon.Cyan)),
                    )
                    Text(if (minutes < 60) "min aujourd'hui" else "h aujourd'hui", color = Neon.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Neon.Yellow)) { append("▲ ${count(dayOpens.granted.values.sum(), "ouverture")}") }
                        withStyle(SpanStyle(color = Neon.Muted)) { append("  ·  ") }
                        withStyle(SpanStyle(color = Neon.Magenta)) { append(count(dayOpens.gaveUp.values.sum(), "renoncement")) }
                    },
                    fontSize = 13.sp,
                )
            }
        }
        if (shown.isEmpty()) {
            item { Text("Aucune appli utilisée plus d'une minute aujourd'hui.", color = Neon.Soft, fontSize = 14.sp) }
        }
        items(shown, key = { it.first }) { (pkg, ms) ->
            AppCard(names.getValue(pkg), pkg, ms, total, tracked[pkg], opensToday.filter { it.packageName == pkg })
        }
    }
}

@Composable
private fun AppCard(label: String, pkg: String, ms: Long, total: Long, rule: Rule?, opens: List<OpenEvent>) {
    val alert = nearLimit(rule, ms)
    val color = when {
        alert -> Neon.Magenta
        rule != null -> Neon.Cyan
        else -> Neon.Muted
    }
    val small = if (alert) Neon.Pink else Neon.Muted
    val percent = if (total == 0L) 0 else Math.round(ms * 100.0 / total).toInt()
    Column(
        Modifier
            .fillMaxWidth()
            .neonFrame(
                if (rule == null) Neon.Off else color.copy(alpha = 0.7f),
                if (alert) Neon.PanelMagenta else Neon.Panel,
                glow = alert,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonIcon(pkg, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = if (alert) Neon.Magenta else Neon.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                // La limite s'affiche à droite du temps : inutile de la répéter ici.
                rule?.let { Text(ruleSummary(it.copy(dailyQuotaMin = null)), color = if (alert) Neon.Pink else Neon.Soft, fontSize = 12.sp) }
            }
            Text(
                buildAnnotatedString {
                    append(shortDuration(ms))
                    rule?.dailyQuotaMin?.let {
                        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = small, shadow = null)) {
                            append(" / ${shortDuration(it * 60_000L)}")
                        }
                    }
                },
                color = color,
                fontFamily = Neon.Mono,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = if (rule != null) glow(color, 24f) else null),
            )
        }
        UsageBar(if (total == 0L) 0f else ms.toFloat() / total, color)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append("$percent % de la journée") }
                if (rule != null) append(" · ${details(opens)}")
            },
            color = small,
            fontSize = 12.sp,
        )
    }
}

/** Ouvertures, renoncements et raisons du jour (comptés par l'écran de frein). */
private fun details(opens: List<OpenEvent>): String {
    val stats = StatsCalculator.opens(opens)
    val reasons = stats.intents.entries.sortedByDescending { it.value }.joinToString(", ") { (r, n) -> if (n > 1) "$r ×$n" else r }
    return "${count(stats.granted.values.sum(), "ouverture")}, ${count(stats.gaveUp.values.sum(), "renoncement")}" +
        if (reasons.isEmpty()) "" else " · Raisons : $reasons"
}
