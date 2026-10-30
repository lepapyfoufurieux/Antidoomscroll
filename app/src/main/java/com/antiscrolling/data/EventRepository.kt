package com.antiscrolling.data

import com.antiscrolling.data.db.AppDao
import com.antiscrolling.data.db.GrantEntity
import com.antiscrolling.data.db.OpenEventEntity
import com.antiscrolling.domain.OpenEvent
import com.antiscrolling.domain.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class EventRepository(
    private val dao: AppDao,
    private val scope: CoroutineScope,
    recentSince: () -> Instant = { Instant.now().minus(Duration.ofDays(2)) },
) {
    private val _grants = MutableStateFlow<Map<String, Instant>?>(null)
    private val _recentOpens = MutableStateFlow<List<OpenEvent>?>(null)

    /** Accès temporaires par package. `null` tant que la base n'a pas été lue. */
    val grants: StateFlow<Map<String, Instant>?> = _grants

    /** Ouvertures des deux derniers jours, de quoi compter celles du jour. `null` tant que la base n'a pas été lue. */
    val recentOpens: StateFlow<List<OpenEvent>?> = _recentOpens

    init {
        scope.launch {
            val saved = dao.grants().associate { it.packageName to Instant.ofEpochMilli(it.until) }
            _grants.update { saved + (it ?: emptyMap()) }
        }
        scope.launch {
            val saved = opensSince(recentSince())
            _recentOpens.update { saved + (it ?: emptyList()) }
        }
    }

    /** Écrit en mémoire tout de suite (avant le finish() de l'écran de blocage), en base ensuite. */
    fun grant(packageName: String, until: Instant) {
        _grants.update { (it ?: emptyMap()) + (packageName to until) }
        scope.launch { dao.upsertGrant(GrantEntity(packageName, until.toEpochMilli())) }
    }

    /** Même principe : le délai croissant de l'ouverture suivante doit déjà la compter. */
    fun recordOpen(open: OpenEvent) {
        _recentOpens.update { (it ?: emptyList()) + open }
        scope.launch {
            dao.insertOpen(
                OpenEventEntity(0, open.packageName, open.at.toEpochMilli(), open.outcome.name, open.intent, open.grantedMin),
            )
        }
    }

    suspend fun opensSince(since: Instant): List<OpenEvent> = dao.opensSince(since.toEpochMilli()).map {
        OpenEvent(it.packageName, Instant.ofEpochMilli(it.at), Outcome.valueOf(it.outcome), it.intent, it.grantedMin)
    }
}
