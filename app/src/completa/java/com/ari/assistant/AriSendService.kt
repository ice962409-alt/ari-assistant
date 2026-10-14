package com.ari.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Solo en la Ari CEO: toca "enviar" en tu app de mensajes cuando Ari acaba de armar un
 * recado (ver [AutoSend]). Revisa que la caja de mensaje tenga exactamente ese recado
 * antes de tocar; si algo no cuadra, no hace nada y tú lo envías a mano.
 */
class AriSendService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pending = AutoSend.current() ?: return
        val root = rootInActiveWindow ?: return
        val box = findCompose(root) ?: return
        if (!AutoSend.matches(box.text?.toString().orEmpty(), pending.text)) return
        val send = findSend(root) ?: return
        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutoSend.disarm()
            AriChat.ariSays(this, "Listo, le mandé el recado a ${pending.name}.", alert = false)
        }
    }

    /** La caja donde se escribe el mensaje (editable y con el recado de Ari). */
    private fun findCompose(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable && node.text?.toString()?.contains("[Ari") == true) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** El botón de enviar: por su nombre interno o por lo que dice ("Enviar", "Send", "SMS"). */
    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        SEND_IDS.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isEnabled }?.let { return clickable(it) }
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val label = (node.contentDescription ?: node.text)?.toString()?.lowercase().orEmpty()
            if (node.isEnabled && SEND_WORDS.any { label == it || label.startsWith("$it ") }) return clickable(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** Si el ícono no se puede tocar, se toca el botón que lo contiene. */
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var steps = 0
        while (current != null && !current.isClickable && steps < 3) {
            current = current.parent
            steps++
        }
        return current?.takeIf { it.isClickable }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val MAX_NODES = 400
        val SEND_IDS = listOf(
            "com.google.android.apps.messaging:id/send_message_button_icon",
            "com.google.android.apps.messaging:id/send_message_button_container",
            "com.samsung.android.messaging:id/send_button",
            "com.android.mms:id/send_button",
            "com.miui.mms:id/send_button",
            "com.android.mms:id/send_button_sms",
        )
        val SEND_WORDS = listOf("enviar", "send", "enviar sms", "enviar mensaje", "send sms", "send message")
    }
}
