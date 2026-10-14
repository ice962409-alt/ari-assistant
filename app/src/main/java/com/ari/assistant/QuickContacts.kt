package com.ari.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contactos de recados: las personas a las que les mandas recados entre Ari, a mano en la
 * burbuja. La Ari para otros guarda máximo 4; la Ari CEO no tiene límite.
 */
data class QuickContact(val name: String, val number: String)

/** Las reglas, sin Android, para poder probarlas. */
object QuickContactRules {
    const val LIMIT_OTHERS = 4

    sealed class Check {
        /** Ya está guardado: el recado sale directo. */
        object Saved : Check()
        /** No está y hay lugar: Ari pregunta si lo guarda. */
        data class AskToSave(val left: Int?) : Check()
        /** No está y ya no hay lugar (solo en la Ari para otros). */
        data class Full(val saved: List<QuickContact>) : Check()
    }

    fun check(number: String, saved: List<QuickContact>, limit: Int?): Check = when {
        saved.any { same(it.number, number) } -> Check.Saved
        limit != null && saved.size >= limit -> Check.Full(saved)
        else -> Check.AskToSave(limit?.let { it - saved.size })
    }

    /** El mismo número aunque uno tenga lada o espacios (se comparan los últimos 10 dígitos). */
    fun same(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }.takeLast(10)
        val y = b.filter { it.isDigit() }.takeLast(10)
        return x.isNotEmpty() && x == y
    }
}

object QuickContacts {
    private const val PREFS = "ari_recados_contactos"
    private const val KEY = "lista"

    /** null: sin límite (Ari CEO). */
    fun limit(context: Context): Int? =
        if (AriPremium.has(context, AriPremium.Feature.UNLIMITED_RECADO_CONTACTS)) null else QuickContactRules.LIMIT_OTHERS

    fun all(context: Context): List<QuickContact> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).map { array.getJSONObject(it).let { o -> QuickContact(o.getString("nombre"), o.getString("numero")) } }
    }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<QuickContact>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("nombre", it.name).put("numero", it.number)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    /** false si ya no hay lugar. */
    fun add(context: Context, contact: QuickContact): Boolean {
        val list = all(context)
        if (list.any { QuickContactRules.same(it.number, contact.number) }) return true
        val max = limit(context)
        if (max != null && list.size >= max) return false
        save(context, list + contact)
        return true
    }

    fun remove(context: Context, contact: QuickContact) {
        save(context, all(context).filterNot { QuickContactRules.same(it.number, contact.number) })
    }
}
