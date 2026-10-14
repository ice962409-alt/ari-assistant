package com.ari.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Solo en la Ari CEO, con el permiso de Accesibilidad:
 *
 * - Recados: toca "enviar" en tu app de mensajes cuando Ari acaba de armar un recado
 *   (ver [AutoSend]). Revisa que la caja tenga exactamente ese recado; si algo no cuadra, no toca.
 * - Manejar tus apps ([AppControl]): toca botones, escribe, desliza, atrás, inicio y lee
 *   la pantalla, solo cuando tú se lo pides. Lo delicado (enviar, pagar, borrar) lo
 *   pregunta Ari antes de llegar aquí.
 *
 * Nunca toca nada en la propia Ari y no guarda lo que ve.
 */
class AriSendService : AccessibilityService(), AppControl.Controller {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppControl.controller = this
        OverlayHost.accessibility = this
        // La burbuja se vuelve a dibujar a través de este servicio, sin el aviso de Android.
        if (AriBubble.isEnabled(this)) {
            AriBubble.stop(this)
            AriBubble.start(this)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (AppControl.controller === this) AppControl.controller = null
        if (OverlayHost.accessibility === this) {
            OverlayHost.accessibility = null
            // Sin Accesibilidad, la burbuja vuelve a la forma normal (si hay permiso de mostrarse encima).
            if (AriBubble.isEnabled(this)) {
                AriBubble.stop(this)
                AriBubble.start(this)
            }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (AppControl.controller === this) AppControl.controller = null
        if (OverlayHost.accessibility === this) OverlayHost.accessibility = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AutoCall.current()?.let { call ->
            placeCall(event, call)
            return
        }
        // Solo los recados armados, y solo en las apps de mensajes.
        val pending = AutoSend.current() ?: return
        if (event?.packageName?.toString() !in MESSAGING) return
        val root = rootInActiveWindow ?: return
        val box = findCompose(root) ?: return
        if (!AutoSend.matches(box.text?.toString().orEmpty(), pending.text)) return
        val send = findSend(root) ?: return
        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutoSend.disarm()
            AriChat.ariSays(this, "Listo, le mandé el recado a ${pending.name}.", alert = false)
        }
    }

    // --- Manejar tus apps ---

    override fun perform(command: AppControl.Command): AppControl.Result = try {
        when (command) {
            is AppControl.Command.Tap -> tap(command.target)
            is AppControl.Command.Type -> type(command.text)
            is AppControl.Command.Scroll -> scroll(command.direction)
            is AppControl.Command.Global -> global(command.action)
            AppControl.Command.ReadScreen -> read()
        }
    } catch (e: Exception) {
        AppControl.Result(false, "No pude hacerlo en esta app. Intenta de otra forma o hazlo tú.")
    }

    /** La ventana de la app que estás usando (nunca la de Ari). */
    private fun appRoot(): AccessibilityNodeInfo? {
        val candidates = runCatching { windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { (if (it.isActive) 2 else 0) + (if (it.isFocused) 1 else 0) }
        for (window in candidates) {
            val root = window.root ?: continue
            if (root.packageName?.toString() != packageName) return root
        }
        return rootInActiveWindow?.takeIf { it.packageName?.toString() != packageName }
    }

    private fun tap(target: String): AppControl.Result {
        val root = appRoot() ?: return NO_APP
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        var bestLabel = ""
        walk(root) { node ->
            if (!node.isVisibleToUser) return@walk
            val label = label(node) ?: return@walk
            val score = AppControl.matchScore(label, target) * 2 + (if (node.isClickable) 1 else 0)
            if (score > bestScore && AppControl.matchScore(label, target) > 0) {
                best = node
                bestScore = score
                bestLabel = label
            }
        }
        val node = best ?: return AppControl.Result(false, "No veo \"$target\" en la pantalla. Dime cómo dice el botón exactamente o di \"lee mi pantalla\".")
        val shown = bestLabel.take(40)
        val clickable = clickableAncestor(node)
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return AppControl.Result(true, "Listo, toqué \"$shown\".")
        }
        // Si no se deja tocar como botón, se toca con el "dedo" en su centro.
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.isEmpty) return AppControl.Result(false, "Encontré \"$shown\" pero no lo pude tocar.")
        gesture(bounds.exactCenterX(), bounds.exactCenterY(), bounds.exactCenterX(), bounds.exactCenterY(), 60)
        return AppControl.Result(true, "Listo, toqué \"$shown\".")
    }

    private fun type(text: String): AppControl.Result {
        val root = appRoot() ?: return NO_APP
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: find(root) { it.isEditable && it.isVisibleToUser }
            ?: return AppControl.Result(false, "No veo dónde escribir en esta pantalla. Toca el campo de texto y vuelve a pedírmelo.")
        if (!field.isFocused) field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            AppControl.Result(true, "Listo, escribí: \"${text.take(60)}\".")
        } else {
            AppControl.Result(false, "Esta app no me dejó escribir ahí.")
        }
    }

    private fun scroll(direction: AppControl.Direction): AppControl.Result {
        val root = appRoot()
        val vertical = direction == AppControl.Direction.UP || direction == AppControl.Direction.DOWN
        if (root != null && vertical) {
            // Lo más grande que se puede desplazar (la lista o la página).
            var list: AccessibilityNodeInfo? = null
            var area = 0
            walk(root) { node ->
                if (node.isScrollable && node.isVisibleToUser) {
                    val r = Rect().also { node.getBoundsInScreen(it) }
                    val a = r.width() * r.height()
                    if (a > area) {
                        area = a
                        list = node
                    }
                }
            }
            val action = if (direction == AppControl.Direction.DOWN) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (list?.performAction(action) == true) return AppControl.Result(true, "Listo.")
        }
        // Si no, desliza con el "dedo".
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        when (direction) {
            AppControl.Direction.DOWN -> gesture(w / 2, h * 0.72f, w / 2, h * 0.3f, 300)
            AppControl.Direction.UP -> gesture(w / 2, h * 0.3f, w / 2, h * 0.72f, 300)
            AppControl.Direction.LEFT -> gesture(w * 0.2f, h / 2, w * 0.8f, h / 2, 250)
            AppControl.Direction.RIGHT -> gesture(w * 0.8f, h / 2, w * 0.2f, h / 2, 250)
        }
        return AppControl.Result(true, "Listo.")
    }

    private fun global(action: AppControl.GlobalAction): AppControl.Result {
        val code = when (action) {
            AppControl.GlobalAction.BACK -> GLOBAL_ACTION_BACK
            AppControl.GlobalAction.HOME -> GLOBAL_ACTION_HOME
            AppControl.GlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
            AppControl.GlobalAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            AppControl.GlobalAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            AppControl.GlobalAction.LOCK_SCREEN, AppControl.GlobalAction.SCREENSHOT -> {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
                    return AppControl.Result(false, "Tu versión de Android no me deja hacer eso.")
                }
                if (action == AppControl.GlobalAction.LOCK_SCREEN) GLOBAL_ACTION_LOCK_SCREEN else GLOBAL_ACTION_TAKE_SCREENSHOT
            }
        }
        val done = when (action) {
            AppControl.GlobalAction.SCREENSHOT -> "Listo, tomé la captura (está en tu galería)."
            AppControl.GlobalAction.LOCK_SCREEN -> "Listo, bloqueé la pantalla."
            else -> "Listo."
        }
        return if (performGlobalAction(code)) AppControl.Result(true, done) else AppControl.Result(false, "No pude hacerlo.")
    }

    private fun read(): AppControl.Result {
        val root = appRoot() ?: return NO_APP
        val texts = mutableListOf<String>()
        walk(root) { node ->
            if (node.isVisibleToUser) label(node)?.let { texts += it }
        }
        val summary = AppControl.summarize(texts)
        val app = root.packageName?.toString()?.let { name ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString() }.getOrNull()
        }
        return if (summary.isBlank()) AppControl.Result(false, "No veo texto en esta pantalla (puede ser una imagen o un video).")
        else AppControl.Result(true, (if (app != null) "En $app dice: " else "En la pantalla dice: ") + summary)
    }

    private fun label(node: AccessibilityNodeInfo): String? =
        (node.text ?: node.contentDescription)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_WALK) {
            val node = queue.removeFirst()
            visited++
            visit(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
    }

    private fun find(root: AccessibilityNodeInfo, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(root) { if (found == null && test(it)) found = it }
        return found
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var steps = 0
        while (current != null && !current.isClickable && steps < 4) {
            current = current.parent
            steps++
        }
        return current?.takeIf { it.isClickable && it.isEnabled }
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null)
    }

    // --- Llamar directo ("llama a mamá") ---

    /** En el marcador, con el número que Ari dejó, toca "llamar". */
    private fun placeCall(event: AccessibilityEvent?, call: AutoCall.Pending) {
        val app = event?.packageName?.toString().orEmpty()
        if (DIALER_HINTS.none { app.contains(it) }) return
        val root = rootInActiveWindow ?: return
        val screen = StringBuilder()
        walk(root) { node -> label(node)?.let { screen.append(it).append(' ') } }
        if (!AutoCall.matches(screen.toString(), call.number)) return
        val button = CALL_IDS.firstNotNullOfOrNull { id -> root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isEnabled } }
            ?: find(root) { node ->
                val text = (node.contentDescription ?: node.text)?.toString()?.lowercase()?.trim().orEmpty()
                node.isEnabled && CALL_WORDS.any { text == it || text.startsWith("$it ") }
            }
            ?: return
        if (clickableAncestor(button)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) AutoCall.disarm()
    }

    // --- Recados ---

    /** La caja donde se escribe el mensaje (editable y con el recado de Ari). */
    private fun findCompose(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        find(root) { it.isEditable && it.text?.toString()?.contains("[Ari") == true }

    /** El botón de enviar: por su nombre interno o por lo que dice ("Enviar", "Send", "SMS"). */
    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        SEND_IDS.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isEnabled }?.let { return clickableAncestor(it) }
        }
        val node = find(root) { node ->
            val label = (node.contentDescription ?: node.text)?.toString()?.lowercase().orEmpty()
            node.isEnabled && SEND_WORDS.any { label == it || label.startsWith("$it ") }
        } ?: return null
        return clickableAncestor(node)
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val MAX_WALK = 1_500
        val NO_APP = AppControl.Result(false, "Abre la app que quieres que maneje y pídemelo desde la burbuja o el modo live.")
        val MESSAGING = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.android.messaging", "com.miui.mms"
        )
        val SEND_IDS = listOf(
            "com.google.android.apps.messaging:id/send_message_button_icon",
            "com.google.android.apps.messaging:id/send_message_button_container",
            "com.samsung.android.messaging:id/send_button",
            "com.android.mms:id/send_button",
            "com.miui.mms:id/send_button",
            "com.android.mms:id/send_button_sms",
        )
        val DIALER_HINTS = listOf("dialer", "contacts", "phone", "incallui", "telecom")
        val CALL_IDS = listOf(
            "com.google.android.dialer:id/dialpad_floating_action_button",
            "com.android.dialer:id/dialpad_floating_action_button",
            "com.android.contacts:id/dialpad_floating_action_button",
            "com.android.contacts:id/call_sim1",
            "com.android.contacts:id/dial_button",
            "com.samsung.android.dialer:id/dialButton",
            "com.samsung.android.dialer:id/dial_button",
        )
        val CALL_WORDS = listOf("llamar", "marcar", "call", "dial", "llamada", "llamar con sim 1", "sim 1")
        val SEND_WORDS = listOf("enviar", "send", "enviar sms", "enviar mensaje", "send sms", "send message")
    }
}
