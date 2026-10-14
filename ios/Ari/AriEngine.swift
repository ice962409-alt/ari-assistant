import Foundation
import AriCore
import LiteRTLM

/// Gemma en el iPhone, con LiteRT-LM. Prueba primero la GPU (Metal) y, si falla, la CPU.
final class AriEngine {
    enum EngineError: LocalizedError {
        case notLoaded

        var errorDescription: String? { "El modelo no está cargado." }
    }

    private var engine: Engine?
    private var conversation: Conversation?
    private(set) var backendName = ""

    var isLoaded: Bool { engine != nil }

    /// Carga el modelo. Tarda unos segundos y no bloquea la pantalla (corre en el actor del motor).
    func load(modelURL: URL, cacheDir: URL) async throws {
        unload()
        var lastError: Error = EngineError.notLoaded
        for backend in [Backend.gpu, Backend.cpu()] {
            do {
                let config = try EngineConfig(
                    modelPath: modelURL.path,
                    backend: backend,
                    maxNumTokens: 4096,
                    cacheDir: cacheDir.path
                )
                let candidate = Engine(engineConfig: config)
                try await candidate.initialize()
                engine = candidate
                backendName = backend == .gpu ? "GPU" : "CPU"
                return
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    /// Abre una conversación nueva que ya sabe quién es Ari, los recuerdos y los últimos mensajes.
    func startConversation(system: String, history: [ChatMessage]) async throws {
        guard let engine else { throw EngineError.notLoaded }
        conversation = nil
        let messages = history.map { Message($0.text, role: $0.role == .user ? .user : .model) }
        let sampler = try SamplerConfig(topK: 40, topP: 0.95, temperature: 0.7)
        let config = ConversationConfig(
            systemMessage: Message(system, role: .system),
            initialMessages: messages,
            samplerConfig: sampler
        )
        conversation = try await engine.createConversation(with: config)
    }

    /// La respuesta llega por partes, mientras Gemma la escribe.
    func reply(to text: String) throws -> AsyncThrowingStream<Message, Error> {
        guard let conversation else { throw EngineError.notLoaded }
        return conversation.sendMessageStream(Message(text))
    }

    func cancel() {
        try? conversation?.cancel()
    }

    func unload() {
        conversation = nil
        engine = nil
        backendName = ""
    }
}
