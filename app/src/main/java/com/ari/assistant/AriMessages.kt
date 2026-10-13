package com.ari.assistant

/**
 * Mensajes entre Ari, por SMS y sin internet: "dile a mi mamá que llego a las 8".
 * Tu Ari prepara un SMS con la marca "[Ari de Javier]"; tu mamá se lo comparte (o lo pega)
 * a su Ari y ella lo reconoce y se lo dice. Si no tiene Ari, le llega como un SMS normal.
 * (Sin Android, para probarlo.)
 */
object AriMessages {

    /** Lo más largo que manda Ari en un recado (unos 2 SMS). */
    const val MAX_TEXT = 280

    data class Request(val recipient: String, val text: String)
    data class Incoming(val claimedSender: String?, val text: String)

    private val ASK = Regex(
        "^(?:ari[,:]?\\s+)?(?:d[ií]le|d[ií]gale|av[ií]sale|m[aá]ndale\\s+(?:a\\s+)?decir|manda\\s+a\\s+decir|p[aá]sale\\s+el\\s+recado)" +
            "\\s+a\\s+(.+?)\\s+que\\s+(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** "Dile a mi mamá que llego a las 8" → (mamá, "llego a las 8"). */
    fun request(text: String): Request? {
        val match = ASK.find(text.trim()) ?: return null
        val recipient = match.groupValues[1].trim()
            .replace(Regex("^(?:mi|mis|la|el)\\s+", RegexOption.IGNORE_CASE), "")
            .trim(' ', ',')
        val message = match.groupValues[2].trim().trimEnd('.')
        if (recipient.isEmpty() || message.isEmpty() || recipient.length > 40) return null
        return Request(recipient, message)
    }

    /** El SMS que se manda: "[Ari de Javier] llego a las 8". */
    fun encode(senderName: String, text: String): String {
        val name = senderName.trim().replace(Regex("[\\[\\]]"), "").take(30)
        val tag = if (name.isEmpty()) "[Ari]" else "[Ari de $name]"
        return "$tag ${text.trim().take(MAX_TEXT)}"
    }

    private val TAG = Regex("^\\[Ari(?: de ([^\\]]{1,30}))?\\]\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)

    /** Un SMS de otra Ari, o null si es un SMS normal (esos Ari no los toca). */
    fun decode(body: String): Incoming? {
        val match = TAG.find(body.trim()) ?: return null
        val text = match.groupValues[2].trim().take(MAX_TEXT + 20)
        if (text.isEmpty()) return null
        return Incoming(match.groupValues[1].trim().ifEmpty { null }, text)
    }

    /** Un recado que te compartieron o pegaste: Ari no sabe de qué número vino, así que lo aclara. */
    fun announceShared(incoming: Incoming): String =
        if (incoming.claimedSender != null) "Recado de la Ari de ${incoming.claimedSender}: ${incoming.text}"
        else "Recado de otra Ari: ${incoming.text}"

    /**
     * Cómo lo dice la Ari que recibe. El nombre que sale es el de tus contactos (el SMS
     * podría decir cualquier nombre); si el número no está en tus contactos, se dice.
     */
    fun announce(contactName: String?, number: String, incoming: Incoming): String = when {
        contactName != null -> "$contactName te manda decir: ${incoming.text}"
        incoming.claimedSender != null ->
            "Un número que no está en tus contactos ($number), que dice ser ${incoming.claimedSender}, te manda decir: ${incoming.text}"
        else -> "El número $number te manda decir: ${incoming.text}"
    }
}
