package com.antiscrolling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTest {
    private val rule = Rule("com.instagram.android", enabled = true, delaySec = 15, dailyQuotaMin = 30)
    private fun min(m: Int) = m * 60_000L

    @Test
    fun `alerte dans les 5 dernieres minutes seulement`() {
        assertFalse(rule.quotaSoon(min(25) - 1))
        assertTrue(rule.quotaSoon(min(25)))
        assertTrue(rule.quotaSoon(min(30) - 1))
        assertFalse(rule.quotaSoon(min(30)))
    }

    @Test
    fun `pas d'alerte sans limite ni pour une limite de 5 min ou moins`() {
        assertFalse(rule.copy(dailyQuotaMin = null).quotaSoon(min(100)))
        assertFalse(rule.copy(dailyQuotaMin = 5).quotaSoon(min(0)))
        assertFalse(rule.copy(dailyQuotaMin = 5).quotaSoon(min(4)))
        assertTrue(rule.copy(dailyQuotaMin = 6).quotaSoon(min(1)))
    }

    @Test
    fun `limite de 0 min atteinte tout de suite`() {
        assertTrue(rule.copy(dailyQuotaMin = 0).quotaReached(0))
    }

    @Test
    fun `temps restant, jamais negatif, rien sans limite`() {
        assertEquals(min(30), rule.remainingMs(0))
        assertEquals(min(1), rule.remainingMs(min(29)))
        assertEquals(0L, rule.remainingMs(min(45)))
        assertEquals(null, rule.copy(dailyQuotaMin = null).remainingMs(0))
    }
}
