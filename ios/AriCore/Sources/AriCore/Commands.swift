import Foundation

/// Lo que Ari resuelve sola, sin pasar por el modelo: los comandos de memoria.
public enum Command: Equatable, Sendable {
    case remember(String)
    case listMemories
    case forget(String)
    case help
    case unknown(String)

    /// Devuelve el comando, o nil si el texto es para platicar con el modelo.
    public static func parse(_ raw: String) -> Command? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        if text.hasPrefix("/") {
            let name = text.split(separator: " ", maxSplits: 1).first.map { String($0).lowercased() } ?? ""
            let argument = text.dropFirst(name.count).trimmingCharacters(in: .whitespaces)
            switch name {
            case "/recuerda": return argument.isEmpty ? .help : .remember(argument)
            case "/memoria", "/memorias": return .listMemories
            case "/olvida": return argument.isEmpty ? .help : .forget(argument)
            case "/ayuda": return .help
            default: return .unknown(name)
            }
        }

        let folded = AriText.fold(text)
        for prefix in ["recuerda que ", "anota que ", "acuerdate de que ", "acuerdate que ", "no olvides que "] {
            if folded.hasPrefix(prefix) {
                let value = String(text.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
                return value.isEmpty ? nil : .remember(AriText.capitalizedFirst(AriText.stripEndPunctuation(value)))
            }
        }

        let cleaned = AriText.stripEndPunctuation(folded).trimmingCharacters(in: CharacterSet(charactersIn: "¿¡ "))
        if ["que recuerdas de mi", "que sabes de mi", "que recuerdas", "mis recuerdos", "tu memoria"].contains(cleaned) {
            return .listMemories
        }

        if folded.hasPrefix("olvida ") {
            let value = AriText.stripEndPunctuation(String(text.dropFirst("olvida ".count)))
                .trimmingCharacters(in: .whitespacesAndNewlines)
            // "olvida eso" o "olvida lo que te dije" son conversación, no una orden de borrar.
            let vague = ["eso", "esto", "lo que te dije", "lo anterior", "todo eso", "lo que dije"]
            if value.isEmpty || vague.contains(AriText.fold(value)) || value.split(separator: " ").count > 6 {
                return nil
            }
            return .forget(value)
        }
        return nil
    }
}

/// Utilidades de texto en español: comparar sin acentos ni mayúsculas.
public enum AriText {
    public static func fold(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "es")).lowercased()
    }

    static func stripEndPunctuation(_ text: String) -> String {
        var result = text.trimmingCharacters(in: .whitespacesAndNewlines)
        while let last = result.last, ".!?¡¿,;:".contains(last) { result.removeLast() }
        return result
    }

    static func capitalizedFirst(_ text: String) -> String {
        guard let first = text.first else { return text }
        return first.uppercased() + text.dropFirst()
    }

    /// ¿El recuerdo contiene la palabra o frase buscada como palabras completas?
    /// "olvida café" borra "Me gusta el café" pero no "Voy a la cafetería".
    public static func containsWords(_ haystack: String, _ needle: String) -> Bool {
        let words = { (s: String) in fold(s).split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map(String.init) }
        let hay = words(haystack)
        let need = words(needle)
        guard !need.isEmpty, need.count <= hay.count else { return false }
        for start in 0...(hay.count - need.count) where Array(hay[start..<(start + need.count)]) == need {
            return true
        }
        return false
    }
}
