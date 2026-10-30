package com.antiscrolling.service

import com.antiscrolling.domain.Decision
import com.antiscrolling.domain.FocusSession
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.RuleSet
import com.antiscrolling.domain.RuleEngine
import com.antiscrolling.domain.opensToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Rester dans une appli ne génère aucun événement : on rappelle decide() toutes les 15 s tant qu'elle est ouverte. */
class QuotaWatcher(
    private val scope: CoroutineScope,
    private val rules: StateFlow<RuleSet?>,
    private val grants: StateFlow<Map<String, Instant>?>,
    private val latestFocus: StateFlow<List<FocusSession>?>,
    private val recentOpens: StateFlow<List<OpenEvent>?>,
    private val isInteractive: () -> Boolean,
    private val now: () -> ZonedDateTime,
    private val usedToday: suspend (String, ZonedDateTime) -> Long,
    private val onDecision: (String, Decision) -> Unit,
    private val onQuotaSoon: (String) -> Unit,
) {
    private var current: String? = null
    private var job: Job? = null
    private val warned = mutableSetOf<Pair<String, LocalDate>>()

    /** Décide tout de suite pour [packageName], puis toutes les 15 s. */
    fun watch(packageName: String) {
        current = packageName
        job?.cancel()
        job = scope.launch {
            // Temps du jour lu dans UsageStats au début de la session, puis compté ici.
            var baseMs = 0L
            var baseAt: ZonedDateTime? = null
            while (true) {
                // Démarrage à froid : attendre les règles et les accès, sinon tout serait autorisé.
                val set = rules.filterNotNull().first()
                if (!set.tracksAnywhere(packageName)) return@launch
                val t = now()
                // Suivie seulement dans un autre profil : on continue de vérifier, le profil peut changer pendant l'usage.
                val rule = set.at(t)[packageName]?.takeIf { it.enabled }
                if (rule != null && isInteractive()) {
                    val grant = grants.filterNotNull().first()[packageName]
                    val focus = latestFocus.filterNotNull().first().firstOrNull()
                    val opens = recentOpens.filterNotNull().first().opensToday(packageName, t)
                    // Relu après minuit, et seulement pour une appli avec une limite.
                    if (rule.dailyQuotaMin != null && baseAt?.toLocalDate() != t.toLocalDate()) {
                        baseMs = usedToday(packageName, t)
                        baseAt = t
                    }
                    val used = baseAt?.let { baseMs + Duration.between(it, t).toMillis() } ?: 0L
                    val decision = RuleEngine.decide(rule, t, RuleEngine.Context(used, grant, focus, opens))
                    onDecision(packageName, decision)
                    if (decision !is Decision.Block && rule.quotaSoon(used) && warned.add(packageName to t.toLocalDate())) {
                        onQuotaSoon(packageName)
                    }
                } else {
                    baseAt = null // l'écran éteint ne compte pas : relire à son retour
                }
                delay(WATCH_INTERVAL_MS)
            }
        }
    }

    fun recheck() {
        current?.let(::watch)
    }

    fun stop() {
        current = null
        job?.cancel()
    }

    private companion object {
        const val WATCH_INTERVAL_MS = 15_000L
    }
}
