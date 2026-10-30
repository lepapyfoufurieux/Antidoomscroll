package com.antiscrolling.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.antiscrolling.container
import com.antiscrolling.ui.MainActivity
import com.antiscrolling.ui.durationLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/** Temps restant avant la limite de l'appli choisie dans ses réglages, ou son temps du jour si elle n'a pas de limite. */
class TimeLeftWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (title, value) = withContext(Dispatchers.IO) { content(context) }
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    Text(
                        value,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }

    private suspend fun content(context: Context): Pair<String, String> {
        val c = context.container
        val pkg = c.prefs.getString(PREF_PACKAGE, null)
            ?: return "Antiscrolling" to "Choisissez une appli"
        val label = c.installedApps.label(pkg)
        val rule = c.ruleRepository.rules.filterNotNull().first().at(ZonedDateTime.now())[pkg]
        if (!c.usageRepository.hasAccess()) return label to "Accès à l'utilisation requis"
        val used = c.usageRepository.today(ZonedDateTime.now()).foregroundMs[pkg] ?: 0L
        val remaining = rule?.takeIf { it.enabled }?.remainingMs(used)
        return label to when {
            remaining == null -> "${durationLabel((used / 60_000).toInt())} aujourd'hui"
            remaining == 0L -> "Limite atteinte"
            else -> "${durationLabel(((remaining + 59_999) / 60_000).toInt())} restantes" // arrondi au-dessus : jamais « 0 min restantes »
        }
    }

    companion object {
        const val PREF_PACKAGE = "widget_package"

        suspend fun refresh(context: Context) = TimeLeftWidget().updateAll(context)
    }
}

class TimeLeftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TimeLeftWidget()
}
