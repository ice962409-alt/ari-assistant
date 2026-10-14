package com.ari.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Solo en la ARI del dueño: deja fichas de trabajo ("issues") en el repositorio privado
 * de Ari en GitHub, con sus propuestas de mejora. Claude las ve en cada sesión y las programa.
 *
 * - La llave de GitHub la pega el dueño en ARI (nunca en el chat). Se guarda cifrada con
 *   el Android Keystore. Solo necesita permiso para crear issues en ese repositorio.
 * - ARI no cambia código: solo escribe la ficha.
 */
object AriGitHub {

    const val REPO = "javierhedzd-ops/ari-assistant"
    const val TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

    private const val KEY_ALIAS = "ari_github_key"
    private const val PREFS = "ari_github"
    private const val FIELD = "llave"

    /** Título de la ficha: "[ARI] Mejoras propuestas (0.52.0, 14/10/2026)". */
    fun title(version: String, date: String) = "[ARI] Mejoras propuestas ($version, $date)"

    /** Limpia lo que se pegó: espacios, saltos de línea, comillas. */
    fun cleanToken(raw: String) = raw.trim().trim('"', '\'').replace(Regex("\\s+"), "")

    fun looksLikeToken(token: String) = token.startsWith("github_pat_") && token.length >= 40

    /** Qué significa cada respuesta de GitHub, en palabras. */
    fun explain(code: Int): String = when (code) {
        in 200..299 -> "Listo."
        401 -> "La llave no es válida o ya venció. Crea una nueva y pégala en Ajustes → GitHub de ARI."
        403 -> "A la llave le falta el permiso Issues (lectura y escritura)."
        404 -> "La llave no incluye el repositorio ari-assistant. En GitHub, edítala y agrégalo."
        410 -> "Los issues están apagados en el repositorio."
        422 -> "GitHub no aceptó la ficha."
        else -> "GitHub respondió con el código $code."
    }

    // --- En el teléfono ---

    fun hasToken(context: Context) = prefs(context).contains(FIELD)

    fun saveToken(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs(context).edit().putString(FIELD, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun clearToken(context: Context) {
        prefs(context).edit().remove(FIELD).apply()
    }

    /** La llave guardada (también la usa [AriUpdate] para bajar actualizaciones). */
    fun token(context: Context): String? = loadToken(context)

    private fun loadToken(context: Context): String? {
        val stored = prefs(context).getString(FIELD, null) ?: return null
        return runCatching {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, 12))
            String(cipher.doFinal(sealed, 12, sealed.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Revisa que la llave sirva para el repositorio (sin crear nada). Devuelve el código de GitHub. */
    fun check(context: Context): Int {
        val token = loadToken(context) ?: return 401
        return request("GET", "https://api.github.com/repos/$REPO", token, null).first
    }

    /** Crea la ficha. Devuelve el código de GitHub y, si salió bien, el enlace a la ficha. */
    fun createIssue(context: Context, title: String, body: String): Pair<Int, String?> {
        val token = loadToken(context) ?: return 401 to null
        val json = JSONObject().put("title", title).put("body", body).toString()
        val (code, response) = request("POST", "https://api.github.com/repos/$REPO/issues", token, json)
        val link = if (code in 200..299) runCatching { JSONObject(response).optString("html_url") }.getOrNull() else null
        return code to link
    }

    private fun request(method: String, url: String, token: String, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "ARI")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText().take(20_000) }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
