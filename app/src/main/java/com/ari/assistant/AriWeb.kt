package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Solo en la ARI del dueño: consultas a internet cuando tú las pides.
 * - Wikipedia en español: "busca en internet qué es …", "¿quién fue …?".
 * - Clima con Open-Meteo: "¿cómo está el clima en …?".
 * Lo único que sale del teléfono es lo que buscas. Las frases no usan Android.
 */
object AriWeb {

    /** Marca que Gemma escribe cuando no sabe algo y la app debe buscarlo en internet. */
    const val SEARCH_TAG = "BUSCAR:"

    /** Lo que Gemma pidió buscar (línea "BUSCAR: …"), o null si no pidió nada. */
    fun autoQuery(response: String): String? =
        response.lines().map { it.trim().trim('*', '_', '-', ' ') }
            .firstOrNull { it.uppercase().startsWith(SEARCH_TAG) }
            ?.substringAfter(':')
            ?.trim { it.isWhitespace() || it in "*_\"'`" }
            ?.trimEnd('.', '?', '!')
            ?.trim()
            ?.takeIf { it.length in 2..120 }

    sealed class Request {
        data class Wiki(val topic: String) : Request()
        data class Weather(val place: String) : Request()
        /** Búsqueda general en internet (más amplia que Wikipedia). */
        data class Search(val query: String) : Request()
    }

    private val WIKI = Regex("^(?:busca|buscame|investiga)\\s+en\\s+wikipedia\\s+(?:que\\s+es\\s+|quien\\s+es\\s+|quien\\s+fue\\s+|sobre\\s+)?(.+)$")
    private val SEARCH = Regex("^(?:busca|buscame|buscar|investiga|investigame|averigua|averiguame|consulta|revisa|revisame|checa)\\s+en\\s+(?:internet|la\\s+web|la\\s+red|google|linea|la\\s+linea)\\s+(?:sobre\\s+|acerca\\s+de\\s+|informacion\\s+(?:de|sobre)\\s+|que\\s+es\\s+|quien\\s+(?:es|fue)\\s+)?(.+)$"
        + "|^(?:que\\s+dice|que\\s+hay\\s+en)\\s+(?:el\\s+)?internet\\s+(?:de|sobre|acerca\\s+de)\\s+(.+)$")
    private val WHO = Regex("^(?:quien\\s+fue|quien\\s+era)\\s+(.+)$")
    private val WEATHER = Regex("^(?:como\\s+esta\\s+|que\\s+tal\\s+(?:esta\\s+)?)?el\\s+clima\\s+(?:en|de)\\s+(.+?)(?:\\s+hoy)?$")

    fun parse(text: String): Request? {
        val t = normalizeForAgents(text)
        WEATHER.matchEntire(t)?.let { return Request.Weather(it.groupValues[1].trim()) }
        WIKI.matchEntire(t)?.let { return Request.Wiki(it.groupValues[1].trim()) }
        WHO.matchEntire(t)?.let { return Request.Wiki(it.groupValues[1].trim()) }
        SEARCH.matchEntire(t)?.let { m -> return Request.Search(m.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty().trim()) }
        return null
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ARI/1.0 (asistente personal)")
            if (c.responseCode !in 200..299) throw IllegalStateException("código ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText().take(200_000) }
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Resumen de Wikipedia en español (busca el artículo más parecido). */
    fun wiki(topic: String): String {
        val search = JSONObject(get("https://es.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch=" + enc(topic)))
        val hits = search.optJSONObject("query")?.optJSONArray("search")
        val title = hits?.optJSONObject(0)?.optString("title").orEmpty()
        if (title.isEmpty()) return "No encontré nada sobre \"$topic\" en Wikipedia."
        val page = JSONObject(get("https://es.wikipedia.org/api/rest_v1/page/summary/" + enc(title.replace(' ', '_'))))
        val extract = page.optString("extract").trim()
        return if (extract.isEmpty()) "Encontré \"$title\" en Wikipedia, pero sin resumen." else "$title (Wikipedia): ${extract.take(900)}"
    }

    fun weather(place: String): String {
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&name=" + enc(place)))
        val hit = geo.optJSONArray("results")?.optJSONObject(0) ?: return "No encontré la ciudad \"$place\"."
        val name = hit.optString("name", place)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${hit.getDouble("latitude")}&longitude=${hit.getDouble("longitude")}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"
        val data = JSONObject(get(url))
        val now = data.getJSONObject("current")
        val day = data.getJSONObject("daily")
        val temp = now.getDouble("temperature_2m").toInt()
        val max = day.getJSONArray("temperature_2m_max").getDouble(0).toInt()
        val min = day.getJSONArray("temperature_2m_min").getDouble(0).toInt()
        val rain = day.optJSONArray("precipitation_probability_max")?.optInt(0) ?: 0
        return "En $name hay $temp °C, ${sky(now.optInt("weather_code"))}. Hoy: máxima $max °C, mínima $min °C, $rain% de probabilidad de lluvia."
    }

    /**
     * Búsqueda general en internet. Usa la API de respuestas de DuckDuckGo (datos en JSON, no
     * copia páginas enteras) y, si no trae nada, cae en Wikipedia. Solo sale lo que buscas.
     */
    fun search(query: String): String {
        val answer = runCatching {
            val o = JSONObject(get("https://api.duckduckgo.com/?no_html=1&skip_disambig=1&t=ari&format=json&q=" + enc(query)))
            o.optString("AbstractText").trim().ifEmpty {
                o.optJSONArray("RelatedTopics")?.optJSONObject(0)?.optString("Text")?.trim().orEmpty()
            }
        }.getOrNull().orEmpty()
        if (answer.isNotEmpty()) return "Esto encontré sobre \"$query\": ${answer.take(900)}"
        // Sin respuesta directa: lo busco en Wikipedia.
        return wiki(query)
    }

    /** Códigos del clima de Open-Meteo en palabras. */
    fun sky(code: Int): String = when (code) {
        0 -> "despejado"
        1, 2 -> "algo nublado"
        3 -> "nublado"
        45, 48 -> "con niebla"
        in 51..67 -> "con lluvia"
        in 71..77 -> "con nieve"
        in 80..82 -> "con chubascos"
        in 95..99 -> "con tormenta"
        else -> "variable"
    }
}
