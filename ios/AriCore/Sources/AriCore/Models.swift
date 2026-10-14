import Foundation

/// Un mensaje del chat. Las notas (avisos de la app) se muestran, pero Gemma no las recibe.
public struct ChatMessage: Codable, Equatable, Identifiable, Sendable {
    public enum Role: String, Codable, Sendable {
        case user
        case ari
        case note
    }

    public let id: UUID
    public let role: Role
    public var text: String
    public let date: Date

    public init(role: Role, text: String, date: Date = Date(), id: UUID = UUID()) {
        self.id = id
        self.role = role
        self.text = text
        self.date = date
    }
}

/// Algo que la persona le pidió a Ari que recuerde.
public struct Memory: Codable, Equatable, Identifiable, Sendable {
    public let id: UUID
    public let text: String
    public let date: Date

    public init(text: String, date: Date = Date(), id: UUID = UUID()) {
        self.id = id
        self.text = text
        self.date = date
    }
}
