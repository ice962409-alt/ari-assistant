package com.ari.assistant

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun sinRecuerdosNoMolesta() {
        assertNull(BackupReminder.reminderText(0, 0, memories = 0, now = now))
    }

    @Test
    fun nuncaRespaldadoAvisa() {
        assertNotNull(BackupReminder.reminderText(0, 0, memories = 3, now = now))
    }

    @Test
    fun respaldoRecienteNoAvisa() {
        assertNull(BackupReminder.reminderText(now - 2 * day, 0, memories = 3, now = now))
    }

    @Test
    fun respaldoViejoAvisaConLosDias() {
        val text = BackupReminder.reminderText(now - 10 * day, 0, memories = 3, now = now)
        assertTrue(text!!.contains("10 días"))
    }

    @Test
    fun comoMuchoUnAvisoAlDia() {
        assertNull(BackupReminder.reminderText(0, now - day / 2, memories = 3, now = now))
        assertNotNull(BackupReminder.reminderText(0, now - 2 * day, memories = 3, now = now))
    }
}
