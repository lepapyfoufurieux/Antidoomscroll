package com.antiscrolling.domain

import java.time.Instant
import java.time.ZonedDateTime

/** Étapes de l'écran de friction, dans l'ordre où elles s'enchaînent. */
enum class Step { DELAY, INTENT, TASK, DURATION }

enum class TaskKind { NONE, COPY, MATH }

/** Petite tâche à réussir avant d'ouvrir l'appli. */
sealed interface Task {
    fun check(answer: String): Boolean

    data class Copy(val sentence: String) : Task {
        // Espaces et casse ignorés : on vérifie l'effort, pas la frappe.
        override fun check(answer: String) = normalize(answer) == normalize(sentence)
        private fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    data class Math(val a: Int, val b: Int) : Task {
        override fun check(answer: String) = answer.trim().toIntOrNull() == a + b
    }

    companion object {
        const val COPY_SENTENCE = "J'ouvre cette appli parce que je l'ai décidé."
    }
}

/** Raisons proposées par la question d'intention. */
val INTENTS = listOf("Répondre à un message", "Chercher quelque chose", "M'occuper", "Par habitude")

/**
 * Avancement dans les étapes de friction. Les composables affichent [current] et appellent [next] ;
 * quand [done], l'accès est accordé pour [grantMinutes].
 */
data class FrictionProgress(
    val steps: List<Step>,
    val index: Int = 0,
    val intent: String? = null,
    val chosenMin: Int? = null,
) {
    val current: Step? get() = steps.getOrNull(index)
    val done: Boolean get() = index >= steps.size
    val isLast: Boolean get() = index == steps.lastIndex

    fun next(): FrictionProgress = copy(index = index + 1)
    fun withIntent(reason: String): FrictionProgress = copy(intent = reason).next()
    fun withDuration(minutes: Int): FrictionProgress = copy(chosenMin = minutes).next()
    fun grantMinutes(defaultMin: Int): Int = chosenMin ?: defaultMin
}

enum class Outcome { GRANTED, GAVE_UP }

/** Une ouverture d'appli suivie qui est passée par l'écran de friction. */
data class OpenEvent(
    val packageName: String,
    val at: Instant,
    val outcome: Outcome,
    val intent: String?,
    val grantedMin: Int?,
)

/** Ouvertures accordées aujourd'hui pour [packageName] : ce qui allonge le délai croissant. */
fun List<OpenEvent>.opensToday(packageName: String, now: ZonedDateTime): Int {
    val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
    return count { it.packageName == packageName && it.outcome == Outcome.GRANTED && !it.at.isBefore(startOfDay) }
}
