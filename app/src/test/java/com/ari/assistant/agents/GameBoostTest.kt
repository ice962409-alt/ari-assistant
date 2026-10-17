package com.ari.assistant.agents

import com.ari.assistant.agents.GameBoost.Confidence
import com.ari.assistant.agents.GameBoost.Heat
import com.ari.assistant.agents.GameBoost.PlayedSession
import com.ari.assistant.agents.GameBoost.Thermal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBoostTest {

    private val calm = GameSnapshot(
        availMemBytes = 5_200_000_000L,
        totalMemBytes = 11_800_000_000L,
        lowMemory = false,
        ariMemBytes = 400_000_000L,
        gemmaLoaded = false,
        thermalStatus = 0,
        thermalHeadroom = 0.3f,
        batteryLevel = 80,
        batteryTenthsC = 315,
        charging = false,
        powerSave = false,
        chip = "Mediatek MT6899",
        eden = EdenApp("dev.eden.eden_emulator", "1.0.0", known = true)
    )

    @Test
    fun noInventaLaTemperatura() {
        assertEquals(Thermal.Unavailable, GameBoost.thermal(null, null))
        assertEquals(Thermal.Unavailable, GameBoost.thermal(0, Float.NaN))
        assertEquals(Thermal.Unavailable, GameBoost.thermal(0, 0f))
        assertEquals(Thermal.Known(Heat.NORMAL, 0.3f), GameBoost.thermal(0, 0.3f))
        assertEquals(Thermal.Known(Heat.HOT, null), GameBoost.thermal(2, null))
        assertEquals(Heat.VERY_HOT, (GameBoost.thermal(0, 1.05f) as Thermal.Known).heat)
        // Se queda con la lectura más caliente de las dos.
        assertEquals(Heat.HOT, (GameBoost.thermal(1, 0.9f) as Thermal.Known).heat)
        assertEquals(Heat.CRITICAL, (GameBoost.thermal(5, Float.NaN) as Thermal.Known).heat)
    }

    @Test
    fun laBateriaEnGrados() {
        assertEquals(31.5f, GameBoost.batteryCelsius(315)!!, 0.001f)
        assertNull(GameBoost.batteryCelsius(null))
        assertNull(GameBoost.batteryCelsius(2000))
        assertNull(GameBoost.batteryCelsius(-300))
    }

    @Test
    fun encuentraEden() {
        val both = mapOf("dev.eden.eden_emulator.nightly" to "1.1.0", "dev.eden.eden_emulator" to "1.0.0")
        assertEquals(EdenApp("dev.eden.eden_emulator", "1.0.0", known = true), GameBoost.pickEden(both, emptyList()))
        val fork = listOf(Triple("Notas", "com.notas", "2.0"), Triple("Eden", "com.fork.eden", "1.0.0"))
        assertEquals(EdenApp("com.fork.eden", "1.0.0", known = false), GameBoost.pickEden(emptyMap(), fork))
        // La variante que se hace pasar por un juego no cuenta como Eden.
        assertNull(GameBoost.pickEden(emptyMap(), listOf(Triple("Eden Optimized", "com.miHoYo.Yuanshen", "1.0"))))
        // "Eden" tiene que ser una palabra suelta.
        assertNull(GameBoost.pickEden(emptyMap(), listOf(Triple("Edenred", "com.edenred.mx", "5.0"))))
        assertNull(GameBoost.pickEden(emptyMap(), listOf(Triple("Credencial Digital", "mx.credencial", "1.0"))))
        assertNull(GameBoost.pickEden(emptyMap(), emptyList()))
    }

    @Test
    fun recomiendaSoloLoQueHaceFalta() {
        assertEquals(listOf("Con lo que pude medir, todo se ve bien para jugar."), GameBoost.recommendations(calm))

        val busy = calm.copy(
            availMemBytes = 1_500_000_000L, gemmaLoaded = true, thermalStatus = 3,
            batteryTenthsC = 425, charging = true, powerSave = true, eden = null
        )
        val tips = GameBoost.recommendations(busy).joinToString("\n")
        assertTrue(tips.contains("Haz una pausa"))
        assertTrue(tips.contains("42.5 °C"))
        assertTrue(tips.contains("mientras carga"))
        assertTrue(tips.contains("ahorro de batería"))
        assertTrue(tips.contains("poca memoria"))
        assertTrue(tips.contains("Gemma está cargada"))
        assertTrue(tips.contains("No encontré Eden"))
        assertFalse(tips.contains("todo se ve bien"))

        // Sin datos de temperatura no dice que todo está bien.
        val noHeat = GameBoost.recommendations(calm.copy(thermalStatus = 0, thermalHeadroom = Float.NaN)).joinToString("\n")
        assertTrue(noHeat.contains("No sé la temperatura"))
        assertFalse(noHeat.contains("todo se ve bien"))

        val loading = GameBoost.recommendations(calm.copy(gemmaLoading = true)).joinToString("\n")
        assertTrue(loading.contains("Gemma se está cargando"))

        val small = GameBoost.recommendations(calm.copy(totalMemBytes = 6_000_000_000L)).joinToString("\n")
        assertTrue(small.contains("8 GB"))
    }

    @Test
    fun elInformeDiceLoQueMidio() {
        val text = GameBoost.report(calm)
        assertTrue(text.contains("**Memoria:** 5.2 GB libres de 11.8 GB."))
        assertTrue(text.contains("**Temperatura:** normal (nivel de calor 0.30 de 1.00"))
        assertTrue(text.contains("sin contar lo que use en la GPU"))
        assertTrue(text.contains("**Batería:** 80 %, 31.5 °C."))
        assertTrue(text.contains("**Chip:** Mediatek MT6899."))
        assertTrue(text.contains("**Eden:** versión 1.0.0 (dev.eden.eden_emulator)."))
        assertTrue(text.contains("solo mido y recomiendo"))

        val unknown = GameBoost.report(calm.copy(thermalStatus = 0, thermalHeadroom = Float.NaN, chip = ""))
        assertTrue(unknown.contains("no informa su estado térmico"))
        assertFalse(unknown.contains("**Chip:**"))
    }

    @Test
    fun confianzaDeUnPerfil() {
        fun played(eden: String = "1.0.0", ari: String = "0.68.0", device: String = "POCO", crashed: Boolean = false) =
            PlayedSession("Zelda", eden, ari, device, crashed, measured = true, reported = true)

        assertEquals(Confidence.LOW, GameBoost.confidence(emptyList(), "Zelda", "1.0.0", "0.68.0", "POCO"))
        assertEquals(Confidence.LOW, GameBoost.confidence(listOf(played()), "Zelda", "1.0.0", "0.68.0", "POCO"))
        assertEquals(Confidence.MEDIUM, GameBoost.confidence(List(2) { played() }, "Zelda", "1.0.0", "0.68.0", "POCO"))
        assertEquals(Confidence.HIGH, GameBoost.confidence(List(4) { played() }, "Zelda", "1.0.0", "0.68.0", "POCO"))
        // Un cierre la deja en baja.
        assertEquals(Confidence.LOW, GameBoost.confidence(List(3) { played() } + played(crashed = true), "Zelda", "1.0.0", "0.68.0", "POCO"))
        // Con otra versión de Eden se empieza de nuevo.
        assertEquals(Confidence.LOW, GameBoost.confidence(List(4) { played() }, "Zelda", "1.1.0", "0.68.0", "POCO"))
        // Un cierre con una versión vieja de Eden ya no cuenta.
        assertEquals(
            Confidence.MEDIUM,
            GameBoost.confidence(listOf(played(crashed = true)) + List(2) { played(eden = "1.1.0") }, "Zelda", "1.1.0", "0.68.0", "POCO")
        )
        // Otra versión de ARI baja un nivel.
        assertEquals(Confidence.MEDIUM, GameBoost.confidence(List(4) { played() }, "Zelda", "1.0.0", "0.69.0", "POCO"))
        // Las sesiones de otro teléfono no cuentan.
        assertEquals(Confidence.LOW, GameBoost.confidence(List(4) { played(device = "Otro") }, "Zelda", "1.0.0", "0.68.0", "POCO"))
    }

    @Test
    fun sesionesQueQuedaronAbiertas() {
        assertEquals(listOf(100L, 200L), GameBoost.orphans(listOf(100L, 200L, 900L), processStart = 500L))
        assertTrue(GameBoost.orphans(emptyList(), 500L).isEmpty())
    }

    @Test
    fun elAgenteEntiendeCuandoVasAJugar() {
        val agent = GameBoostAgent { calm }
        listOf(
            "Voy a jugar", "prepara Eden", "¿Cómo está el teléfono para jugar?", "¿Cómo está el celular para jugar?",
            "game boost", "Ari, modo juego", "Quiero jugar en Eden"
        )
            .forEach { assertNotNull(it, agent.match(it, normalizeForAgents(it))) }
        listOf("voy a jugar futbol con mi hijo", "vamos a jugar", "quiero jugar", "abre eden", "qué hora es", "prepara la cena")
            .forEach { assertNull(it, agent.match(it, normalizeForAgents(it))) }

        val result = agent.execute(agent.match("voy a jugar", "voy a jugar")!!)
        assertTrue(result.success)
        assertTrue(result.reply.contains("Game Boost"))
    }
}
