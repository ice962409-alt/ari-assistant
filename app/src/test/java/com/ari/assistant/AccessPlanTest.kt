package com.ari.assistant

import com.ari.assistant.AccessPlan.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPlanTest {

    @Test
    fun soloLoQueAplicaEnEsteTelefono() {
        val ceo = AccessPlan.applicable(sdk = 35, ceo = true, xiaomi = true)
        assertTrue(Item.ACCESSIBILITY in ceo)
        assertTrue(Item.PHOTOS in ceo)
        assertFalse(Item.PHOTOS_OLD in ceo)
        assertTrue(Item.AUTOSTART in ceo)
        // La Ari para otros no pide lo que solo usa la del dueño, y sin Xiaomi no hay inicio automático.
        val otros = AccessPlan.applicable(sdk = 35, ceo = false, xiaomi = false)
        assertFalse(Item.ACCESSIBILITY in otros)
        assertFalse(Item.AUTOSTART in otros)
        assertTrue(Item.NOTIFICATION_ACCESS in otros)
        // Android 12: sin permiso de notificaciones (no existía) y fotos con el permiso viejo.
        val old = AccessPlan.applicable(sdk = 31, ceo = true, xiaomi = false)
        assertFalse(Item.NOTIFICATIONS in old)
        assertTrue(Item.PHOTOS_OLD in old)
        assertTrue(Item.EXACT_ALARMS in old)
        assertFalse(Item.EXACT_ALARMS in AccessPlan.applicable(sdk = 30, ceo = true, xiaomi = false))
    }

    @Test
    fun losPermisosNormalesSePidenDeUnaVez() {
        val missing = listOf(Item.MICROPHONE, Item.LOCATION, Item.ACCESSIBILITY, Item.CONTACTS)
        assertEquals(
            listOf(
                "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION", "android.permission.READ_CONTACTS"
            ),
            AccessPlan.permissionsToAsk(missing)
        )
    }

    @Test
    fun lasPantallasVanUnaPorUnaYSePuedenSaltar() {
        val missing = listOf(Item.MICROPHONE, Item.ACCESSIBILITY, Item.OVERLAY, Item.BATTERY)
        assertEquals(Item.ACCESSIBILITY, AccessPlan.nextScreen(missing, emptySet()))
        assertEquals(Item.OVERLAY, AccessPlan.nextScreen(missing, setOf(Item.ACCESSIBILITY)))
        assertNull(AccessPlan.nextScreen(missing, setOf(Item.ACCESSIBILITY, Item.OVERLAY, Item.BATTERY)))
        val screens = listOf(Item.ACCESSIBILITY, Item.OVERLAY, Item.BATTERY)
        assertEquals("Paso 2 de 3: Mostrarse sobre otras apps", AccessPlan.stepLabel(Item.OVERLAY, screens))
    }

    @Test
    fun laListaCuentaCadaCosaUnaVez() {
        val items = listOf(Item.MICROPHONE, Item.PHOTOS, Item.PHOTOS_OLD, Item.OVERLAY)
        val lines = AccessPlan.lines(items, setOf(Item.MICROPHONE))
        assertEquals("✓ Micrófono", lines[0])
        assertEquals("○ Fotos — mostrarte tus fotos y capturas", lines[1])
        assertEquals(3, lines.size)
        assertEquals("1 de 3 listos", AccessPlan.progress(items, setOf(Item.MICROPHONE)))
    }

    @Test
    fun entiendeElPedido() {
        listOf("Dale todo a Ari", "darle todo a ARI", "activa todo", "todos los permisos", "dale acceso total").forEach {
            assertTrue(it, AccessPlan.asks(it))
        }
        listOf("dale play", "activa el wifi", "todo bien").forEach { assertFalse(it, AccessPlan.asks(it)) }
    }
}
