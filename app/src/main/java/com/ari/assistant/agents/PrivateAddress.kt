package com.ari.assistant.agents

/** Direcciones de la red de casa (o del hotspot): las únicas con las que Ari habla con su equipo. */
object PrivateAddress {
    /** true si [host] es una dirección IPv4 privada escrita con números (192.168.x.x, 10.x.x.x, 172.16-31.x.x). */
    fun matches(host: String): Boolean {
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }
}
