package com.antiscrolling.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.antiscrolling.AppContainer
import com.antiscrolling.container
import com.antiscrolling.service.BlockerAccessibilityService
import com.antiscrolling.ui.apps.AppsScreen
import com.antiscrolling.ui.focus.FocusScreen
import com.antiscrolling.ui.home.HomeScreen
import com.antiscrolling.ui.profiles.ProfilesScreen
import com.antiscrolling.ui.onboarding.AccessibilityScreen
import com.antiscrolling.ui.onboarding.ConsentScreen
import com.antiscrolling.ui.onboarding.UsageAccessScreen
import com.antiscrolling.ui.stats.StatsScreen
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    // Incrémenté à chaque retour dans l'appli, pour revérifier les permissions au retour des paramètres.
    private var resumeCount by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(dark, dark)
        super.onCreate(savedInstanceState)
        val prefs = container.prefs
        setContent {
            AntiscrollingTheme {
                var consent by remember { mutableStateOf(prefs.getBoolean(KEY_CONSENT, false)) }
                val serviceOn = remember(resumeCount) { isServiceEnabled() }
                val usageOn = remember(resumeCount) { container.usageRepository.hasAccess() }
                when {
                    !consent -> ConsentScreen(onAccept = {
                        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
                        consent = true
                    })
                    !serviceOn -> AccessibilityScreen(onOpenSettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    })
                    !usageOn -> UsageAccessScreen(onOpenSettings = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    })
                    else -> MainTabs(container)
                }
            }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val self = ComponentName(this, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    private companion object {
        const val KEY_CONSENT = "consent"
        val dark = SystemBarStyle.dark(Color.Transparent.toArgb()) // icônes claires : le fond est toujours sombre
    }
}

@Composable
private fun MainTabs(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sessions by container.ruleRepository.latestFocus.collectAsState()
    // Recalcule « en cours » quand le focus se termine pendant que l'appli est affichée.
    val tick by produceState(0) {
        while (true) {
            delay(15_000)
            value = value + 1
        }
    }
    val focus = remember(sessions, tick) { sessions?.firstOrNull()?.takeIf { it.isActive(ZonedDateTime.now()) } }
    // Pendant un focus, tout passe au magenta.
    val accent = if (focus != null) Neon.Magenta else Neon.Cyan
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Neon.Bg,
                modifier = Modifier.drawBehind { drawLine(accent.copy(alpha = 0.7f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
            ) {
                TABS.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = Neon.Muted,
                            unselectedTextColor = Neon.Muted,
                        ),
                        modifier = if (tab == index) {
                            Modifier.drawBehind { drawLine(accent, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx()) }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        },
        modifier = Modifier.neonGrid(accent),
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(container)
                1 -> AppsScreen(container)
                2 -> ProfilesScreen(container)
                3 -> FocusScreen(container, focus)
                else -> StatsScreen(container)
            }
        }
    }
}

private val TABS = listOf(
    "Aujourd'hui" to Icons.Filled.Home,
    "Applis" to Icons.AutoMirrored.Filled.List,
    "Profils" to Icons.Filled.Person,
    "Focus" to Icons.Filled.Lock,
    "Stats" to Icons.Filled.DateRange,
)
