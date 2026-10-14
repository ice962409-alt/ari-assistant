import Foundation
import AriCore

/// El cerebro de la pantalla: mensajes, recuerdos, modelo y comandos.
@MainActor
final class ChatStore: ObservableObject {
    enum ModelState: Equatable {
        case missing
        case downloading(AriModel, Double)
        case loading(AriModel)
        case ready(String)
        case failed(String)
    }

    @Published private(set) var messages: [ChatMessage]
    @Published private(set) var memories: [Memory]
    @Published private(set) var profile: Profile?
    @Published private(set) var reportsAllowed: Bool?
    @Published private(set) var modelState: ModelState = .missing
    @Published private(set) var busy = false

    let store: AriStore
    let reporter: Reporter
    private let engine = AriEngine()
    private let downloader = ModelDownloader()
    private var needsFreshConversation = true
    private var generation: Task<Void, Never>?
    private var started = false

    init() {
        let file = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Ari", isDirectory: true)
            .appendingPathComponent("ari.json")
        let store = AriStore(fileURL: file)
        self.store = store
        reporter = Reporter(store: store)
        messages = store.data.messages
        memories = store.data.memories
        profile = store.profile
        reportsAllowed = store.data.reportsAllowed
    }

    /// Al abrir la app: informes pendientes, descargas en curso y el modelo guardado.
    func start() async {
        guard !started else { return }
        started = true
        reporter.start()
        downloader.onProgress = { [weak self] model, progress in
            Task { @MainActor in self?.modelState = .downloading(model, progress) }
        }
        downloader.onFinish = { [weak self] model, result in
            Task { @MainActor in self?.downloadFinished(model, result) }
        }
        downloader.reconnect()
        await reporter.flush()

        if let downloading = await downloader.activeModel() {
            modelState = .downloading(downloading, 0)
        } else if let saved = store.data.model, ModelFiles.exists(saved) {
            await loadModel(saved)
        } else if let found = AriModel.allCases.first(where: ModelFiles.exists) {
            await loadModel(found)
        }
    }

    var currentModel: AriModel? { store.data.model }

    var needsOnboarding: Bool { profile == nil }

    // MARK: Perfil y permisos

    func saveProfile(_ profile: Profile) {
        store.setProfile(profile)
        self.profile = profile
        needsFreshConversation = true
    }

    func setReportsAllowed(_ allowed: Bool) {
        store.setReportsAllowed(allowed)
        reportsAllowed = allowed
        if allowed { Task { await reporter.flush() } }
    }

    // MARK: Chat

    func send(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !busy else { return }

        if let command = Command.parse(text) {
            append(ChatMessage(role: .user, text: text))
            append(ChatMessage(role: .ari, text: handle(command)))
            return
        }

        guard case .ready = modelState else {
            append(ChatMessage(role: .user, text: text))
            append(ChatMessage(role: .note, text: "Primero necesito mi modelo: toca Modelo para descargarlo o cargarlo."))
            return
        }

        append(ChatMessage(role: .user, text: text))
        busy = true
        generation = Task { await answer(text, retried: false) }
    }

    func stop() {
        engine.cancel()
    }

    private func answer(_ text: String, retried: Bool) async {
        let placeholder = ChatMessage(role: .ari, text: "")
        messages.append(placeholder)
        var reply = ""
        do {
            if needsFreshConversation {
                try await engine.startConversation(system: systemPrompt(), history: HistoryTrimmer.trim(store.data.messages))
                needsFreshConversation = false
            }
            for try await chunk in try engine.reply(to: text) {
                reply += chunk.toString
                updateLast(placeholder.id, reply)
            }
            finish(placeholder.id, reply)
        } catch {
            messages.removeAll { $0.id == placeholder.id }
            // Si Gemma falla antes de escribir (por ejemplo, la plática ya no cabe),
            // se abre una conversación nueva y se intenta una vez más.
            if reply.isEmpty && !retried {
                needsFreshConversation = true
                await answer(text, retried: true)
                return
            }
            if !reply.isEmpty { finish(placeholder.id, reply) }
            append(ChatMessage(role: .note, text: "No pude terminar la respuesta. Intenta de nuevo."))
            needsFreshConversation = true
            reporter.reportError(error, summary: "Error al responder", model: modelLabel)
            busy = false
        }
    }

    private func finish(_ id: UUID, _ reply: String) {
        let clean = reply.trimmingCharacters(in: .whitespacesAndNewlines)
        messages.removeAll { $0.id == id }
        if !clean.isEmpty { append(ChatMessage(role: .ari, text: clean, id: id)) }
        busy = false
    }

    private func updateLast(_ id: UUID, _ text: String) {
        if let index = messages.lastIndex(where: { $0.id == id }) {
            messages[index].text = text
        }
    }

    private func append(_ message: ChatMessage) {
        messages.append(message)
        store.addMessage(message)
    }

    private func systemPrompt() -> String {
        var text = (profile ?? Profile()).personality()
        if !memories.isEmpty {
            text += "\n\nRecuerdos guardados sobre la persona (úsalos cuando sirvan, sin repetirlos literalmente):\n"
            text += memories.map { "- \($0.text)" }.joined(separator: "\n")
        }
        return text
    }

    // MARK: Recuerdos

    private func handle(_ command: Command) -> String {
        switch command {
        case .remember(let value):
            return addMemory(value) ? "Lo recordaré: \(value)" : "Eso ya lo tenía guardado."
        case .listMemories:
            if memories.isEmpty { return "Todavía no tengo recuerdos guardados. Escribe \"recuerda que …\" para guardar uno." }
            return "Esto es lo que recuerdo:\n" + memories.map { "• \($0.text)" }.joined(separator: "\n")
        case .forget(let value):
            let removed = store.forget(value)
            refreshMemories()
            switch removed {
            case 0: return "No encontré recuerdos con «\(value)»."
            case 1: return "Olvidé 1 recuerdo."
            default: return "Olvidé \(removed) recuerdos."
            }
        case .help:
            return "Puedo recordar cosas: escribe «recuerda que …». Para verlas: «qué recuerdas de mí». Para borrar: «olvida …»."
        case .unknown:
            return "No conozco ese comando. Prueba /recuerda, /memoria, /olvida o /ayuda."
        }
    }

    @discardableResult
    func addMemory(_ text: String) -> Bool {
        let added = store.addMemory(text)
        refreshMemories()
        return added
    }

    func deleteMemory(_ memory: Memory) {
        store.deleteMemory(id: memory.id)
        refreshMemories()
    }

    func clearChat() {
        store.clearMessages()
        messages = []
        needsFreshConversation = true
    }

    private func refreshMemories() {
        memories = store.data.memories
        needsFreshConversation = true
    }

    // MARK: Modelo

    var modelLabel: String {
        guard let model = store.data.model else { return "sin modelo" }
        return engine.backendName.isEmpty ? model.title : "\(model.title) (\(engine.backendName))"
    }

    func recommendation() -> ModelCatalog.Advice {
        ModelCatalog.recommend(ramBytes: ProcessInfo.processInfo.physicalMemory, freeBytes: ModelFiles.freeBytes)
    }

    func download(_ model: AriModel) {
        modelState = .downloading(model, 0)
        downloader.start(model)
    }

    func cancelDownload() {
        downloader.cancelAll()
        modelState = .missing
    }

    /// Un modelo que la persona ya tenía (por ejemplo, en Archivos).
    func importModel(from url: URL) async {
        let model: AriModel = url.lastPathComponent.uppercased().contains("E2B") ? .e2b : .e4b
        modelState = .loading(model)
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        do {
            // Copiar varios GB tarda: fuera del hilo de la pantalla.
            try await Task.detached(priority: .userInitiated) {
                _ = try ModelFiles.install(url, as: model, copy: true)
            }.value
            await loadModel(model)
        } catch {
            modelState = .failed("No pude copiar el archivo: \(error.localizedDescription)")
        }
    }

    func loadModel(_ model: AriModel) async {
        guard ModelFiles.exists(model) else {
            modelState = .missing
            return
        }
        modelState = .loading(model)
        do {
            try await engine.load(modelURL: ModelFiles.url(for: model), cacheDir: ModelFiles.cacheDir)
            store.setModel(model)
            needsFreshConversation = true
            modelState = .ready(modelLabel)
            append(ChatMessage(role: .note, text: "\(modelLabel) cargado. Ari está lista."))
        } catch {
            modelState = .failed("No pude cargar \(model.title): \(error.localizedDescription)")
            reporter.reportError(error, summary: "El modelo no cargó", model: model.title)
        }
    }

    func deleteModel(_ model: AriModel) {
        if store.data.model == model {
            engine.unload()
            store.setModel(nil)
            modelState = .missing
        }
        ModelFiles.delete(model)
    }

    private func downloadFinished(_ model: AriModel, _ result: Result<URL, Error>) {
        switch result {
        case .success:
            Task { await loadModel(model) }
        case .failure(let error):
            modelState = .failed(error.localizedDescription)
        }
    }
}
