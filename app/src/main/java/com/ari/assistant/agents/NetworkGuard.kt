package com.ari.assistant.agents

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Decide en qué redes puede Ari hablar con sus subordinados.
 *
 * - El hotspot del propio teléfono siempre está permitido: es una red privada
 *   entre el teléfono y la laptop.
 * - Una red Wi-Fi solo se usa si el usuario la aprobó. Para saber su nombre,
 *   Android exige el permiso de ubicación.
 */
class NetworkGuard(
    private val context: Context,
    private val settings: SharedPreferences
) {

    sealed class Network {
        /** Red compartida desde el teléfono (hotspot o USB). */
        data class Hotspot(val interfaces: List<String>) : Network()

        /** [open]: red sin contraseña; cualquiera puede crear una con el mismo nombre. */
        data class Wifi(val name: String, val interfaceName: String?, val open: Boolean = false) : Network()

        /** Wi-Fi cuyo nombre no se puede leer (sin permiso o con la ubicación apagada). */
        object UnknownWifi : Network()

        object None : Network()
    }

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun current(): Network {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val active = connectivity?.activeNetwork
        val capabilities = active?.let { connectivity.getNetworkCapabilities(it) }

        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            if (!hasLocationPermission()) return Network.UnknownWifi
            val name = wifiName() ?: return Network.UnknownWifi
            val interfaceName = connectivity.getLinkProperties(active)?.interfaceName
            return Network.Wifi(name, interfaceName, isOpenWifi())
        }

        val hotspot = interfaces()
            .filter { it.isUp && HOTSPOT_PREFIXES.any { prefix -> it.name.startsWith(prefix) } }
            .filter { privateSubnetsOf(it).isNotEmpty() }
            .map { it.name }
        return if (hotspot.isNotEmpty()) Network.Hotspot(hotspot) else Network.None
    }

    /**
     * Las redes sin contraseña nunca se aprueban: la aprobación se guarda por
     * nombre, y cualquiera puede crear una red abierta con el nombre de otra.
     */
    fun isApproved(network: Network): Boolean = when (network) {
        is Network.Hotspot -> true
        is Network.Wifi -> !network.open && network.name in approvedNetworks()
        else -> false
    }

    fun approve(network: Network.Wifi) {
        settings.edit().putStringSet(KEY_APPROVED, approvedNetworks() + network.name).apply()
    }

    fun revoke(network: Network.Wifi) {
        settings.edit().putStringSet(KEY_APPROVED, approvedNetworks() - network.name).apply()
    }

    fun approvedNetworks(): Set<String> =
        settings.getStringSet(KEY_APPROVED, emptySet()).orEmpty().toSet()

    /** Clave para guardar datos por red, o null si la red no se puede identificar. */
    fun key(network: Network): String? = when (network) {
        is Network.Hotspot -> "hotspot"
        is Network.Wifi -> "wifi:${network.name}"
        else -> null
    }

    fun describe(network: Network): String = when (network) {
        is Network.Hotspot -> "el hotspot de tu teléfono"
        is Network.Wifi -> "la red Wi-Fi «${network.name}»"
        is Network.UnknownWifi -> "una red Wi-Fi sin identificar"
        is Network.None -> "ninguna red"
    }

    /** Prefijos /24 (por ejemplo "192.168.1") donde buscar subordinados en esta red. */
    fun subnets(network: Network): List<String> {
        val names = when (network) {
            is Network.Hotspot -> network.interfaces
            is Network.Wifi -> listOfNotNull(network.interfaceName)
            else -> emptyList()
        }
        return names
            .mapNotNull { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
            .flatMap { privateSubnetsOf(it) }
            .distinct()
    }

    /** La lista de interfaces puede ser null o fallar en algunos teléfonos. */
    private fun interfaces(): List<NetworkInterface> =
        runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()

    /** true si la Wi-Fi actual no tiene contraseña. Solo se puede saber en Android 12 o más. */
    @Suppress("DEPRECATION")
    private fun isOpenWifi(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        val info = context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
            ?: return false
        return info.currentSecurityType == android.net.wifi.WifiInfo.SECURITY_TYPE_OPEN ||
            info.currentSecurityType == android.net.wifi.WifiInfo.SECURITY_TYPE_OWE
    }

    @Suppress("DEPRECATION")
    private fun wifiName(): String? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val ssid = wifi?.connectionInfo?.ssid?.trim('"') ?: return null
        return ssid.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
    }

    private fun privateSubnetsOf(networkInterface: NetworkInterface): List<String> =
        runCatching { networkInterface.inetAddresses.toList() }.getOrDefault(emptyList())
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress.orEmpty().substringBeforeLast('.') }
            .filter { it.isNotEmpty() }

    companion object {
        private const val KEY_APPROVED = "approved_networks"
        private const val UNKNOWN_SSID = "<unknown ssid>"

        /** Interfaces que Android usa para el hotspot y para compartir por USB o Bluetooth. */
        private val HOTSPOT_PREFIXES = listOf("ap", "swlan", "softap", "wlan1", "rndis", "bt-pan")

        /** true si [host] es una dirección IPv4 privada escrita con números (192.168.x.x, 10.x.x.x, 172.16-31.x.x). */
        fun isPrivateAddress(host: String): Boolean {
            val parts = host.split('.').map { it.toIntOrNull() ?: return false }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            return parts[0] == 10 ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 192 && parts[1] == 168)
        }
    }
}
