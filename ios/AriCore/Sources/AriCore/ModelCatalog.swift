import Foundation

/// Qué Gemma le conviene a cada iPhone. Ari mide la RAM y el espacio libre y recomienda.
public enum AriModel: String, CaseIterable, Codable, Sendable {
    case e4b
    case e2b

    public var title: String {
        switch self {
        case .e4b: return "Gemma 4 E4B"
        case .e2b: return "Gemma 4 E2B"
        }
    }

    public var url: URL {
        switch self {
        case .e4b:
            return URL(string: "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm")!
        case .e2b:
            return URL(string: "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm")!
        }
    }

    public var fileName: String { "gemma-4-\(rawValue.uppercased())-it.litertlm" }

    /// Tamaño aproximado del archivo, para avisar antes de descargar.
    public var bytes: Int64 {
        switch self {
        case .e4b: return 3_659_530_240
        case .e2b: return 2_588_147_712
        }
    }

    /// El modelo más un margen para no dejar el teléfono sin espacio.
    public var spaceNeeded: Int64 { bytes + 500_000_000 }
}

public enum ModelCatalog {
    private static let gib: UInt64 = 1024 * 1024 * 1024
    /// Un iPhone "de 8 GB" reporta un poco menos: se cuenta desde 7.
    public static let fullRAM: UInt64 = 7 * gib
    /// Con menos de esto, ni el modelo ligero corre bien.
    public static let minRAM: UInt64 = 4 * gib

    public struct Advice: Equatable, Sendable {
        public let model: AriModel
        public let tooWeak: Bool
        public let reason: String
    }

    public static func recommend(ramBytes: UInt64, freeBytes: Int64) -> Advice {
        let ram = "\(gb(Int64(ramBytes))) GB de RAM"
        let free = "\(gb(freeBytes)) GB libres"
        if ramBytes >= fullRAM && freeBytes >= AriModel.e4b.spaceNeeded {
            return Advice(model: .e4b, tooWeak: false, reason: "Tu iPhone tiene \(ram) y \(free): le va el modelo completo.")
        }
        if ramBytes >= fullRAM {
            return Advice(model: .e2b, tooWeak: false,
                          reason: "Tu iPhone tiene \(ram), pero solo \(free): el modelo completo no cabe, así que te recomiendo el ligero.")
        }
        if ramBytes >= minRAM {
            return Advice(model: .e2b, tooWeak: false, reason: "Tu iPhone tiene \(ram): el modelo ligero corre mejor ahí.")
        }
        return Advice(model: .e2b, tooWeak: true,
                      reason: "Tu iPhone tiene \(ram): es poca para Gemma. Puedes probar el modelo ligero, pero puede ir muy lento o cerrarse.")
    }

    public static func gb(_ bytes: Int64) -> String {
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), Double(bytes) / 1_000_000_000)
    }
}
