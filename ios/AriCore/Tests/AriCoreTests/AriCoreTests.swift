import XCTest
@testable import AriCore

final class CommandTests: XCTestCase {
    func testSlashCommands() {
        XCTAssertEqual(Command.parse("/recuerda Me gusta el café"), .remember("Me gusta el café"))
        XCTAssertEqual(Command.parse("/RECUERDA  té verde "), .remember("té verde"))
        XCTAssertEqual(Command.parse("/memoria"), .listMemories)
        XCTAssertEqual(Command.parse("/memorias"), .listMemories)
        XCTAssertEqual(Command.parse("/olvida café"), .forget("café"))
        XCTAssertEqual(Command.parse("/recuerda"), .help)
        XCTAssertEqual(Command.parse("/ayuda"), .help)
        XCTAssertEqual(Command.parse("/foo"), .unknown("/foo"))
    }

    func testNaturalPhrases() {
        XCTAssertEqual(Command.parse("Recuerda que mi perro se llama Toby."), .remember("Mi perro se llama Toby"))
        XCTAssertEqual(Command.parse("acuérdate de que pago la renta el 5"), .remember("Pago la renta el 5"))
        XCTAssertEqual(Command.parse("¿Qué recuerdas de mí?"), .listMemories)
        XCTAssertEqual(Command.parse("olvida café"), .forget("café"))
    }

    func testConversationIsNotACommand() {
        XCTAssertNil(Command.parse("Hola Ari, ¿cómo estás?"))
        XCTAssertNil(Command.parse("olvida eso"))
        XCTAssertNil(Command.parse("Olvida lo que te dije."))
        XCTAssertNil(Command.parse("olvida que te pedí ayuda con la tarea de matemáticas ayer"))
        XCTAssertNil(Command.parse("   "))
    }

    func testWholeWordMatching() {
        XCTAssertTrue(AriText.containsWords("Me gusta el café", "cafe"))
        XCTAssertFalse(AriText.containsWords("Voy a la cafetería", "café"))
        XCTAssertTrue(AriText.containsWords("Mi perro se llama Toby", "se llama toby"))
        XCTAssertFalse(AriText.containsWords("Toby", "se llama Toby"))
    }
}

final class HistoryTests: XCTestCase {
    private func msg(_ role: ChatMessage.Role, _ text: String) -> ChatMessage { ChatMessage(role: role, text: text) }

    func testStartsWithUserAndEndsWithAri() {
        let saved = [msg(.ari, "Hola"), msg(.user, "¿Qué hora es?"), msg(.ari, "Las 5"), msg(.note, "Modelo cargado"), msg(.user, "Gracias")]
        let trimmed = HistoryTrimmer.trim(saved)
        XCTAssertEqual(trimmed.map(\.text), ["¿Qué hora es?", "Las 5"])
    }

    func testRespectsCharacterLimit() {
        var saved: [ChatMessage] = []
        for i in 0..<10 {
            saved.append(msg(.user, "pregunta \(i) " + String(repeating: "x", count: 900)))
            saved.append(msg(.ari, "respuesta \(i) " + String(repeating: "y", count: 900)))
        }
        let trimmed = HistoryTrimmer.trim(saved)
        XCTAssertLessThanOrEqual(trimmed.reduce(0) { $0 + $1.text.count }, HistoryTrimmer.maxTotalChars)
        XCTAssertEqual(trimmed.first?.role, .user)
        XCTAssertEqual(trimmed.last?.role, .ari)
        XCTAssertTrue(trimmed.last!.text.hasPrefix("respuesta 9"))
    }

    func testLongMessagesAreCut() {
        let trimmed = HistoryTrimmer.trim([msg(.user, String(repeating: "a", count: 3000)), msg(.ari, "ok")])
        XCTAssertEqual(trimmed.first?.text.count, HistoryTrimmer.maxMessageChars + 1)
    }
}

final class ProfileTests: XCTestCase {
    func testPersonalityUsesNameAndForbidsInventions() {
        let text = Profile(userName: "  Rosa \n López ", tone: .professional).personality()
        XCTAssertTrue(text.contains("asistente personal de Rosa López"))
        XCTAssertTrue(text.contains("nunca le llames Ari"))
        XCTAssertTrue(text.contains("nunca inventes menús"))
        XCTAssertTrue(text.contains("tono profesional"))
    }

    func testCleanName() {
        XCTAssertEqual(Profile.cleanName("  Javi  "), "Javi")
        XCTAssertEqual(Profile.cleanName(String(repeating: "a", count: 80)).count, Profile.maxNameLength)
    }
}

final class ModelCatalogTests: XCTestCase {
    private let gib: UInt64 = 1024 * 1024 * 1024

    func testRecommendations() {
        XCTAssertEqual(ModelCatalog.recommend(ramBytes: 8 * gib, freeBytes: 20_000_000_000).model, .e4b)
        XCTAssertEqual(ModelCatalog.recommend(ramBytes: 8 * gib, freeBytes: 3_500_000_000).model, .e2b)
        XCTAssertEqual(ModelCatalog.recommend(ramBytes: 6 * gib, freeBytes: 20_000_000_000).model, .e2b)
        XCTAssertTrue(ModelCatalog.recommend(ramBytes: 3 * gib, freeBytes: 20_000_000_000).tooWeak)
    }

    func testFileNames() {
        XCTAssertEqual(AriModel.e4b.fileName, "gemma-4-E4B-it.litertlm")
        XCTAssertTrue(AriModel.e2b.url.absoluteString.hasSuffix("gemma-4-E2B-it.litertlm"))
    }
}

final class StoreTests: XCTestCase {
    private func tempURL() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("ari.json")
    }

    func testMemoriesPersistAndDeduplicate() {
        let url = tempURL()
        let store = AriStore(fileURL: url)
        XCTAssertTrue(store.addMemory("Me gusta el café"))
        XCTAssertFalse(store.addMemory("me gusta el CAFE"))
        store.addMemory("Voy a la cafetería los lunes")
        store.addMessage(ChatMessage(role: .user, text: "hola"))

        let reopened = AriStore(fileURL: url)
        XCTAssertEqual(reopened.data.memories.count, 2)
        XCTAssertEqual(reopened.data.messages.map(\.text), ["hola"])
        XCTAssertEqual(reopened.data.installId, store.data.installId)

        XCTAssertEqual(reopened.forget("café"), 1)
        XCTAssertEqual(reopened.data.memories.map(\.text), ["Voy a la cafetería los lunes"])
    }

    func testOldFilesStillLoad() throws {
        let url = tempURL()
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data(#"{"memories":[{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","text":"Hola","date":"2026-10-14T10:00:00Z"}]}"#.utf8)
            .write(to: url)
        let store = AriStore(fileURL: url)
        XCTAssertEqual(store.data.memories.map(\.text), ["Hola"])
        XCTAssertNil(store.data.reportsAllowed)
    }

    func testMessagesAreCapped() {
        let store = AriStore(fileURL: nil)
        for i in 0..<(AriStore.maxMessages + 5) { store.addMessage(ChatMessage(role: .user, text: "\(i)")) }
        XCTAssertEqual(store.data.messages.count, AriStore.maxMessages)
        XCTAssertEqual(store.data.messages.first?.text, "5")
    }

    func testReportsNeedPermission() {
        let store = AriStore(fileURL: nil)
        let report = CrashReport(kind: .error, summary: "x", details: "", appVersion: "0.1.0",
                                 osVersion: "iOS 18", device: "iPhone", ramGb: "8", model: "e2b")
        store.enqueueReport(report)
        XCTAssertTrue(store.data.pendingReports.isEmpty)
        store.setReportsAllowed(true)
        store.enqueueReport(report)
        XCTAssertEqual(store.data.pendingReports.count, 1)
        for _ in 0..<CrashReport.maxAttempts { store.recordFailedAttempt(id: report.id) }
        XCTAssertTrue(store.data.pendingReports.isEmpty)
    }
}

final class CrashReportTests: XCTestCase {
    func testBodyFollowsContract() throws {
        let report = CrashReport(kind: .cierre, summary: "EXC_BAD_ACCESS", details: "Llamar a ana@example.com", appVersion: "0.1.0",
                                 osVersion: "iOS 18.0", device: "iPhone16,1", ramGb: "8", model: "Gemma 4 E2B (GPU)")
        let installId = UUID()
        let json = try JSONSerialization.jsonObject(with: report.body(installId: installId)) as! [String: String]
        XCTAssertEqual(json["platform"], "ios")
        XCTAssertEqual(json["kind"], "cierre")
        XCTAssertEqual(json["installId"], installId.uuidString)
        XCTAssertEqual(json["details"], "Llamar a [correo]")
    }

    func testScrubKeepsDatesAndTraces() {
        let trace = "2026-10-14 22:31:09 Ari.ChatStore.send(_:) + 120"
        XCTAssertEqual(CrashReport.scrub(trace), trace)
        XCTAssertEqual(CrashReport.scrub("+52 55 1234 5678"), "[número]")
    }

    func testOutcomesAndRetries() {
        XCTAssertEqual(CrashReport.outcome(forStatus: 201), .sent)
        XCTAssertEqual(CrashReport.outcome(forStatus: 413), .drop)
        XCTAssertEqual(CrashReport.outcome(forStatus: 429), .retryLater)
        XCTAssertEqual(CrashReport.outcome(forStatus: 500), .retryLater)

        var report = CrashReport(kind: .error, summary: "x", details: "", appVersion: "0.1.0",
                                 osVersion: "", device: "", ramGb: "", model: "")
        let now = Date()
        XCTAssertTrue(report.isDue(now: now))
        report.attempts = 1
        report.lastAttempt = now
        XCTAssertFalse(report.isDue(now: now.addingTimeInterval(30)))
        XCTAssertTrue(report.isDue(now: now.addingTimeInterval(61)))
    }
}
