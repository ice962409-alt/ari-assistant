import Foundation

/// Todo lo que Ari guarda en el iPhone, en un archivo JSON privado de la app.
/// No sale del teléfono. Se usa desde un solo hilo (la pantalla).
public final class AriStore {
    public struct Snapshot: Codable, Equatable {
        public var profile: Profile?
        public var messages: [ChatMessage] = []
        public var memories: [Memory] = []
        /// nil: todavía no se le preguntó. true/false: lo que eligió.
        public var reportsAllowed: Bool?
        public var installId: UUID = UUID()
        public var model: AriModel?
        public var pendingReports: [CrashReport] = []

        public init() {}

        // Campos que falten (de una versión anterior) toman su valor por defecto,
        // para no perder lo guardado al actualizar la app.
        public init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            profile = try c.decodeIfPresent(Profile.self, forKey: .profile)
            messages = try c.decodeIfPresent([ChatMessage].self, forKey: .messages) ?? []
            memories = try c.decodeIfPresent([Memory].self, forKey: .memories) ?? []
            reportsAllowed = try c.decodeIfPresent(Bool.self, forKey: .reportsAllowed)
            installId = try c.decodeIfPresent(UUID.self, forKey: .installId) ?? UUID()
            model = try? c.decodeIfPresent(AriModel.self, forKey: .model)
            pendingReports = (try? c.decodeIfPresent([CrashReport].self, forKey: .pendingReports)) ?? []
        }
    }

    /// Mensajes que se conservan; los más viejos se descartan.
    public static let maxMessages = 1000
    public static let maxPendingReports = 20

    public private(set) var data: Snapshot
    private let fileURL: URL?

    /// - Parameter fileURL: dónde guardar. nil para pruebas en memoria.
    public init(fileURL: URL?) {
        self.fileURL = fileURL
        guard let fileURL, FileManager.default.fileExists(atPath: fileURL.path) else {
            data = Snapshot()
            return
        }
        if let raw = try? Data(contentsOf: fileURL), let loaded = try? AriStore.decoder.decode(Snapshot.self, from: raw) {
            data = loaded
        } else {
            // No se pudo leer (por ejemplo, archivo dañado): se aparta en vez de sobrescribirlo,
            // para no perder las pláticas y los recuerdos.
            let backup = fileURL.appendingPathExtension("respaldo-\(Int(Date().timeIntervalSince1970))")
            try? FileManager.default.moveItem(at: fileURL, to: backup)
            data = Snapshot()
        }
    }

    // MARK: Perfil

    public var profile: Profile? { data.profile }

    public func setProfile(_ profile: Profile) {
        data.profile = profile
        save()
    }

    public func setModel(_ model: AriModel?) {
        data.model = model
        save()
    }

    public func setReportsAllowed(_ allowed: Bool) {
        data.reportsAllowed = allowed
        if !allowed { data.pendingReports = [] }
        save()
    }

    // MARK: Mensajes

    public func addMessage(_ message: ChatMessage) {
        data.messages.append(message)
        if data.messages.count > AriStore.maxMessages {
            data.messages.removeFirst(data.messages.count - AriStore.maxMessages)
        }
        save()
    }

    public func clearMessages() {
        data.messages = []
        save()
    }

    // MARK: Recuerdos

    /// Devuelve false si ya existía uno igual (sin importar mayúsculas ni acentos).
    @discardableResult
    public func addMemory(_ text: String) -> Bool {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return false }
        if data.memories.contains(where: { AriText.fold($0.text) == AriText.fold(clean) }) { return false }
        data.memories.append(Memory(text: clean))
        save()
        return true
    }

    /// Borra los recuerdos que contienen esas palabras. Devuelve cuántos borró.
    @discardableResult
    public func forget(_ query: String) -> Int {
        let before = data.memories.count
        data.memories.removeAll { AriText.containsWords($0.text, query) }
        let removed = before - data.memories.count
        if removed > 0 { save() }
        return removed
    }

    public func deleteMemory(id: UUID) {
        data.memories.removeAll { $0.id == id }
        save()
    }

    // MARK: Informes de fallos

    /// Solo con permiso. Si hay demasiados pendientes, se descartan los más viejos.
    public func enqueueReport(_ report: CrashReport) {
        guard data.reportsAllowed == true else { return }
        data.pendingReports.append(report)
        if data.pendingReports.count > AriStore.maxPendingReports {
            data.pendingReports.removeFirst(data.pendingReports.count - AriStore.maxPendingReports)
        }
        save()
    }

    public func removeReport(id: UUID) {
        data.pendingReports.removeAll { $0.id == id }
        save()
    }

    /// Anota un intento fallido; tras demasiados, el informe se descarta.
    public func recordFailedAttempt(id: UUID, at date: Date = Date()) {
        guard let index = data.pendingReports.firstIndex(where: { $0.id == id }) else { return }
        data.pendingReports[index].attempts += 1
        data.pendingReports[index].lastAttempt = date
        if data.pendingReports[index].attempts >= CrashReport.maxAttempts {
            data.pendingReports.remove(at: index)
        }
        save()
    }

    // MARK: Guardado

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    private func save() {
        guard let fileURL, let raw = try? AriStore.encoder.encode(data) else { return }
        try? FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        #if os(iOS)
        try? raw.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        #else
        try? raw.write(to: fileURL, options: [.atomic])
        #endif
    }
}
