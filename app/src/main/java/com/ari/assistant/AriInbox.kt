package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Solo en la Ari CEO: tus notificaciones recientes (WhatsApp, correo, bancos…), para
 * resumírtelas y contestar mensajes desde la notificación ("respóndele a Juan que sí").
 *
 * - Se guardan solo en la memoria mientras Ari está abierta (nunca en disco ni en el respaldo)
 *   y se olvidan a las 12 horas.
 * - Contestar siempre pasa por tu "sí".
 * La lógica no usa Android, para poder probarla; [AriRecadosListener] la llena.
 */
object AriInbox {

    const val MAX_ENTRIES = 60
    const val KEEP_MS = 12 * 60 * 60 * 1000L

    /** Una notificación: de qué app, de quién, qué dice y si se puede contestar desde ahí. */
    data class Entry(
        val key: String,
        val app: String,
        val title: String,
        val text: String,
        val at: Long,
        val canReply: Boolean
    )

    sealed class Request {
        object Summary : Request()
        data class Reply(val name: String, val text: String) : Request()
    }

    /** Quien sabe contestar (el servicio de notificaciones); null si no está conectado. */
    interface Replier {
        fun reply(key: String, text: String): Boolean
    }

    @Volatile
    var replier: Replier? = null

    private val entries = ArrayList<Entry>()

    @Synchronized
    fun add(entry: Entry) {
        entries.removeAll { it.key == entry.key }
        entries += entry
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    @Synchronized
    fun recent(now: Long): List<Entry> {
        entries.removeAll { now - it.at > KEEP_MS }
        return entries.toList()
    }

    @Synchronized
    fun clear() = entries.clear()

    private val SUMMARY = Regex(
        "^(?:que|cuales) notificaciones tengo$|^(?:leeme|lee|resume|resumeme|dime) (?:mis |las )?notificaciones$" +
            "|^que (?:me )?(?:llego|ha llegado|me ha llegado)$|^(?:tengo|hay) (?:mensajes|notificaciones) (?:nuevos|nuevas)$" +
            "|^quien me (?:escribio|ha escrito|mando mensaje)$|^que hay de nuevo$|^que me perdi$"
    )
    private val REPLY = Regex("^(?:respondele|contestale|respondale|contestele|responde a|contesta a)\\s+(?:a\\s+)?(.+?)\\s+(?:que|diciendo que|diciendo)\\s+(.+)$")

    fun parse(text: String): Request? {
        val normalized = normalizeForAgents(text)
        if (SUMMARY.matches(normalized)) return Request.Summary
        val match = REPLY.matchEntire(normalized) ?: return null
        val name = match.groupValues[1].trim()
        if (name.isEmpty() || name.split(' ').size > 4) return null
        // El texto de la respuesta, como lo dijiste (con mayúsculas y acentos).
        val original = text.trim()
        val cut = Regex("(?i)\\s(?:que|diciendo que|diciendo)\\s").find(original)
        val reply = if (cut != null) original.substring(cut.range.last + 1).trim() else match.groupValues[2]
        return if (reply.isEmpty()) null else Request.Reply(name, reply)
    }

    /** "WhatsApp: Juan (2): llego tarde. Gmail: Banco: tu estado de cuenta." Lo más nuevo primero. */
    fun summarize(entries: List<Entry>, maxChars: Int = 900): String {
        if (entries.isEmpty()) return "No tienes notificaciones nuevas desde que me abriste."
        val byApp = LinkedHashMap<String, MutableList<Entry>>()
        entries.sortedByDescending { it.at }.forEach { byApp.getOrPut(it.app) { mutableListOf() } += it }
        val out = StringBuilder()
        for ((app, list) in byApp) {
            val people = LinkedHashMap<String, MutableList<String>>()
            list.forEach { people.getOrPut(it.title) { mutableListOf() } += it.text }
            val parts = people.map { (who, texts) ->
                val count = if (texts.size > 1) " (${texts.size})" else ""
                "$who$count: ${texts.first().take(90)}"
            }
            val line = "$app — " + parts.joinToString("; ")
            if (out.length + line.length > maxChars) {
                out.append("\n…y más.")
                break
            }
            if (out.isNotEmpty()) out.append("\n")
            out.append(line)
        }
        return out.toString()
    }

    /** La notificación más nueva de [name] que se puede contestar. */
    fun findReplyTarget(entries: List<Entry>, name: String): Entry? =
        entries.filter { it.canReply }
            .map { it to AppControl.matchScore(it.title, name) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.at })
            ?.first
}
