package com.antiscrolling.ui.stats

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.AppContainer
import com.antiscrolling.data.History
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.StatsCalculator
import com.antiscrolling.ui.Caption
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.ScreenHeader
import com.antiscrolling.ui.glow
import com.antiscrolling.ui.neonGlow
import com.antiscrolling.ui.neonFrame
import com.antiscrolling.ui.shortDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private class StatsData(
    val history: History,
    val today: Map<String, Long>, // temps par appli aujourd'hui
    val unlocksToday: Int,
    val opens: List<OpenEvent>, // 7 derniers jours
    val labels: Map<String, String>,
)

/** Une barre du graphique : un jour, son temps sur les applis freinées, limite dépassée ou non. */
private class Day(val date: LocalDate, val ms: Long, val over: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(container: AppContainer) {
    val rules by container.ruleRepository.rules.collectAsState()
    val data by produceState<StatsData?>(null) {
        val now = ZonedDateTime.now()
        container.historyBackfill.run(now) // recopie d'abord les jours manquants
        value = withContext(Dispatchers.IO) {
            val today = now.toLocalDate()
            val usage = container.usageRepository.today(now)
            StatsData(
                history = container.usageRepository.history(today.minusDays(14)),
                today = usage.foregroundMs,
                unlocksToday = usage.unlocks,
                opens = container.eventRepository.opensSince(today.minusDays(7).atStartOfDay(now.zone).toInstant()),
                labels = container.installedApps.load().associate { it.packageName to it.label },
            )
        }
    }
    val stats = data
    val allRules = rules
    if (stats == null || allRules == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val today = ZonedDateTime.now().toLocalDate()
    // shortcut: limites et applis du profil actif seulement ; les combiner si plusieurs profils gênent la série.
    val tracked = allRules.at(ZonedDateTime.now()).values.filter { it.enabled }
    val packages = tracked.map { it.packageName }.toSet()
    val limits = tracked.mapNotNull { rule -> rule.dailyQuotaMin?.let { rule.packageName to it } }.toMap()
    val streak = StatsCalculator.streak(stats.history.usage, limits, today)
    val week = StatsCalculator.weekOverWeek(stats.history.usage, packages, today)
    val opens = StatsCalculator.opens(stats.opens)
    fun label(pkg: String) = stats.labels[pkg] ?: pkg

    // Les 7 derniers jours, aujourd'hui compris.
    val days = (6L downTo 0L).map { ago ->
        val date = today.minusDays(ago)
        val apps = if (ago == 0L) stats.today else stats.history.usage[date].orEmpty()
        Day(date, apps.filterKeys { it in packages }.values.sum(), StatsCalculator.overLimit(apps, limits))
    }
    val yesterday = stats.history.usage[today.minusDays(1)]?.let { days[5].ms }
    // Plus qu'hier : le cadre de la semaine passe au magenta.
    val worse = yesterday != null && days[6].ms > yesterday
    val weekColor = if (worse) Neon.Magenta else Neon.Cyan

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Journal", "Stats")
        if (stats.history.usage.isEmpty()) {
            Text("L'historique commence au premier jour complet après l'installation. Revenez demain.", color = Neon.Soft, fontSize = 14.sp)
        }

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val serieColor = when {
                limits.isEmpty() -> Neon.Muted
                streak > 0 -> Neon.Yellow
                else -> Neon.Magenta
            }
            Tile(
                "Série",
                if (limits.isEmpty()) "—" else "$streak j",
                if (limits.isEmpty()) "posez une limite pour la suivre" else "sans dépasser une limite",
                serieColor,
                if (streak > 0) Neon.PanelYellow else if (limits.isEmpty()) Neon.Panel else Neon.PanelMagenta,
                glow = limits.isNotEmpty(),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val average = StatsCalculator.averageUnlocks(stats.history.unlocks, today)
            Tile(
                "Déverrouillages",
                "${stats.unlocksToday}",
                "aujourd'hui" + (average?.let { " · moy. $it" } ?: ""),
                Neon.Cyan,
                Neon.Panel,
                glow = false,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Column(
            Modifier.fillMaxWidth().neonFrame(if (worse) Neon.Magenta else Neon.Cyan.copy(alpha = 0.5f), if (worse) Neon.PanelMagenta else Neon.Panel, glow = worse)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Caption("7 derniers jours · applis freinées")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    shortDuration(days.sumOf { it.ms }),
                    color = weekColor,
                    fontFamily = Neon.Mono,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = glow(weekColor, 28f)),
                )
                StatsCalculator.change(week.thisWeek.values.sum(), week.lastWeek.values.sum())?.takeIf { week.lastWeekComplete }?.let { change ->
                    Text(
                        if (change <= 0) "▼ ${-change} % vs semaine d'avant" else "▲ $change % vs semaine d'avant",
                        color = if (change <= 0) Neon.Yellow else Neon.Magenta,
                        fontFamily = Neon.Mono,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Bars(days)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(Neon.Magenta))
                Text("Limite dépassée ce jour-là (série cassée)", color = Neon.Soft, fontSize = 12.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth().neonFrame(Neon.Cyan.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Caption("Ouvertures sur 7 jours")
            val apps = (opens.granted.keys + opens.gaveUp.keys).sortedByDescending { opens.granted[it] ?: 0 }
            if (apps.isEmpty()) Text("Aucune ouverture freinée pour l'instant.", color = Neon.Soft, fontSize = 14.sp)
            apps.forEach { pkg ->
                Row {
                    Text(label(pkg), color = Neon.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        buildAnnotatedString {
                            append("${opens.granted[pkg] ?: 0} · ")
                            // Un renoncement est une réussite : jaune.
                            withStyle(SpanStyle(color = Neon.Yellow)) { append("${opens.gaveUp[pkg] ?: 0} renoncées") }
                        },
                        color = Neon.Soft,
                        fontFamily = Neon.Mono,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (opens.intents.isNotEmpty()) {
            Caption("Raisons données")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                opens.intents.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                    Text(
                        buildAnnotatedString {
                            append("$reason ")
                            withStyle(SpanStyle(color = Neon.Yellow, fontFamily = Neon.Mono)) { append("$count") }
                        },
                        color = Neon.Cyan,
                        fontSize = 13.sp,
                        modifier = Modifier.neonFrame(Neon.Cyan.copy(alpha = 0.5f), Color.Transparent).padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, value: String, detail: String, color: Color, fill: Color, glow: Boolean, modifier: Modifier) {
    Column(
        modifier.neonFrame(if (glow) color else color.copy(alpha = 0.5f), fill, glow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Caption(title, if (color == Neon.Cyan) Neon.Muted else color.copy(alpha = 0.85f))
        Text(
            value,
            color = color,
            fontFamily = Neon.Mono,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = if (glow) glow(color, 28f) else null),
        )
        Text(detail, color = Neon.Soft, fontSize = 12.sp)
    }
}

/** Une barre par jour, aujourd'hui allumé ; magenta les jours où une limite a été dépassée. */
@Composable
private fun Bars(days: List<Day>) {
    val max = days.maxOf { it.ms }.coerceAtLeast(1)
    val description = days.joinToString(", ") {
        it.date.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.FRENCH) + " " + shortDuration(it.ms) + if (it.over) " (limite dépassée)" else ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics { contentDescription = "Temps par jour : $description" }
                .drawBehind { drawLine(Neon.Cyan.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, day ->
                val isToday = index == days.lastIndex
                val color = when {
                    day.over && isToday -> Neon.Magenta
                    day.over -> Neon.Magenta.copy(alpha = 0.6f)
                    isToday -> Neon.Cyan
                    else -> Neon.Cyan.copy(alpha = 0.35f)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((day.ms.toFloat() / max).coerceAtLeast(0.02f))
                        .then(if (isToday) Modifier.neonGlow(color, 18.dp, 0.55f, fill = color) else Modifier.background(color)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEachIndexed { index, day ->
                val isToday = index == days.lastIndex
                Text(
                    day.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.FRENCH).uppercase(),
                    color = if (isToday) Neon.Cyan else Neon.Muted,
                    fontFamily = Neon.Mono,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
