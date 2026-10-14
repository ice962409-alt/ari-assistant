package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLockTest {

    @Test
    fun cadaTelefonoTieneSuCodigo() {
        val a = DeviceLock.codeFor("1234abcd")
        assertEquals(8, a.length)
        assertEquals(a, DeviceLock.codeFor("1234abcd"))
        assertNotEquals(a, DeviceLock.codeFor("1234abce"))
    }

    @Test
    fun soloAbreEnLosTelefonosDelDueno() {
        val mine = DeviceLock.codeFor("mi-poco")
        assertTrue(DeviceLock.isAllowed(mine, listOf(mine)))
        assertFalse(DeviceLock.isAllowed(DeviceLock.codeFor("otro"), listOf(mine)))
        // Antes de registrar el teléfono del dueño, abre para que pueda ver su código.
        assertTrue(DeviceLock.isAllowed(mine, emptyList()))
    }
}
