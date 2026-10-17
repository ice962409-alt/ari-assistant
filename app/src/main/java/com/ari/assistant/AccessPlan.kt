package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * "Darle todo a ARI": todos los permisos y accesos que ARI puede usar, en el orden en que se
 * piden con un solo botón (decisión del dueño, 17/10/2026). Primero los permisos normales, de
 * una vez (Android muestra una ventanita por cada uno); después, una por una, las pantallas
 * especiales que Android no deja conceder desde la app. MainActivity revisa cada uno y abre su
 * pantalla. Sin Android, para probarlo.
 */
object AccessPlan {

    enum class Kind {
        /** Se piden todos juntos con requestPermissions. */
        PERMISSION,
        /** Una pantalla de ajustes del teléfono donde activas a ARI y regresas. */
        SCREEN,
    }

    enum class Item(
        val title: String,
        val why: String,
        val kind: Kind,
        val ceoOnly: Boolean = false,
        val minSdk: Int = 0,
        val maxSdk: Int = Int.MAX_VALUE,
        val permissions: List<String> = emptyList(),
    ) {
        MICROPHONE("Micrófono", "hablar con ella en voz", Kind.PERMISSION, permissions = listOf("android.permission.RECORD_AUDIO")),
        NOTIFICATIONS("Notificaciones", "avisarte de recordatorios", Kind.PERMISSION, minSdk = 33,
            permissions = listOf("android.permission.POST_NOTIFICATIONS")),
        CONTACTS("Contactos", "llamar y mandar mensajes por nombre", Kind.PERMISSION,
            permissions = listOf("android.permission.READ_CONTACTS")),
        CALENDAR("Calendario", "decirte tu agenda", Kind.PERMISSION, permissions = listOf("android.permission.READ_CALENDAR")),
        CALENDAR_WRITE("Guardar citas", "agendar en tu calendario", Kind.PERMISSION, ceoOnly = true,
            permissions = listOf("android.permission.WRITE_CALENDAR")),
        PHOTOS("Fotos", "mostrarte tus fotos y capturas", Kind.PERMISSION, ceoOnly = true, minSdk = 33,
            permissions = listOf("android.permission.READ_MEDIA_IMAGES")),
        PHOTOS_OLD("Fotos", "mostrarte tus fotos y capturas", Kind.PERMISSION, ceoOnly = true, maxSdk = 32,
            permissions = listOf("android.permission.READ_EXTERNAL_STORAGE")),
        STEPS("Actividad física", "contar tus pasos", Kind.PERMISSION, ceoOnly = true, minSdk = 29,
            permissions = listOf("android.permission.ACTIVITY_RECOGNITION")),
        LOCATION("Ubicación", "saber en qué wifi estás", Kind.PERMISSION, ceoOnly = true,
            permissions = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")),
        ACCESSIBILITY("Manejar mis apps (Accesibilidad)", "tocar, escribir y enviar por ti cuando se lo pides", Kind.SCREEN, ceoOnly = true),
        NOTIFICATION_ACCESS("Leer notificaciones", "leerte y contestar tus mensajes", Kind.SCREEN),
        OVERLAY("Mostrarse sobre otras apps", "su burbuja y abrirse cuando dices \"Ari\"", Kind.SCREEN),
        EXACT_ALARMS("Alarmas a la hora exacta", "que los recordatorios lleguen a tiempo", Kind.SCREEN, minSdk = 31),
        BATTERY("Batería sin restricciones", "seguir escuchando con la pantalla apagada", Kind.SCREEN, ceoOnly = true),
        USAGE("Acceso al uso", "decirte cuánto usas cada app", Kind.SCREEN, ceoOnly = true),
        ALL_FILES("Todos los archivos", "buscar tus documentos", Kind.SCREEN, ceoOnly = true, minSdk = 30),
        WRITE_SETTINGS("Cambiar ajustes", "subir o bajar el brillo", Kind.SCREEN, ceoOnly = true),
        DO_NOT_DISTURB("No molestar", "activarlo cuando se lo pides", Kind.SCREEN, ceoOnly = true),
        INSTALL_UPDATES("Instalar actualizaciones", "instalar sus versiones nuevas", Kind.SCREEN, ceoOnly = true),
        AUTOSTART("Inicio automático (Xiaomi)", "volver a funcionar sola después de reiniciar", Kind.SCREEN),
    }

    /** Los accesos que aplican en este teléfono ([sdk]), en esta ARI ([ceo]) y de esta marca ([xiaomi]). */
    fun applicable(sdk: Int, ceo: Boolean, xiaomi: Boolean): List<Item> = Item.values().filter { item ->
        sdk in item.minSdk..item.maxSdk && (ceo || !item.ceoOnly) && (item != Item.AUTOSTART || xiaomi)
    }

    /** Los permisos normales que faltan, para pedirlos todos de una vez (sin repetir). */
    fun permissionsToAsk(missing: List<Item>): List<String> =
        missing.filter { it.kind == Kind.PERMISSION }.flatMap { it.permissions }.distinct()

    /** La siguiente pantalla especial que falta y no se saltó (en orden), o null si ya no hay. */
    fun nextScreen(missing: List<Item>, skipped: Set<Item>): Item? =
        missing.firstOrNull { it.kind == Kind.SCREEN && it !in skipped }

    /** "Paso 2 de 7": la posición de [item] entre las pantallas de esta vuelta. */
    fun stepLabel(item: Item, screens: List<Item>): String {
        val index = screens.indexOf(item)
        return if (index < 0) item.title else "Paso ${index + 1} de ${screens.size}: ${item.title}"
    }

    /** "✓ Micrófono" o "○ Contactos — llamar y mandar mensajes por nombre", sin repetir títulos. */
    fun lines(items: List<Item>, done: Set<Item>): List<String> =
        items.groupBy { it.title }.map { (title, group) ->
            if (group.all { it in done }) "✓ $title" else "○ $title — ${group.first().why}"
        }

    /** "14 de 18 listos" contando cada título una vez. */
    fun progress(items: List<Item>, done: Set<Item>): String {
        val groups = items.groupBy { it.title }.values
        return "${groups.count { group -> group.all { it in done } }} de ${groups.size} listos"
    }

    private val ASK = Regex(
        "^(?:(?:da|dale|darle|dar|dame)(?:le)?\\s+(?:todo|todos los permisos|todos los accesos|acceso total|todo el acceso)(?:\\s+a\\s+ari)?" +
            "|(?:activa|activar)\\s+todo|todos los permisos|acceso total)$"
    )

    /** "dale todo a Ari", "darle todo a ARI", "activa todo", "todos los permisos". */
    fun asks(text: String) = ASK.matches(normalizeForAgents(text))
}
