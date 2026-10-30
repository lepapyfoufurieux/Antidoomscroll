package com.antiscrolling.domain

import java.time.ZonedDateTime

/** Le profil « Par défaut » : pas d'horaire, il s'applique hors de ceux des autres profils. */
const val DEFAULT_PROFILE_ID = 0L
const val DEFAULT_PROFILE_NAME = "Par défaut"

/** Désactivé : jamais actif. Activé : actif pendant ses horaires, ou tout le temps s'il n'en a pas. */
data class Profile(val id: Long, val name: String, val schedules: List<Schedule>, val enabled: Boolean = true) {
    fun isActive(now: ZonedDateTime): Boolean = enabled && (schedules.isEmpty() || schedules.any { it.contains(now) })
}

/** Règles de chaque profil ([rules] : id du profil → package → règle). [profiles] ne contient pas le profil par défaut. */
data class RuleSet(val profiles: List<Profile>, val rules: Map<Long, Map<String, Rule>>) {
    /** Le plus ancien profil actif à [now], sinon le profil par défaut. */
    fun activeProfileId(now: ZonedDateTime): Long =
        profiles.sortedBy { it.id }.firstOrNull { it.isActive(now) }?.id ?: DEFAULT_PROFILE_ID

    /** Règles du profil actif à [now]. */
    fun at(now: ZonedDateTime): Map<String, Rule> = of(activeProfileId(now))

    fun of(profileId: Long): Map<String, Rule> = rules[profileId].orEmpty()

    fun name(profileId: Long): String = profiles.firstOrNull { it.id == profileId }?.name ?: DEFAULT_PROFILE_NAME

    /** Suivie dans au moins un profil : le profil peut changer pendant qu'elle est ouverte. */
    fun tracksAnywhere(packageName: String): Boolean = rules.values.any { it[packageName]?.enabled == true }

    companion object {
        /** Seulement le profil par défaut. */
        fun of(vararg rules: Rule) = RuleSet(emptyList(), mapOf(DEFAULT_PROFILE_ID to rules.associateBy { it.packageName }))
    }
}
