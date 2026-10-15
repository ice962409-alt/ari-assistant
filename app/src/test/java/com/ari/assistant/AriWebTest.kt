package com.ari.assistant

import com.ari.assistant.AriWeb.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AriWebTest {

    @Test
    fun understandsWhatToLookUp() {
        assertEquals(Request.Search("la fotosintesis"), AriWeb.parse("Busca en internet qué es la fotosíntesis"))
        assertEquals(Request.Wiki("la fotosintesis"), AriWeb.parse("Busca en wikipedia la fotosíntesis"))
        assertEquals(Request.Search("las noticias de hoy"), AriWeb.parse("busca en la web las noticias de hoy"))
        assertEquals(Request.Search("bitcoin"), AriWeb.parse("revisa en internet bitcoin"))
        assertEquals(Request.Search("el dolar"), AriWeb.parse("qué dice internet sobre el dólar"))
        assertEquals(Request.Wiki("benito juarez"), AriWeb.parse("¿Quién fue Benito Juárez?"))
        assertEquals(Request.Weather("monterrey"), AriWeb.parse("¿Cómo está el clima en Monterrey?"))
        assertEquals(Request.Weather("guadalajara"), AriWeb.parse("el clima de Guadalajara hoy"))
        assertNull(AriWeb.parse("¿qué tengo hoy?"))
    }

    @Test
    fun weatherCodesInWords() {
        assertEquals("despejado", AriWeb.sky(0))
        assertEquals("con lluvia", AriWeb.sky(61))
        assertEquals("con tormenta", AriWeb.sky(95))
    }

    @Test
    fun picksUpWhatGemmaAsksToSearch() {
        assertEquals("precio del bitcoin hoy", AriWeb.autoQuery("No tengo ese dato actualizado.\nBUSCAR: precio del bitcoin hoy"))
        assertEquals("quien gano el partido", AriWeb.autoQuery("**BUSCAR:** quien gano el partido."))
        assertNull(AriWeb.autoQuery("El agua hierve a 100 grados."))
        assertNull(AriWeb.autoQuery("BUSCAR: a"))
    }
}
