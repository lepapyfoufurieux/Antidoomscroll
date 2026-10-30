package com.antiscrolling.domain

import java.time.Instant
import java.time.ZonedDateTime

object RuleEngine {
    data class Context(
        val usedTodayMs: Long, // début de session (UsageCalculator) + temps écoulé
        val grantUntil: Instant?, // accès temporaire, lu en mémoire
        val focus: FocusSession?, // dernière session lancée, active ou non
        val opensToday: Int = 0, // ouvertures accordées aujourd'hui, pour le délai croissant
    )

    /** Ordre fixe, la première règle qui s'applique gagne. Les blocages durs passent avant l'accès temporaire. */
    fun decide(rule: Rule?, now: ZonedDateTime, ctx: Context): Decision = when {
        rule == null || !rule.enabled -> Decision.Allow
        ctx.focus?.isActive(now) == true -> Decision.Block(Reason.FOCUS)
        rule.schedules.any { it.contains(now) } -> Decision.Block(Reason.SCHEDULE)
        rule.quotaReached(ctx.usedTodayMs) -> Decision.Block(Reason.QUOTA)
        ctx.grantUntil?.isAfter(now.toInstant()) == true -> Decision.Allow // un accès ne lève que la friction
        else -> rule.friction(ctx.opensToday)
    }

    /** Fin du blocage dur en cours (pour mettre les notifications en attente), `null` si l'appli n'est pas bloquée. */
    fun blockedUntil(rule: Rule?, now: ZonedDateTime, ctx: Context): ZonedDateTime? {
        val decision = decide(rule, now, ctx) as? Decision.Block ?: return null
        return when (decision.reason) {
            Reason.FOCUS -> ctx.focus?.endAt?.atZone(now.zone)
            Reason.SCHEDULE -> rule?.schedules?.filter { it.contains(now) }?.maxOfOrNull { it.endAfter(now) }
            Reason.QUOTA -> now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        }
    }
}
