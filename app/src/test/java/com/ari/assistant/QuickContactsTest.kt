package com.ari.assistant

import com.ari.assistant.QuickContactRules.Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickContactsTest {

    private val four = (1..4).map { QuickContact("P$it", "55 1234 000$it") }

    @Test
    fun guardadoSaleDirecto() {
        assertEquals(Check.Saved, QuickContactRules.check("+52 55 1234 0002", four, 4))
    }

    @Test
    fun laAriParaOtrosTieneMaximo4() {
        assertTrue(QuickContactRules.check("5599998888", four, 4) is Check.Full)
        assertEquals(Check.AskToSave(1), QuickContactRules.check("5599998888", four.take(3), 4))
    }

    @Test
    fun laAriCeoNoTieneLimite() {
        val many = (1..20).map { QuickContact("P$it", "55000000${it + 10}") }
        assertEquals(Check.AskToSave(null), QuickContactRules.check("5599998888", many, null))
    }
}
