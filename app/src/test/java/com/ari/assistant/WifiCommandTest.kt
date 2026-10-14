package com.ari.assistant

import com.ari.assistant.agents.PhoneAction
import com.ari.assistant.agents.PhoneCommands
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiCommandTest {

    private fun parse(text: String) = PhoneCommands.parse(text)

    @Test
    fun savesAWifiWithItsPassword() {
        assertEquals(PhoneAction.SaveWifi("CafeCentral", "abc12345"), parse("guarda el wifi CafeCentral con la clave abc12345"))
        assertEquals(PhoneAction.SaveWifi("Casa de Ana", "mi clave 2024"), parse("conéctate a Casa de Ana con contraseña mi clave 2024"))
        assertEquals(PhoneAction.SaveWifi("Oficina", "Secreta1"), parse("agrega la red wifi Oficina con clave Secreta1"))
    }

    @Test
    fun opensWifiListWhenNoPassword() {
        assertEquals(PhoneAction.SaveWifi("", ""), parse("abre el wifi"))
        assertEquals(PhoneAction.SaveWifi("", ""), parse("muéstrame las redes wifi"))
    }

    @Test
    fun notAWifiCommand() {
        assertEquals(null, parse("guarda que me gusta el café"))
    }
}
