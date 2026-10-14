import Foundation

/// Un informe de fallo con el formato del intermediario (ver CONTRATO.md en ari-informes).
/// Solo datos técnicos: nunca mensajes, recuerdos ni nombres.
public struct CrashReport: Codable, Equatable, Identifiable, Sendable {
    public enum Kind: String, Codable, Sendable {
        case cierre
        case congelada
        case memoria
        case error
    }

    public let id: UUID
    public let kind: Kind
    public let summary: String
    public let details: String
    public let appVersion: String
    public let osVersion: String
    public let device: String
    public let ramGb: String
    public let model: String
    public let happenedAt: Date
    public var attempts: Int
    public var lastAttempt: Date?

    public init(
        kind: Kind, summary: String, details: String, appVersion: String, osVersion: String,
        device: String, ramGb: String, model: String, happenedAt: Date = Date(), id: UUID = UUID()
    ) {
        self.id = id
        self.kind = kind
        self.summary = String(CrashReport.scrub(summary).prefix(200))
        self.details = String(CrashReport.scrub(details).prefix(8000))
        self.appVersion = appVersion
        self.osVersion = osVersion
        self.device = device
        self.ramGb = ramGb
        self.model = model
        self.happenedAt = happenedAt
        self.attempts = 0
        self.lastAttempt = nil
    }

    /// El cuerpo JSON que espera POST /v1/informe.
    public func body(installId: UUID) throws -> Data {
        let formatter = ISO8601DateFormatter()
        let payload: [String: String] = [
            "platform": "ios",
            "appVersion": appVersion,
            "kind": kind.rawValue,
            "summary": summary.isEmpty ? "(sin resumen)" : summary,
            "details": details,
            "osVersion": osVersion,
            "device": device,
            "ramGb": ramGb,
            "model": model,
            "installId": installId.uuidString,
            "happenedAt": formatter.string(from: happenedAt),
        ]
        return try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
    }

    /// Primera capa de limpieza (el intermediario hace otra): correos, teléfonos, enlaces y llaves.
    public static func scrub(_ text: String) -> String {
        let rules: [(String, String)] = [
            (#"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"#, "[correo]"),
            (#"\b(github_pat_|ghp_|hf_|sk-)[A-Za-z0-9_\-]{10,}"#, "[llave]"),
            (#"https?://\S+"#, "[enlace]"),
            (#"(?<![\w.:-])\+?\d(?:[ -]?\d){9,14}(?![\w.:-])"#, "[número]"),
        ]
        var result = text
        for (pattern, replacement) in rules {
            result = result.replacingOccurrences(of: pattern, with: replacement, options: .regularExpression)
        }
        return result
    }

    /// Qué hacer según la respuesta del intermediario.
    public enum Outcome: Equatable {
        case sent
        case drop
        case retryLater
    }

    public static func outcome(forStatus status: Int) -> Outcome {
        switch status {
        case 200, 201: return .sent
        case 400, 403, 413: return .drop
        default: return .retryLater
        }
    }

    /// Reintentos espaciados: 1 min, 10 min, 1 h, y luego cada 6 h. Tras 8 intentos se descarta.
    public static let maxAttempts = 8

    public func isDue(now: Date = Date()) -> Bool {
        guard let lastAttempt, attempts > 0 else { return true }
        let waits: [TimeInterval] = [60, 600, 3600]
        let wait = attempts <= waits.count ? waits[attempts - 1] : 6 * 3600
        return now.timeIntervalSince(lastAttempt) >= wait
    }
}
