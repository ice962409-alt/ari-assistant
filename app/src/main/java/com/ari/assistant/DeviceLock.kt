package com.ari.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * La Ari CEO es exclusiva del dueño: solo abre en los teléfonos cuyo código está en
 * `ari_ceo_telefonos` (src/completa/res/values/ceo.xml). Cada teléfono tiene un código
 * propio que Android le da a Ari y que no se puede elegir ni copiar a otro teléfono.
 * La Ari para otros no tiene candado (su lista está vacía en main).
 */
object DeviceLock {

    /** El código de este teléfono (8 letras y números), se ve en Ajustes → Acerca de Ari. */
    @SuppressLint("HardwareIds")
    fun code(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return codeFor(id)
    }

    /** Sin Android, para probarlo. */
    fun codeFor(androidId: String): String =
        MessageDigest.getInstance("SHA-256").digest("ari-ceo:$androidId".toByteArray())
            .take(4).joinToString("") { "%02X".format(it) }

    /**
     * true si esta Ari puede abrir aquí. Mientras la lista esté vacía (antes de registrar
     * el teléfono del dueño), abre en cualquiera para que el dueño pueda ver su código.
     */
    fun allowed(context: Context): Boolean {
        if (!context.resources.getBoolean(R.bool.ari_ceo)) return true
        val phones = context.resources.getStringArray(R.array.ari_ceo_telefonos).map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        return isAllowed(code(context), phones)
    }

    fun isAllowed(code: String, phones: List<String>): Boolean = phones.isEmpty() || code.uppercase() in phones
}
