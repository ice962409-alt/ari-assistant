package com.ari.assistant

import android.content.Context

/**
 * La Ari comercial es gratis con lo básico y tiene funciones Premium.
 * Hoy solo existe la lista y la pantalla "Ari Premium": el cobro llega con Google Play
 * (Play Billing). La ARI del dueño tiene todo. Sin Android en las reglas, para probarlas.
 */
object AriPremium {

    enum class Feature(val title: String) {
        UNLIMITED_RECADO_CONTACTS("Contactos de recados sin límite"),
        TUTOR_TOPICS("Tutora de varios temas a la vez"),
        UNLIMITED_ROUTINES("Rutinas sin límite"),
        WAKE_WORD("Responder a su nombre"),
        COLORS("Todos los colores de Ari"),
    }

    /** Lo que siempre es gratis. */
    val FREE = listOf(
        "Platicar con Ari sin internet",
        "Recordatorios, alarmas y agenda",
        "Llamadas, mensajes y abrir apps",
        "Recados con hasta ${QuickContactRules.LIMIT_OTHERS} contactos",
        "Lecciones para aprender a usarla",
    )

    /** Reglas: la ARI del dueño tiene todo; en la comercial, Premium cuando se compre. */
    fun has(ceo: Boolean, purchased: Boolean): Boolean = ceo || purchased

    fun has(context: Context, @Suppress("UNUSED_PARAMETER") feature: Feature): Boolean =
        has(context.resources.getBoolean(R.bool.ari_ceo), purchased(context))

    /** Todavía no hay compras: llegan con Google Play. */
    fun purchased(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false

    fun description(): String = buildString {
        append("Gratis, siempre:\n")
        FREE.forEach { append("• ").append(it).append("\n") }
        append("\nAri Premium (muy pronto, con Google Play):\n")
        Feature.values().forEach { append("• ").append(it.title).append("\n") }
        append("\nTus conversaciones nunca salen del teléfono, en ninguna versión.")
    }
}
