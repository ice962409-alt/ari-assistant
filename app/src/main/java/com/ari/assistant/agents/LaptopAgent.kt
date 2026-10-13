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

/**
 * Primer subordinado de Ari: un modelo de IA que corre en una laptop de la
 * misma red Wi-Fi (Ollama, LM Studio o cualquier servidor compatible con la
 * API de OpenAI). Ari lo busca por su cuenta en la red, se conecta y le
 * delega preguntas.
 */
class LaptopAgent(
    private val settings: SharedPreferences,
    private val guard: NetworkGuard,
    /** Contexto sobre el usuario (sus recuerdos) que se le pasa al subordinado. */
    private val userContext: () -> String
) : Agent {

    override val id = "laptop"
    override val name = "Modelo de la laptop"
    override val description =
        "Un modelo de IA en tu laptop, en la misma red Wi-Fi. Ari lo busca, se conecta y le delega preguntas."
    override val examples = listOf(
        "laptop, explícame la fotosíntesis",
        "busca la laptop",
        "/laptop"
    )

    /** La laptop se guarda por red: la misma dirección en otra red puede ser otro equipo. */
    fun isConnected(network: NetworkGuard.Network): Boolean = baseUrl(network) != null

    private fun baseUrl(network: NetworkGuard.Network): String? =
        guard.key(network)?.let { settings.getString("$KEY_URL|$it", null) }

    private fun modelName(network: NetworkGuard.Network): String? =
        guard.key(network)?.let { settings.getString("$KEY_MODEL|$it", null) }

    /** Tarea para buscar la laptop sin que el usuario lo pida (al abrir Ari). */
    fun discoveryTask() = AgentTask("búsqueda automática", ACTION_DISCOVER)

    override fun match(message: String, normalized: String): AgentTask? {
        val trimmed = message.trim()

        SET_PATTERN.matchEntire(trimmed)?.let {
            return AgentTask(message, ACTION_SET, it.groupValues[1].trimEnd('/'))
        }
        if (DISCOVER_PATTERN.matches(normalized)) return AgentTask(message, ACTION_DISCOVER)
        if (FORGET_PATTERN.matches(normalized)) return AgentTask(message, ACTION_FORGET)
        if (STATUS_PATTERN.matches(normalized)) return AgentTask(message, ACTION_STATUS)

        ASK_PATTERN.matchEntire(trimmed)?.let {
            val question = it.groupValues[1].trim()
            if (question.isNotEmpty()) return AgentTask(message, ACTION_ASK, question)
        }
        return null
    }

    /** Con /delega laptop, cualquier texto es una pregunta para el modelo de la laptop. */
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
                "No uso el subordinado en ${guard.describe(network)} porque no la has aprobado."
            else -> null
        }
        if (blocked != null) return AgentResult(blocked, success = false)

        return when (task.action) {
            ACTION_DISCOVER -> discover(network)
            ACTION_SET -> connect(network, task.argument)
            ACTION_STATUS -> status(network)
            ACTION_FORGET -> {
                val key = guard.key(network)
                settings.edit().remove("$KEY_URL|$key").remove("$KEY_MODEL|$key").apply()
                AgentResult("Desconecté la laptop en esta red. Di \"busca la laptop\" para volver a conectarla.")
            }
            ACTION_ASK -> ask(network, task.argument)
            else -> AgentResult("Acción desconocida: ${task.action}", success = false)
        }
    }

    private fun status(network: NetworkGuard.Network): AgentResult {
        val url = baseUrl(network) ?: return AgentResult(
            "Todavía no tengo un subordinado en la laptop. Di \"busca la laptop\" o usa /laptop http://IP:PUERTO.",
            success = false
        )
        return if (isReachable(url)) {
            AgentResult(
                "La laptop está conectada en $url (${guard.describe(network)}) con el modelo ${modelName(network) ?: "desconocido"}."
            )
        } else {
            AgentResult(
                "Tengo guardada la laptop en $url, pero no responde. ¿Está encendida y en la misma red Wi-Fi?",
                success = false
            )
        }
    }

    private fun ask(network: NetworkGuard.Network, question: String): AgentResult {
        if (baseUrl(network) == null) {
            // Autonomía: si aún no tiene subordinado en esta red, lo busca antes de preguntar.
            val found = discover(network)
            if (!found.success) return found
        }

        val url = baseUrl(network) ?: return AgentResult("No encontré la laptop.", success = false)
        val model = modelName(network) ?: findModel(url)
            ?: return AgentResult("La laptop responde, pero no tiene ningún modelo cargado.", success = false)

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt()))
            .put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)

        return try {
            val response = request("$url/v1/chat/completions", body.toString(), ASK_TIMEOUT_MS)
            val reply = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            AgentResult(reply.ifEmpty { "La laptop no devolvió respuesta." }, reply.isNotEmpty())
        } catch (e: Exception) {
            AgentResult(
                "No pude hablar con la laptop ($url): ${e.message}. ¿Sigue encendida y en la misma red?",
                success = false
            )
        }
    }

    private fun systemPrompt(): String {
        val context = userContext()
        return buildString {
            append("Eres un subordinado de Ari, el asistente personal del usuario. ")
            append("Ari te delega tareas; respóndelas en español, de forma clara y breve.")
            if (context.isNotBlank()) {
                append("\n\nLo que Ari sabe del usuario:\n")
                append(context)
            }
        }
    }

    private fun connect(network: NetworkGuard.Network, url: String): AgentResult {
        val host = runCatching { URL(url).host }.getOrNull().orEmpty()
        if (!NetworkGuard.isPrivateAddress(host)) {
            return AgentResult(
                "Solo me conecto a direcciones de la red local, como http://192.168.1.50:11434.",
                success = false
            )
        }

        val model = findModel(url) ?: return AgentResult(
            "No encontré un servidor de modelos en $url. Revisa la dirección y que el servidor acepte conexiones de la red.",
            success = false
        )
        save(network, url, model)
        return AgentResult("Conexión lista con la laptop en $url. Mi subordinado es el modelo $model.")
    }

    /** Recorre la red Wi-Fi buscando un servidor de modelos en los puertos conocidos. */
    private fun discover(network: NetworkGuard.Network): AgentResult {
        baseUrl(network)?.let { saved ->
            findModel(saved)?.let { model ->
                save(network, saved, model)
                return AgentResult("La laptop ya está conectada en $saved con el modelo $model.")
            }
        }

        val candidates = guard.subnets(network).flatMap { prefix ->
            (1..254).flatMap { host -> KNOWN_PORTS.map { port -> "$prefix.$host" to port } }
        }
        if (candidates.isEmpty()) {
            return AgentResult("No encontré dónde buscar en ${guard.describe(network)}.", success = false)
        }

        val pool = Executors.newFixedThreadPool(SCAN_THREADS)
        try {
            val scan = ExecutorCompletionService<String?>(pool)
            candidates.forEach { (host, port) ->
                scan.submit(Callable { if (isPortOpen(host, port)) "http://$host:$port" else null })
            }

            repeat(candidates.size) {
                val url = scan.poll(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)?.get() ?: return@repeat
                val model = findModel(url) ?: return@repeat
                save(network, url, model)
                return AgentResult("¡Encontré mi primer subordinado! Modelo $model en la laptop ($url).")
            }
        } finally {
            pool.shutdownNow()
        }

        return AgentResult(
            "No encontré ningún modelo en la red. En la laptop, abre Ollama o LM Studio y permite conexiones desde la red local.",
            success = false
        )
    }

    private fun save(network: NetworkGuard.Network, url: String, model: String) {
        val key = guard.key(network) ?: return
        settings.edit().putString("$KEY_URL|$key", url).putString("$KEY_MODEL|$key", model).apply()
    }

    private fun isReachable(url: String): Boolean = findModel(url) != null

    /** Devuelve el primer modelo que ofrece el servidor, o null si no es un servidor de modelos. */
    private fun findModel(url: String): String? = try {
        val data = JSONObject(request("$url/v1/models", null, PROBE_TIMEOUT_MS)).getJSONArray("data")
        if (data.length() == 0) null else data.getJSONObject(0).getString("id")
    } catch (e: Exception) {
        null
    }

    private fun isPortOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: Exception) {
        false
    }

    /** GET si [body] es null; si no, POST con JSON. Devuelve el cuerpo de la respuesta. */
    private fun request(url: String, body: String?, readTimeoutMs: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS * 4
            connection.readTimeout = readTimeoutMs
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("respuesta HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ACTION_ASK = "preguntar"
        private const val ACTION_DISCOVER = "buscar"
        private const val ACTION_SET = "configurar"
        private const val ACTION_STATUS = "estado"
        private const val ACTION_FORGET = "desconectar"

        private const val KEY_URL = "laptop_url"
        private const val KEY_MODEL = "laptop_model"

        /** 11434: Ollama. 1234: LM Studio. */
        private val KNOWN_PORTS = listOf(11434, 1234)

        private const val SCAN_THREADS = 64
        private const val CONNECT_TIMEOUT_MS = 400
        private const val SCAN_TIMEOUT_MS = 5_000L
        private const val PROBE_TIMEOUT_MS = 3_000
        private const val ASK_TIMEOUT_MS = 180_000

        private const val PREFIX = "^[¿¡\\s]*(?:ari[,:]?\\s+)?"

        private val SET_PATTERN = Regex("^/laptop\\s+(https?://\\S+)$", RegexOption.IGNORE_CASE)

        // Se comparan con el texto normalizado (sin acentos ni signos).
        private val STATUS_PATTERN = Regex("^(?:/laptop(?: estado)?|estado de (?:la )?laptop)$")
        private val DISCOVER_PATTERN = Regex(
            "^(?:/laptop buscar|busca(?:r)? (?:la )?laptop|conectate (?:a|con) (?:la )?laptop|conecta(?:r)? (?:la )?laptop)$"
        )
        private val FORGET_PATTERN = Regex("^(?:/laptop olvidar|desconecta(?:te de)? (?:la )?laptop)$")

        private val ASK_PATTERN = Regex(
            "$PREFIX(?:/laptop|laptop[,:]|preg[uú]ntale a la laptop|pide a la laptop que|dile a la laptop que)\\s+(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
