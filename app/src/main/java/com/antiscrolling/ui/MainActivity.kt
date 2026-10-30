package com.antiscrolling.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

class MainActivity : ComponentActivity() {
    // Incrémenté à chaque retour dans l'appli, pour revérifier les permissions au retour des paramètres.
    private var resumeCount by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
    }
}

@Composable
private fun MainTabs(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(container)
                1 -> AppsScreen(container)
                2 -> ProfilesScreen(container)
                3 -> FocusScreen(container)
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
