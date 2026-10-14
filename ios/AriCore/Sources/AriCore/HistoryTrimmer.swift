import Foundation

/// Lo que el modelo recibe al empezar cada conversación: los últimos mensajes guardados,
/// recortados para que quepan en su memoria. Igual que GemmaContext en Android.
public enum HistoryTrimmer {
    public static let maxMessages = 20
    public static let maxTotalChars = 4000
    public static let maxMessageChars = 1200

    /// Empieza con un mensaje de la persona y termina con una respuesta de Ari. Un mensaje
    /// de la persona sin respuesta al final se quita: es el que se está enviando ahora.
    public static func trim(
        _ saved: [ChatMessage],
        maxMessages: Int = HistoryTrimmer.maxMessages,
        maxTotal: Int = HistoryTrimmer.maxTotalChars,
        maxEach: Int = HistoryTrimmer.maxMessageChars
    ) -> [ChatMessage] {
        var recent = Array(
            saved.filter { $0.role != .note && !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                .suffix(maxMessages)
        )
        while recent.last?.role == .user { recent.removeLast() }

        var kept: [ChatMessage] = []
        var total = 0
        for message in recent.reversed() {
            var text = message.text.trimmingCharacters(in: .whitespacesAndNewlines)
            if text.count > maxEach { text = String(text.prefix(maxEach)) + "…" }
            if total + text.count > maxTotal { break }
            total += text.count
            kept.insert(ChatMessage(role: message.role, text: text, date: message.date, id: message.id), at: 0)
        }
        while let first = kept.first, first.role != .user { kept.removeFirst() }
        return kept
    }
}
