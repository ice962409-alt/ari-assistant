package com.ari.assistant.agents

import java.util.Locale

/**
 * Game Boost (solo la ARI del dueño): antes de jugar en un emulador como Eden, ARI mide el
 * teléfono y recomienda qué hacer. No acelera nada: Android no deja que una app mejore la
 * memoria, la temperatura ni la batería de otra. Solo mide, explica y recomienda.
 * Diseño completo en docs/ARI_GAME_BOOST_DISENO.md.
 *
 * Aquí está la parte sin Android (se prueba en la computadora): leer bien la temperatura,
 * armar las recomendaciones, la confianza de un perfil y las sesiones que quedaron abiertas.
 */

/** Eden encontrado en el teléfono. [known]: es uno de los paquetes conocidos de Eden. */
data class EdenApp(val packageName: String, val version: String, val known: Boolean)

/** Una medición del teléfono. Lo que no se pudo leer va en null o en 0. */
data class GameSnapshot(
    val availMemBytes: Long,
    val totalMemBytes: Long,
    val lowMemory: Boolean,
    /** Lo que ocupa ARI misma según Android (PSS). No cuenta lo que Gemma use en la GPU. */
    val ariMemBytes: Long,
    val gemmaLoaded: Boolean,
    /** Gemma se está cargando: todavía no ocupa toda su memoria, pero la va a ocupar. */
    val gemmaLoading: Boolean = false,
    /** PowerManager.THERMAL_STATUS_* (0 = sin calentamiento), o null antes de Android 10. */
    val thermalStatus: Int?,
    /** Margen térmico: 1.0 = el teléfono ya se frena por calor. null antes de Android 11; puede ser NaN. */
    val thermalHeadroom: Float?,
    val batteryLevel: Int?,
    /** Temperatura de la batería en décimas de grado (como la da Android). */
    val batteryTenthsC: Int?,
    val charging: Boolean,
    val powerSave: Boolean,
    /** El chip del teléfono ("Mediatek MT6899"), o vacío si Android no lo dice. */
    val chip: String,
    val eden: EdenApp?
)

/** Lo que el agente necesita del teléfono: la base de datos en la app; una medición fija en las pruebas. */
fun interface GameBoostReader {
    fun read(): GameSnapshot
}

object GameBoost {

    /** Los paquetes de Eden conocidos, del oficial al más viejo. No se incluye la variante que se hace pasar por un juego. */
    val EDEN_PACKAGES = listOf("dev.eden.eden_emulator", "dev.eden.eden_emulator.nightly", "dev.legacy.eden_emulator")

    /** Paquetes que se llaman "Eden" pero no lo son (la variante "Optimized" se hace pasar por Genshin Impact). */
    private val NOT_EDEN = setOf("com.miHoYo.Yuanshen")

    /** "Eden" como palabra suelta: "Edenred" o "Credencial" no cuentan. */
    private val EDEN_WORD = Regex("(^|[^a-z0-9])eden([^a-z0-9]|$)")

    /** Eden pide al menos 8 GB de memoria (su documentación). */
    private const val EDEN_MIN_RAM = 8_000_000_000L

    /** Menos que esto libre ya es poca memoria para jugar. */
    private const val LOW_FREE_RAM = 3_000_000_000L

    enum class Heat(val label: String) {
        NORMAL("normal"),
        WARM("tibio"),
        HOT("caliente"),
        VERY_HOT("muy caliente: el teléfono ya baja su velocidad"),
        CRITICAL("crítico: el teléfono se está protegiendo del calor")
    }

    sealed class Thermal {
        /** El teléfono no da datos térmicos fiables: ARI no inventa valores. */
        object Unavailable : Thermal()
        data class Known(val heat: Heat, val headroom: Float?) : Thermal()
    }

    /**
     * Lee el estado térmico. Si no hay margen térmico válido (null, NaN o 0) y el estado es
     * "sin calentamiento", no se puede distinguir "fresco" de "no informa": se dice que no hay datos.
     */
    fun thermal(status: Int?, headroom: Float?): Thermal {
        val margin = headroom?.takeIf { !it.isNaN() && it > 0f }
        val byStatus = when {
            status == null || status <= 0 -> null
            status == 1 -> Heat.WARM
            status == 2 -> Heat.HOT
            status == 3 -> Heat.VERY_HOT
            else -> Heat.CRITICAL
        }
        val byMargin = when {
            margin == null -> null
            margin >= 1.0f -> Heat.VERY_HOT
            margin >= 0.85f -> Heat.HOT
            margin >= 0.65f -> Heat.WARM
            else -> Heat.NORMAL
        }
        if (byStatus == null && byMargin == null) return Thermal.Unavailable
        val heat = listOfNotNull(byStatus, byMargin).maxByOrNull { it.ordinal } ?: Heat.NORMAL
        return Thermal.Known(heat, margin)
    }

    /** Grados de la batería, o null si la lectura no tiene sentido. */
    fun batteryCelsius(tenths: Int?): Float? =
        tenths?.let { it / 10f }?.takeIf { it in -20f..80f }

    /** El primer Eden conocido que esté instalado; si no hay, una app cuyo nombre diga "Eden". */
    fun pickEden(known: Map<String, String>, launcherApps: List<Triple<String, String, String>>): EdenApp? {
        EDEN_PACKAGES.firstOrNull { it in known }?.let { return EdenApp(it, known.getValue(it), known = true) }
        return launcherApps
            .firstOrNull { (label, pkg, _) -> EDEN_WORD.containsMatchIn(normalizeForAgents(label)) && pkg !in NOT_EDEN }
            ?.let { (_, pkg, version) -> EdenApp(pkg, version, known = false) }
    }

    /** Qué conviene hacer antes de jugar, de lo más importante a lo menos. ARI no cambia nada: las aplicas tú. */
    fun recommendations(snapshot: GameSnapshot): List<String> {
        val tips = mutableListOf<String>()
        val reading = thermal(snapshot.thermalStatus, snapshot.thermalHeadroom)
        val heat = (reading as? Thermal.Known)?.heat
        if (reading is Thermal.Unavailable) {
            tips += "No sé la temperatura del teléfono porque no la informa: si lo sientes caliente, haz una pausa."
        } else if (heat != null && heat >= Heat.VERY_HOT) {
            tips += "Haz una pausa antes de jugar: el teléfono ya está muy caliente y se va a frenar."
        } else if (heat == Heat.HOT) {
            tips += "Está caliente: baja la resolución o el límite de velocidad en Eden, usa el modo portátil y quita la funda."
        }
        batteryCelsius(snapshot.batteryTenthsC)?.takeIf { it >= 40f }?.let {
            tips += "La batería está a ${celsius(it)}: espera a que se enfríe o juega en un lugar ventilado."
        }
        if (snapshot.charging) tips += "Mejor no juegues mientras carga: el teléfono se calienta más."
        if (snapshot.powerSave) tips += "El ahorro de batería está activado y baja el rendimiento. Si quieres jugar mejor, apágalo en Ajustes del teléfono."
        val lowFree = snapshot.lowMemory || (snapshot.availMemBytes in 1 until LOW_FREE_RAM)
        if (lowFree) tips += "Hay poca memoria libre: cierra las apps que no estés usando antes de abrir Eden."
        if (snapshot.totalMemBytes <= 0) tips += "No pude leer la memoria del teléfono."
        if (snapshot.gemmaLoaded) {
            tips += "Gemma está cargada y ocupa memoria. Más adelante podré soltarla antes de jugar, con tu permiso."
        } else if (snapshot.gemmaLoading) {
            tips += "Gemma se está cargando y va a ocupar memoria. Más adelante podré dejarla sin cargar mientras juegas, con tu permiso."
        }
        if (snapshot.totalMemBytes in 1 until EDEN_MIN_RAM) {
            tips += "Eden pide al menos 8 GB de memoria y este teléfono tiene ${gb(snapshot.totalMemBytes)} GB: algunos juegos pueden ir lentos."
        }
        if (snapshot.eden == null) {
            tips += "No encontré Eden con los nombres que conozco. Si lo tienes, revisa en Ajustes del teléfono → Apps cómo se llama y díselo a Claude para que lo agregue."
        } else if (!snapshot.eden.known) {
            tips += "Encontré una app llamada Eden con un paquete que no conozco (${snapshot.eden.packageName}): puede no ser el Eden oficial."
        }
        if (tips.isEmpty()) tips += "Con lo que pude medir, todo se ve bien para jugar."
        return tips
    }

    /** El informe completo, con formato para el chat. */
    fun report(snapshot: GameSnapshot): String = buildString {
        append("**Game Boost: cómo está el teléfono para jugar**\n\n")
        val memory = if (snapshot.totalMemBytes > 0) {
            "${gb(snapshot.availMemBytes)} GB libres de ${gb(snapshot.totalMemBytes)} GB" +
                if (snapshot.lowMemory) " (Android avisa que queda poca)" else ""
        } else "no la pude leer"
        append("- **Memoria:** ").append(memory).append(".\n")
        if (snapshot.ariMemBytes > 0) {
            append("- **ARI:** unos ${gb(snapshot.ariMemBytes)} GB según Android (sin contar lo que use en la GPU)")
            append(
                when {
                    snapshot.gemmaLoaded -> ", con Gemma cargada.\n"
                    snapshot.gemmaLoading -> "; Gemma se está cargando.\n"
                    else -> "; Gemma no está cargada.\n"
                }
            )
        }
        val heat = when (val t = thermal(snapshot.thermalStatus, snapshot.thermalHeadroom)) {
            Thermal.Unavailable -> "el teléfono no informa su estado térmico, así que no lo invento (abajo va la temperatura de la batería)"
            is Thermal.Known -> t.heat.label + (t.headroom?.let { " (nivel de calor ${decimal(it, 2)} de 1.00; en 1.00 el teléfono se frena fuerte)" } ?: "")
        }
        append("- **Temperatura:** ").append(heat).append(".\n")
        val battery = listOfNotNull(
            snapshot.batteryLevel?.let { "$it %" },
            batteryCelsius(snapshot.batteryTenthsC)?.let { celsius(it) },
            "cargando".takeIf { snapshot.charging },
            "ahorro de batería activado".takeIf { snapshot.powerSave }
        )
        append("- **Batería:** ").append(if (battery.isEmpty()) "no la pude leer" else battery.joinToString(", ")).append(".\n")
        if (snapshot.chip.isNotBlank()) append("- **Chip:** ").append(snapshot.chip).append(".\n")
        val eden = snapshot.eden?.let { "versión ${it.version.ifBlank { "desconocida" }} (${it.packageName})" } ?: "no lo encontré"
        append("- **Eden:** ").append(eden).append(".\n\n")
        append("**Recomendaciones** (las aplicas tú; yo no cambio nada):\n")
        recommendations(snapshot).forEach { append("- ").append(it).append("\n") }
        append("\nNo puedo acelerar el teléfono ni a Eden: solo mido y recomiendo.")
    }

    // --- Para las siguientes etapas (perfiles e historial) ---

    enum class Confidence(val label: String) { LOW("baja"), MEDIUM("media"), HIGH("alta") }

    /** Una sesión jugada, para calcular la confianza de un perfil. */
    data class PlayedSession(
        val game: String,
        val edenVersion: String,
        val ariVersion: String,
        val device: String,
        val crashed: Boolean,
        val measured: Boolean,
        val reported: Boolean
    )

    /**
     * Qué tanto confiar en las recomendaciones de un juego (diseño, sección 4.3). Solo cuentan
     * las sesiones del mismo juego, el mismo teléfono y la misma versión de Eden: con otra
     * versión de Eden se empieza de nuevo. Baja sin sesiones o con algún cierre; media con 2
     * o más sin cierres; alta con 4 o más, todas medidas y con resultado reportado. Si la
     * última fue con otra versión de ARI, baja un nivel.
     */
    fun confidence(sessions: List<PlayedSession>, game: String, edenVersion: String, ariVersion: String, device: String): Confidence {
        val same = sessions.filter { it.game == game && it.device == device && it.edenVersion == edenVersion }
        if (same.isEmpty() || same.any { it.crashed }) return Confidence.LOW
        val base = when {
            same.size >= 4 && same.all { it.measured && it.reported } -> Confidence.HIGH
            same.size >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val ariChanged = same.last().ariVersion != ariVersion
        return if (ariChanged && base != Confidence.LOW) Confidence.entries[base.ordinal - 1] else base
    }

    /**
     * Sesiones que siguen "en curso" pero empezaron antes de que arrancara esta ARI: ARI se
     * cerró (o Android la cerró) mientras se jugaba. Se le pregunta al usuario cómo le fue.
     */
    fun orphans(openSessionStarts: List<Long>, processStart: Long): List<Long> =
        openSessionStarts.filter { it < processStart }

    private fun gb(bytes: Long): String = decimal(bytes / 1_000_000_000.0, 1)

    private fun celsius(value: Float): String = decimal(value.toDouble(), 1) + " °C"

    private fun decimal(value: Number, places: Int): String =
        String.format(Locale.US, "%.${places}f", value.toDouble())
}

/** Responde en el chat cómo está el teléfono para jugar. No necesita a Gemma. */
class GameBoostAgent(private val reader: GameBoostReader) : Agent {

    override val id = "juego"
    override val name = "Game Boost"
    override val description = "Revisa si el teléfono está listo para jugar en Eden y recomienda qué hacer. Solo mide y recomienda."
    override val examples = listOf("voy a jugar", "prepara Eden", "game boost")

    override fun match(message: String, normalized: String): AgentTask? =
        if (PATTERN.matches(normalized)) AgentTask(message, ACTION_STATUS) else null

    override fun execute(task: AgentTask): AgentResult =
        if (task.action == ACTION_STATUS) AgentResult(GameBoost.report(reader.read()))
        else AgentResult("Acción desconocida: ${task.action}", success = false)

    companion object {
        private const val ACTION_STATUS = "estado"

        // Se compara con el texto normalizado (sin acentos ni signos al principio o al final).
        private val PATTERN = Regex(
            "^(?:game ?boost|modo juego|(?:activa|abre|usa)(?:r)? (?:el )?(?:game ?boost|modo juego)|" +
                "voy a jugar|(?:voy|vamos) a jugar en eden|quiero jugar en eden|" +
                "prepara(?:r)? (?:eden|el (?:telefono|celular|cel) para jugar|todo para jugar)|" +
                "como esta el (?:telefono|celular|cel) para jugar|estado para jugar)$"
        )
    }
}
