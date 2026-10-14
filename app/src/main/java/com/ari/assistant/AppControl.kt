package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Solo en la Ari CEO: Ari maneja tus apps por voz con el permiso de Accesibilidad.
 *
 * - "toca Enviar", "dale a Siguiente": toca el botón que dice eso en la app abierta.
 * - "teclea hola, ya voy" / "escribe aquí …": escribe en el campo de texto.
 * - "desliza hacia abajo", "baja la pantalla": mueve la pantalla.
 * - "atrás", "ve al inicio", "apps recientes", "abre las notificaciones".
 * - "lee mi pantalla": te dice lo que hay escrito en la app abierta.
 *
 * Antes de tocar algo delicado (enviar, pagar, comprar, borrar…) te pregunta.
 * Quien hace el trabajo es el servicio de Accesibilidad (src/completa), que se
 * registra en [controller]; en la Ari para otros no hay servicio y esto no hace nada.
 * La lógica de las frases no usa Android, para poder probarla.
 */
object AppControl {

    enum class Direction { UP, DOWN, LEFT, RIGHT }
    enum class GlobalAction { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT }

    sealed class Command {
        data class Tap(val target: String) : Command()
        data class Type(val text: String) : Command()
        /** Hacia dónde se mueve lo que ves: DOWN = ver lo que sigue abajo. */
        data class Scroll(val direction: Direction) : Command()
        data class Global(val action: GlobalAction) : Command()
        object ReadScreen : Command()
    }

    data class Result(val ok: Boolean, val message: String)

    /** Lo que sabe hacer el servicio de Accesibilidad. */
    interface Controller {
        fun perform(command: Command): Result
    }

    @Volatile
    var controller: Controller? = null

    private val TAP = Regex(
        "^(?:toca|tocale|toque|presiona|presionale|aprieta|aprietale|pica|picale|selecciona|dale clic a|dale click a|dale a|dale en|haz clic en|haz click en|pulsa)" +
            "\\s+(?:en\\s+)?(?:el boton (?:de |que dice )?|la opcion (?:de )?|el icono (?:de )?|donde dice |lo que dice |el |la |los |las )?(.+)$"
    )
    private val TYPE = Regex("^(?:teclea|escribe (?:aqui|ahi|en la pantalla|en el campo|en la caja))[:,]?\\s+(.+)$")
    private val SCROLL = Regex(
        "^(?:desliza|deslizate|desplaza|desplazate|mueve la pantalla|baja la pantalla|sube la pantalla|scroll)" +
            "(?:\\s+(?:hacia\\s+|para\\s+)?(arriba|abajo|a la izquierda|a la derecha|izquierda|derecha))?$"
    )
    private val BACK = Regex("^(?:atras|regresa|vuelve atras|ve atras|ve para atras|regresate|regresa atras)$")
    private val HOME = Regex("^(?:inicio|ve al inicio|vete al inicio|pantalla de inicio|ve a la pantalla de inicio|sal de la app|salte de la app)$")
    private val RECENTS = Regex("^(?:recientes|apps recientes|aplicaciones recientes|muestra las apps abiertas|apps abiertas)$")
    private val NOTIFICATIONS = Regex("^(?:abre |muestra |muestrame |baja )?(?:las |mis )?notificaciones$")
    private val QUICK = Regex("^(?:abre |muestra |muestrame |baja )?(?:los )?ajustes rapidos$")
    private val LOCK = Regex("^(?:bloquea|apaga)\\s+(?:la\\s+pantalla|el\\s+telefono|el\\s+celular)$")
    private val SCREENSHOT = Regex("^(?:toma|tomame|haz|hazme|saca|sacame)\\s+(?:una\\s+)?captura(?:\\s+de\\s+pantalla)?$|^captura\\s+(?:la|mi)\\s+pantalla$|^screenshot$")
    private val READ = Regex("^(?:lee|leeme) (?:lo que dice |lo que hay en )?(?:la|mi) pantalla$|^que (?:dice|hay en) (?:la|mi) pantalla$")

    /** Lo que no es tocar un botón: "toca música de…" es para poner música. */
    private val NOT_TAP_TARGETS = listOf("musica", "canciones", "cancion", "la cancion", "el album", "algo", "una cancion")

    /** Botones delicados: Ari pregunta antes de tocarlos. */
    private val SENSITIVE = listOf(
        "enviar", "envia", "send", "mandar", "manda", "publicar", "publica", "post", "compartir",
        "pagar", "paga", "pago", "comprar", "compra", "buy", "pay", "checkout", "ordenar", "pedir", "realizar pedido",
        "confirmar", "confirma", "aceptar", "acepto", "transferir", "transfiere", "suscribir", "suscribete",
        "borrar", "borra", "eliminar", "elimina", "delete", "remove", "quitar", "desinstalar", "vaciar",
        "llamar", "llama", "call", "instalar", "permitir", "allow", "cerrar sesion", "salir de la cuenta"
    )

    /** Qué pediste. null si no es una orden para manejar apps. */
    fun parse(text: String): Command? {
        val normalized = normalizeForAgents(text)
        TAP.matchEntire(normalized)?.let { match ->
            val target = match.groupValues[1].trim().trim('"', '“', '”', '\'')
            if (target.isEmpty() || target.split(' ').size > 6) return null
            if (NOT_TAP_TARGETS.any { target == it || target.startsWith("$it ") }) return null
            return Command.Tap(target)
        }
        TYPE.matchEntire(normalized)?.let { match ->
            // Lo que vas a escribir se toma con mayúsculas y acentos, tal como lo dijiste.
            val original = text.trim().replace(Regex("^(?i)ari[,:]?\\s+"), "")
            val cut = Regex("^(?i)(?:teclea|escribe (?:aquí|aqui|ahí|ahi|en la pantalla|en el campo|en la caja))[:,]?\\s+").find(original)
            val value = if (cut != null) original.substring(cut.range.last + 1).trim() else match.groupValues[1]
            return if (value.isEmpty()) null else Command.Type(value)
        }
        SCROLL.matchEntire(normalized)?.let { match ->
            val word = match.groupValues[1]
            val direction = when {
                word.endsWith("izquierda") -> Direction.LEFT
                word.endsWith("derecha") -> Direction.RIGHT
                word == "arriba" || normalized.startsWith("sube") -> Direction.UP
                else -> Direction.DOWN
            }
            return Command.Scroll(direction)
        }
        if (BACK.matches(normalized)) return Command.Global(GlobalAction.BACK)
        if (HOME.matches(normalized)) return Command.Global(GlobalAction.HOME)
        if (RECENTS.matches(normalized)) return Command.Global(GlobalAction.RECENTS)
        if (NOTIFICATIONS.matches(normalized)) return Command.Global(GlobalAction.NOTIFICATIONS)
        if (QUICK.matches(normalized)) return Command.Global(GlobalAction.QUICK_SETTINGS)
        if (LOCK.matches(normalized)) return Command.Global(GlobalAction.LOCK_SCREEN)
        if (SCREENSHOT.matches(normalized)) return Command.Global(GlobalAction.SCREENSHOT)
        if (READ.matches(normalized)) return Command.ReadScreen
        return null
    }

    /** true si tocar [target] puede mandar, pagar o borrar algo: Ari pregunta antes. */
    fun isSensitive(target: String): Boolean {
        val words = simplify(target)
        return SENSITIVE.any { s -> words == s || words.startsWith("$s ") || words.endsWith(" $s") || words.contains(" $s ") }
    }

    fun isYes(text: String) = simplify(normalizeForAgents(text)).let {
        it in setOf("si", "sí", "si tocalo", "tocalo", "dale", "va", "ok", "okay", "claro", "hazlo", "adelante", "confirmo", "si dale", "si hazlo", "si por favor")
    }

    fun isNo(text: String) = simplify(normalizeForAgents(text)).let {
        it in setOf("no", "no gracias", "cancela", "cancelar", "mejor no", "no lo toques", "espera", "detente", "para")
    }

    /**
     * Qué tan bien un texto de la pantalla coincide con lo que pediste tocar:
     * 3 = igual, 2 = empieza igual, 1 = lo contiene, 0 = no.
     */
    fun matchScore(label: String, target: String): Int {
        val a = simplify(label)
        val b = simplify(target)
        if (a.isEmpty() || b.isEmpty()) return 0
        return when {
            a == b -> 3
            a.startsWith("$b ") || a.startsWith(b) && b.length >= 4 -> 2
            a.contains(b) && b.length >= 3 -> 1
            else -> 0
        }
    }

    /** Lo que hay escrito en la pantalla, sin repetir y corto, para decírtelo. */
    fun summarize(texts: List<String>, maxChars: Int = 700): String {
        val seen = mutableSetOf<String>()
        val out = StringBuilder()
        for (raw in texts) {
            val t = raw.replace(Regex("\\s+"), " ").trim()
            if (t.length < 2 || !seen.add(simplify(t))) continue
            if (out.length + t.length + 2 > maxChars) break
            if (out.isNotEmpty()) out.append(". ")
            out.append(t)
        }
        return out.toString()
    }

    fun simplify(text: String) = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
