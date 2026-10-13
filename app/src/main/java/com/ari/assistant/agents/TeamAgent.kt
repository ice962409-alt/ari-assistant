package com.ari.assistant.agents

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Un modelo de IA de la red que trabaja para Ari. */
data class Subordinate(val url: String, val model: String, val role: Role) {
    val label: String
        get() = "${role.title} ($model)"
}

/**
 * El equipo de Ari: modelos de IA que corren en computadoras o celulares de
 * la misma red, servidos con Ollama, LM Studio, llama.cpp o cualquier
 * servidor compatible con la API de OpenAI.
 *
 * Ari los busca por su cuenta y asigna a cada uno un puesto según su
 * capacidad ([Role.assign]), pero solo trabajan para ella los que el usuario
 * aprueba: cualquier equipo de la red podría hacerse pasar por un servidor de
 * modelos. Los encontrados quedan como candidatos hasta que el usuario decide.
 *
 * A los subordinados solo se les manda la pregunta, nunca los recuerdos del
 * usuario. El equipo se guarda por red: la misma dirección en otra red puede
 * ser otro equipo.
 */
class TeamAgent(
    private val settings: SharedPreferences,
    private val guard: NetworkGuard
) : Agent {

    override val id = "equipo"
    override val name = "Equipo de modelos"
    override val description =
        "Modelos de IA en tus computadoras o celulares de la misma red. Ari los busca, les da un puesto y les delega tareas."
    override val examples = listOf(
        "equipo, explícame qué es una red neuronal",
        "busca modelos",
        "/equipo"
    )

    fun hasTeam(network: NetworkGuard.Network): Boolean = team(network).isNotEmpty()

    fun team(network: NetworkGuard.Network): List<Subordinate> = load(KEY_TEAM, network)

    /** Modelos encontrados en la red que esperan la aprobación del usuario. */
    fun candidates(network: NetworkGuard.Network): List<Subordinate> = load(KEY_CANDIDATES, network)

    /** Contrata a los candidatos elegidos por el usuario y descarta al resto. */
    fun hireCandidates(network: NetworkGuard.Network, chosen: List<Subordinate>) {
        val team = (team(network) + chosen)
            .distinctBy { it.url to it.model }
            .take(MAX_TEAM)
        store(KEY_TEAM, network, team)
        store(KEY_CANDIDATES, network, emptyList())
    }

    fun rejectCandidates(network: NetworkGuard.Network) {
        store(KEY_CANDIDATES, network, emptyList())
    }

    private fun load(prefix: String, network: NetworkGuard.Network): List<Subordinate> {
        val key = guard.key(network) ?: return emptyList()
        val saved = settings.getString("$prefix|$key", null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val role = runCatching { Role.valueOf(item.getString("role")) }.getOrNull()
                    ?: return@mapNotNull null
                Subordinate(item.getString("url"), item.getString("model"), role)
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** Tarea para buscar el equipo sin que el usuario lo pida (al abrir Ari). */
    fun discoveryTask() = AgentTask("búsqueda automática", ACTION_DISCOVER)

    override fun match(message: String, normalized: String): AgentTask? {
        val trimmed = message.trim()

        SET_PATTERN.matchEntire(trimmed)?.let {
            return AgentTask(message, ACTION_ADD, it.groupValues[1].trimEnd('/'))
        }
        if (DISCOVER_PATTERN.matches(normalized)) return AgentTask(message, ACTION_DISCOVER)
        if (FORGET_PATTERN.matches(normalized)) return AgentTask(message, ACTION_FORGET)
        if (STATUS_PATTERN.matches(normalized)) return AgentTask(message, ACTION_STATUS)

        ASK_PATTERN.matchEntire(trimmed)?.let {
            val question = it.groupValues[1].trim()
            if (question.isNotEmpty()) return AgentTask(message, ACTION_ASK, question)
        }

        // Autonomía: si el mensaje es de una especialidad y en esta red hay un
        // modelo con ese puesto, Ari se lo encarga sin que se lo pidan.
        val role = Role.requestedBy(normalized) ?: return null
        val network = guard.current()
        if (!guard.isApproved(network) || team(network).none { it.role == role }) return null
        return AgentTask(message, ACTION_ASK, trimmed, automatic = true)
    }

    /** Con /delega equipo, cualquier texto es una tarea para el equipo. */
    override fun matchDelegated(message: String, normalized: String): AgentTask? =
        match(message, normalized)
            ?: message.trim().takeIf { it.isNotEmpty() }?.let { AgentTask(message, ACTION_ASK, it) }

    override fun execute(task: AgentTask): AgentResult {
        val network = guard.current()
        val blocked = when {
            network is NetworkGuard.Network.None ->
                "El teléfono no está conectado a ninguna red."
            network is NetworkGuard.Network.UnknownWifi ->
                "No puedo reconocer esta red Wi-Fi. Necesito el permiso de ubicación y la ubicación encendida."
            !guard.isApproved(network) ->
                "No trabajo con el equipo en ${guard.describe(network)} porque no la has aprobado."
            else -> null
        }
        if (blocked != null) return AgentResult(blocked, success = false)

        return when (task.action) {
            ACTION_DISCOVER -> discover(network)
            ACTION_ADD -> add(network, task.argument)
            ACTION_STATUS -> status(network)
            ACTION_FORGET -> {
                store(KEY_TEAM, network, emptyList())
                store(KEY_CANDIDATES, network, emptyList())
                AgentResult("Listo, despedí al equipo de esta red. Di \"busca modelos\" para volver a formarlo.")
            }
            ACTION_ASK -> ask(network, task.argument)
            else -> AgentResult("Acción desconocida: ${task.action}", success = false)
        }
    }

    private fun status(network: NetworkGuard.Network): AgentResult {
        val team = team(network)
        if (team.isEmpty()) {
            return AgentResult(
                "Todavía no tengo equipo en ${guard.describe(network)}. Di \"busca modelos\" o usa /equipo http://IP:PUERTO.",
                success = false
            )
        }
        // Una sola consulta por servidor, aunque tenga varios modelos.
        val online = team.map { it.url }.distinct().associateWith { findModels(it) != null }
        val lines = team.joinToString("\n") { member ->
            val state = if (online[member.url] == true) "disponible" else "no responde"
            "• ${member.role.title}: ${member.model}, en ${member.url} ($state)"
        }
        return AgentResult("Mi equipo en ${guard.describe(network)}:\n$lines")
    }

    private fun ask(network: NetworkGuard.Network, question: String): AgentResult {
        if (!hasTeam(network)) {
            // Autonomía: si aún no tiene equipo en esta red, busca candidatos;
            // la pregunta espera a que el usuario apruebe a alguien.
            val found = discover(network)
            if (!found.success) return found
            return AgentResult(
                "Todavía no tengo equipo aquí. ${found.reply} Cuando apruebes a alguien, vuelve a pedírmelo.",
                success = false
            )
        }

        val member = choose(team(network), question)
            ?: return AgentResult("No tengo a nadie en el equipo para esta tarea.", success = false)

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt(member)))
            .put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject()
            .put("model", member.model)
            .put("messages", messages)
            .put("stream", false)

        return try {
            val response = request("${member.url}/v1/chat/completions", body.toString(), ASK_TIMEOUT_MS)
            val reply = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            if (reply.isEmpty()) {
                AgentResult("${member.label} no devolvió respuesta.", success = false)
            } else {
                AgentResult("[${member.label}]\n$reply")
            }
        } catch (e: Throwable) {
            AgentResult(
                "No pude hablar con ${member.label} en ${member.url}: ${e.message}. ¿Sigue encendido y en la misma red?",
                success = false
            )
        }
    }

    /** El puesto especializado que pide la tarea; si no, el mejor generalista. */
    private fun choose(team: List<Subordinate>, question: String): Subordinate? {
        val wanted = Role.requestedBy(normalizeForAgents(question))
        wanted?.let { role -> team.firstOrNull { it.role == role }?.let { return it } }
        return Role.GENERAL_PREFERENCE.firstNotNullOfOrNull { role -> team.firstOrNull { it.role == role } }
    }

    // Sin recuerdos del usuario: un subordinado solo necesita la tarea.
    private fun systemPrompt(member: Subordinate): String =
        "Trabajas para Ari, la CEO de una empresa en formación dedicada a la autonomía de " +
            "modelos de IA locales. Ari te dio el puesto de ${member.role.title}: ${member.role.duty} " +
            "Ari te delega tareas en nombre de su creador; respóndelas en español, " +
            "de forma clara y directa, siempre pensando en los intereses de la empresa."


    private fun add(network: NetworkGuard.Network, url: String): AgentResult {
        val host = runCatching { URL(url).host }.getOrNull().orEmpty()
        if (!NetworkGuard.isPrivateAddress(host)) {
            return AgentResult(
                "Solo me conecto a direcciones de la red local, como http://192.168.1.50:11434.",
                success = false
            )
        }

        val hired = hire(url) ?: return AgentResult(
            "No encontré un servidor de modelos en $url. Revisa la dirección y que el servidor acepte conexiones de la red.",
            success = false
        )
        if (hired.isEmpty()) {
            return AgentResult("El servidor de $url responde, pero no tiene modelos para conversar.", success = false)
        }

        // El usuario escribió esta dirección, así que se contrata directamente.
        val team = (team(network).filter { it.url != url } + hired).take(MAX_TEAM)
        store(KEY_TEAM, network, team)
        return AgentResult(describeHiring(hired))
    }

    /** Recorre la red buscando servidores de modelos y los deja como candidatos. */
    private fun discover(network: NetworkGuard.Network): AgentResult {
        val candidates = guard.subnets(network).flatMap { prefix ->
            (1..254).flatMap { host -> KNOWN_PORTS.map { port -> "$prefix.$host" to port } }
        }
        if (candidates.isEmpty()) {
            return AgentResult("No encontré dónde buscar en ${guard.describe(network)}.", success = false)
        }

        val servers = mutableListOf<String>()
        val pool = Executors.newFixedThreadPool(SCAN_THREADS)
        try {
            val scan = ExecutorCompletionService<String?>(pool)
            candidates.forEach { (host, port) ->
                scan.submit(Callable { if (isPortOpen(host, port)) "http://$host:$port" else null })
            }
            repeat(candidates.size) {
                scan.poll(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)?.get()?.let { servers.add(it) }
            }
        } finally {
            pool.shutdownNow()
        }

        val hired = servers.flatMap { hire(it).orEmpty() }
        if (hired.isEmpty()) {
            return AgentResult(
                "No encontré modelos en la red. Abre Ollama, LM Studio o llama.cpp en la computadora o el celular " +
                    "y permite conexiones desde la red local.",
                success = false
            )
        }

        val known = team(network).map { it.url to it.model }.toSet()
        // El tope se aplica después de quitar a los conocidos, para no perder a los nuevos.
        val fresh = hired.filter { (it.url to it.model) !in known }.take(MAX_TEAM)
        if (fresh.isEmpty()) {
            return AgentResult("No encontré modelos nuevos; tu equipo de esta red sigue igual.")
        }

        store(KEY_CANDIDATES, network, fresh)
        val count = if (fresh.size == 1) "1 modelo" else "${fresh.size} modelos"
        return AgentResult("Encontré $count en la red. Elige a quién contrato.")
    }

    private fun describeHiring(hired: List<Subordinate>): String {
        val lines = hired.joinToString("\n") { "• ${it.role.title}: ${it.model}" }
        val count = if (hired.size == 1) "a 1 modelo" else "a ${hired.size} modelos"
        return "¡Contraté $count para el equipo! Así quedaron los puestos:\n$lines"
    }

    /** Los modelos de conversación de un servidor, con su puesto; null si no es un servidor de modelos. */
    private fun hire(url: String): List<Subordinate>? =
        findModels(url)?.mapNotNull { model -> Role.assign(model)?.let { Subordinate(url, model, it) } }

    private fun findModels(url: String): List<String>? = try {
        val data = JSONObject(request("$url/v1/models", null, PROBE_TIMEOUT_MS)).getJSONArray("data")
        (0 until data.length()).map { data.getJSONObject(it).getString("id") }
    } catch (e: Throwable) {
        null
    }

    private fun store(prefix: String, network: NetworkGuard.Network, members: List<Subordinate>) {
        val key = guard.key(network) ?: return
        val array = JSONArray()
        members.forEach {
            array.put(JSONObject().put("url", it.url).put("model", it.model).put("role", it.role.name))
        }
        settings.edit().putString("$prefix|$key", array.toString()).apply()
    }

    private fun isPortOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: Throwable) {
        false
    }

    /** GET si [body] es null; si no, POST con JSON. Devuelve el cuerpo de la respuesta. */
    private fun request(url: String, body: String?, readTimeoutMs: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS * 4
            connection.readTimeout = readTimeoutMs
            // Un equipo de la red no puede redirigir a Ari fuera de ella.
            connection.instanceFollowRedirects = false
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("respuesta HTTP $code")
            return readLimited(connection)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Lee la respuesta con un tope de tamaño: los equipos de la red no son de
     * confianza, y una respuesta enorme agotaría la memoria y cerraría Ari.
     */
    private fun readLimited(connection: HttpURLConnection): String {
        val buffer = java.io.ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                if (buffer.size() + read > MAX_RESPONSE_BYTES) {
                    throw IllegalStateException("respuesta demasiado grande")
                }
                buffer.write(chunk, 0, read)
            }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val ACTION_ASK = "preguntar"
        private const val ACTION_DISCOVER = "buscar"
        private const val ACTION_ADD = "agregar"
        private const val ACTION_STATUS = "estado"
        private const val ACTION_FORGET = "despedir"

        private const val KEY_TEAM = "team"
        private const val KEY_CANDIDATES = "candidates"
        private const val MAX_TEAM = 12
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        /** 11434: Ollama. 1234: LM Studio. 8080: servidor de llama.cpp. */
        private val KNOWN_PORTS = listOf(11434, 1234, 8080)

        private const val SCAN_THREADS = 64
        private const val CONNECT_TIMEOUT_MS = 400
        private const val SCAN_TIMEOUT_MS = 5_000L
        private const val PROBE_TIMEOUT_MS = 3_000
        private const val ASK_TIMEOUT_MS = 180_000

        private const val PREFIX = "^[¿¡\\s]*(?:ari[,:]?\\s+)?"

        private val SET_PATTERN = Regex("^/(?:equipo|laptop)\\s+(https?://\\S+)$", RegexOption.IGNORE_CASE)

        // Se comparan con el texto normalizado (sin acentos ni signos).
        private val STATUS_PATTERN = Regex(
            "^(?:/equipo(?: estado)?|/laptop(?: estado)?|estado del equipo|quien(?:es)? (?:esta|estan) en (?:el|tu) equipo|tu equipo)$"
        )
        private val DISCOVER_PATTERN = Regex(
            "^(?:/(?:equipo|laptop) buscar|busca(?:r)? (?:modelos|subordinados|(?:al |el )?equipo|(?:la )?laptop)|forma (?:el|tu) equipo|conectate (?:a|con) (?:la )?laptop)$"
        )
        private val FORGET_PATTERN = Regex(
            "^(?:/(?:equipo|laptop) (?:olvidar|despedir)|despide (?:al|el) equipo|desconecta(?:te de)? (?:la laptop|el equipo))$"
        )

        private val ASK_PATTERN = Regex(
            "$PREFIX(?:/equipo|/laptop|equipo[,:]|laptop[,:]|preg[uú]ntale (?:a la laptop|al equipo)|pide (?:a la laptop|al equipo) que|dile (?:a la laptop|al equipo) que)\\s+(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
