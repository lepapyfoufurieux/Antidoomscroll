package com.antiscrolling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProfileTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val pkg = "com.instagram.android"
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, paris)

    // Jeudi 29/10/2026
    private val work = Profile(1, "Travail", listOf(Schedule(1, Schedule.EVERY_DAY, 9 * 60, 18 * 60)))
    private val night = Profile(2, "Nuit", listOf(Schedule(2, Schedule.EVERY_DAY, 22 * 60, 7 * 60)))
    private val strict = Rule(pkg, enabled = true, delaySec = 60)
    private val loose = Rule(pkg, enabled = true, delaySec = 5)
    private val set = RuleSet(listOf(night, work), mapOf(DEFAULT_PROFILE_ID to mapOf(pkg to loose), 1L to mapOf(pkg to strict)))

    @Test
    fun `hors de tout horaire, le profil par defaut s'applique`() {
        assertEquals(DEFAULT_PROFILE_ID, set.activeProfileId(at(29, 19)))
        assertEquals(loose, set.at(at(29, 19))[pkg])
    }

    @Test
    fun `pendant son horaire, le profil et ses regles s'appliquent, debut inclus, fin exclue`() {
        assertEquals(1L, set.activeProfileId(at(29, 9)))
        assertEquals(strict, set.at(at(29, 9))[pkg])
        assertEquals(DEFAULT_PROFILE_ID, set.activeProfileId(at(29, 18)))
    }

    @Test
    fun `un horaire qui chevauche minuit reste actif le lendemain matin`() {
        assertEquals(2L, set.activeProfileId(at(29, 23)))
        assertEquals(2L, set.activeProfileId(at(30, 6, 59)))
        assertEquals(DEFAULT_PROFILE_ID, set.activeProfileId(at(30, 7)))
    }

    @Test
    fun `deux horaires qui se chevauchent, le plus ancien profil gagne`() {
        val late = Profile(3, "Soir", listOf(Schedule(3, Schedule.EVERY_DAY, 17 * 60, 23 * 60)))
        assertEquals(1L, set.copy(profiles = set.profiles + late).activeProfileId(at(29, 17, 30)))
    }

    @Test
    fun `un profil desactive ne s'applique jamais, meme pendant son horaire`() {
        val off = set.copy(profiles = listOf(night, work.copy(enabled = false)))
        assertEquals(DEFAULT_PROFILE_ID, off.activeProfileId(at(29, 10)))
    }

    @Test
    fun `un profil active sans horaire s'applique tout le temps, desactive jamais`() {
        val manual = Profile(5, "Vacances", emptyList())
        assertEquals(5L, set.copy(profiles = listOf(manual)).activeProfileId(at(29, 19)))
        assertEquals(DEFAULT_PROFILE_ID, set.copy(profiles = listOf(manual.copy(enabled = false))).activeProfileId(at(29, 19)))
    }

    @Test
    fun `un profil sans regle pour l'appli la laisse libre`() {
        assertEquals(null, set.at(at(29, 23))[pkg])
    }

    @Test
    fun `suivie dans un profil, meme s'il n'est pas actif`() {
        assertTrue(set.tracksAnywhere(pkg))
        assertFalse(RuleSet.of(strict.copy(enabled = false)).tracksAnywhere(pkg))
        assertFalse(set.tracksAnywhere("com.other"))
    }
}
