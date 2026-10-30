package com.antiscrolling.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.BuildConfig
import com.antiscrolling.container
import com.antiscrolling.domain.FrictionProgress
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.Outcome
import com.antiscrolling.domain.Reason
import com.antiscrolling.domain.Step
import com.antiscrolling.domain.TaskKind
import com.antiscrolling.domain.opensToday
import com.antiscrolling.ui.AntiscrollingTheme
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonButton
import com.antiscrolling.ui.NeonIcon
import com.antiscrolling.ui.NeonRing
import com.antiscrolling.ui.Tag
import com.antiscrolling.ui.block.steps.DelayStep
import com.antiscrolling.ui.block.steps.DurationStep
import com.antiscrolling.ui.block.steps.IntentStep
import com.antiscrolling.ui.block.steps.TaskStep
import com.antiscrolling.ui.hhmm
import com.antiscrolling.ui.nearLimit
import com.antiscrolling.ui.pressureColor
import com.antiscrolling.ui.scanlines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class BlockActivity : ComponentActivity() {
    // Le QuotaWatcher relance cette activité toutes les 15 s : même package, la progression n'est pas remise à zéro.
    private var blockedPackage by mutableStateOf("")
    private var delaySec by mutableIntStateOf(0)
    private var steps by mutableStateOf(emptyList<Step>())
    private var task by mutableStateOf(TaskKind.NONE)
    private var reason by mutableStateOf<Reason?>(null) // null : friction, sinon blocage sans accès possible

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(dark, dark)
        super.onCreate(savedInstanceState)
        read(intent)
        onBackPressedDispatcher.addCallback(this) { giveUp(null) }
        setContent {
            AntiscrollingTheme {
                val latestFocus by container.ruleRepository.latestFocus.collectAsState()
                val recentOpens by container.eventRepository.recentOpens.collectAsState()
                val rules by container.ruleRepository.rules.collectAsState()
                key(blockedPackage) {
                    val pkg = blockedPackage
                    val label = container.installedApps.label(pkg)
                    val blockedBy = reason
                    if (blockedBy != null) {
                        val focusEnd = latestFocus?.firstOrNull()?.endAt
                        BlockedScreen(pkg, label, blockedMessage(blockedBy, focusEnd), onClose = ::goHome)
                    } else {
                        val usedMs by produceState(0L) {
                            value = withContext(Dispatchers.IO) { container.usageRepository.today(ZonedDateTime.now()).foregroundMs[pkg] ?: 0L }
                        }
                        val now = ZonedDateTime.now()
                        FrictionScreen(
                            pkg,
                            label,
                            recentOpens.orEmpty().opensToday(pkg, now),
                            nearLimit(rules?.at(now)?.get(pkg), usedMs),
                            delaySec,
                            steps,
                            task,
                            onGrant = ::grant,
                            onGiveUp = ::giveUp,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    // Quitté sans choisir (Accueil, écran éteint) : la prochaine ouverture repart du début.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    private fun read(intent: Intent) {
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        delaySec = intent.getIntExtra(EXTRA_DELAY, 0)
        steps = intent.getStringArrayExtra(EXTRA_STEPS)?.map(Step::valueOf).orEmpty()
        task = intent.getStringExtra(EXTRA_TASK)?.let(TaskKind::valueOf) ?: TaskKind.NONE
        reason = intent.getStringExtra(EXTRA_REASON)?.let(Reason::valueOf)
    }

    private fun grant(minutes: Int, intent: String?) {
        val now = Instant.now()
        // En mémoire avant finish() : sinon l'appli qui revient serait rebloquée aussitôt.
        container.eventRepository.grant(blockedPackage, now.plus(Duration.ofMinutes(minutes.toLong())))
        container.eventRepository.recordOpen(OpenEvent(blockedPackage, now, Outcome.GRANTED, intent, minutes))
        finish()
    }

    private fun giveUp(intent: String?) {
        container.eventRepository.recordOpen(OpenEvent(blockedPackage, Instant.now(), Outcome.GAVE_UP, intent, null))
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_DELAY = "delay"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_TASK = "task"
        private const val EXTRA_REASON = "reason"
        private val dark = SystemBarStyle.dark(Color.Transparent.toArgb()) // icônes claires : le fond est toujours sombre

        fun friction(context: Context, packageName: String, delaySec: Int, steps: List<Step>, task: TaskKind): Intent =
            base(context, packageName)
                .putExtra(EXTRA_DELAY, delaySec)
                .putExtra(EXTRA_STEPS, steps.map { it.name }.toTypedArray())
                .putExtra(EXTRA_TASK, task.name)

        fun blocked(context: Context, packageName: String, reason: Reason): Intent =
            base(context, packageName).putExtra(EXTRA_REASON, reason.name)

        private fun base(context: Context, packageName: String) =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

// 1 min en debug, pour passer la recette sans attendre 5 min.
private val DEFAULT_GRANT_MIN = if (BuildConfig.DEBUG) 1 else 5

/** Icône et nom de l'appli, magenta quand sa limite est proche, avec un léger dédoublement de l'autre couleur. */
@Composable
private fun BlockedApp(packageName: String, label: String, alert: Boolean) {
    val color = if (alert) Neon.Magenta else Neon.Cyan
    val shift = if (alert) Neon.Cyan else Neon.Magenta
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonIcon(packageName, color, 64.dp)
        Text(
            label.uppercase(),
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(shift.copy(alpha = 0.75f), Offset(4f, 0f), 2f)),
        )
    }
}

@Composable
private fun FrictionScreen(
    packageName: String,
    label: String,
    opensToday: Int,
    alert: Boolean,
    delaySec: Int,
    steps: List<Step>,
    task: TaskKind,
    onGrant: (minutes: Int, intent: String?) -> Unit,
    onGiveUp: (intent: String?) -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var intent by rememberSaveable { mutableStateOf<String?>(null) }
    val progress = FrictionProgress(steps, index, intent)
    fun advance(next: FrictionProgress) {
        index = next.index
        intent = next.intent
        if (next.done) onGrant(next.grantMinutes(DEFAULT_GRANT_MIN), next.intent)
    }
    val continueLabel = if (progress.isLast) "Ouvrir $DEFAULT_GRANT_MIN min" else "Continuer"
    val nth = opensToday + 1
    BackHandler { onGiveUp(intent) } // Retour = Renoncer, en gardant la raison déjà donnée

    Column(
        Modifier.fillMaxSize().scanlines().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Tag("Interception") }
            Text(
                "${if (nth == 1) "1re" else "${nth}e"} OUVERTURE AUJOURD'HUI",
                color = Neon.Muted,
                fontFamily = Neon.Mono,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }
        BlockedApp(packageName, label, alert)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (progress.current) {
                Step.DELAY -> DelayStep(delaySec, continueLabel, pressureColor(opensToday)) { advance(progress.next()) }
                Step.INTENT -> IntentStep(label) { advance(progress.withIntent(it)) }
                Step.TASK -> TaskStep(task, continueLabel) { advance(progress.next()) }
                Step.DURATION -> DurationStep { advance(progress.withDuration(it)) }
                null -> Unit
            }
        }
        NeonButton("Renoncer", { onGiveUp(intent) }, Modifier.fillMaxWidth(), color = Neon.Magenta, filled = true)
    }
}

private fun blockedMessage(reason: Reason, focusEnd: Instant?): String = when (reason) {
    Reason.FOCUS -> "Mode focus en cours" + (focusEnd?.let { " jusqu'à ${hhmm(it)}" } ?: "") + "."
    Reason.SCHEDULE -> "Plage de blocage en cours."
    Reason.QUOTA -> "Le temps prévu pour aujourd'hui est écoulé. À demain !"
}

@Composable
private fun BlockedScreen(packageName: String, label: String, message: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().scanlines().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) { Tag("Blocage") }
        Spacer(Modifier.weight(1f))
        BlockedApp(packageName, label, alert = true)
        NeonRing(Neon.Magenta, 1f, 120.dp) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Neon.Magenta, modifier = Modifier.size(40.dp))
        }
        Text(message, color = Neon.Text, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        NeonButton("Fermer", onClose, Modifier.fillMaxWidth(), color = Neon.Magenta)
    }
}
