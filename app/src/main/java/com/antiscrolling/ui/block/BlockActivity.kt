package com.antiscrolling.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antiscrolling.BuildConfig
import com.antiscrolling.container
import com.antiscrolling.domain.FrictionProgress
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.Outcome
import com.antiscrolling.domain.Reason
import com.antiscrolling.domain.Step
import com.antiscrolling.domain.TaskKind
import com.antiscrolling.ui.AntiscrollingTheme
import com.antiscrolling.ui.block.steps.DelayStep
import com.antiscrolling.ui.block.steps.DurationStep
import com.antiscrolling.ui.block.steps.IntentStep
import com.antiscrolling.ui.block.steps.TaskStep
import com.antiscrolling.ui.hhmm
import java.time.Duration
import java.time.Instant

class BlockActivity : ComponentActivity() {
    // Le QuotaWatcher relance cette activité toutes les 15 s : même package, la progression n'est pas remise à zéro.
    private var blockedPackage by mutableStateOf("")
    private var delaySec by mutableIntStateOf(0)
    private var steps by mutableStateOf(emptyList<Step>())
    private var task by mutableStateOf(TaskKind.NONE)
    private var reason by mutableStateOf<Reason?>(null) // null : friction, sinon blocage sans accès possible

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        read(intent)
        onBackPressedDispatcher.addCallback(this) { giveUp(null) }
        setContent {
            AntiscrollingTheme {
                val latestFocus by container.ruleRepository.latestFocus.collectAsState()
                key(blockedPackage) {
                    val label = container.installedApps.label(blockedPackage)
                    val blockedBy = reason
                    if (blockedBy != null) {
                        val focusEnd = latestFocus?.firstOrNull()?.endAt
                        BlockedScreen(label, blockedMessage(blockedBy, focusEnd), onClose = ::goHome)
                    } else {
                        FrictionScreen(label, delaySec, steps, task, onGrant = ::grant, onGiveUp = ::giveUp)
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

@Composable
private fun FrictionScreen(
    label: String,
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
    BackHandler { onGiveUp(intent) } // Retour = Renoncer, en gardant la raison déjà donnée

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        when (progress.current) {
            Step.DELAY -> DelayStep(delaySec, continueLabel) { advance(progress.next()) }
            Step.INTENT -> IntentStep(label) { advance(progress.withIntent(it)) }
            Step.TASK -> TaskStep(task, continueLabel) { advance(progress.next()) }
            Step.DURATION -> DurationStep { advance(progress.withDuration(it)) }
            null -> Unit
        }
        OutlinedButton(onClick = { onGiveUp(intent) }, modifier = Modifier.fillMaxWidth()) { Text("Renoncer") }
    }
}

private fun blockedMessage(reason: Reason, focusEnd: Instant?): String = when (reason) {
    Reason.FOCUS -> "Mode focus en cours" + (focusEnd?.let { " jusqu'à ${hhmm(it)}" } ?: "") + "."
    Reason.SCHEDULE -> "Plage de blocage en cours."
    Reason.QUOTA -> "Le temps prévu pour aujourd'hui est écoulé. À demain !"
}

@Composable
private fun BlockedScreen(label: String, message: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
    }
}
