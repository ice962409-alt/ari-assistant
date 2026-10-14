import Foundation
import MetricKit
import AriCore

/// Informes de fallos, solo con permiso de quien usa Ari.
/// - Cierres y congelamientos: los entrega iOS (MetricKit) al día siguiente o al volver a abrir.
/// - Errores atrapados (por ejemplo, el modelo no carga): al momento.
/// Se mandan al intermediario de ari-informes; nunca llevan mensajes, recuerdos ni nombres.
final class Reporter: NSObject, MXMetricManagerSubscriber {
    private let store: AriStore
    private var flushing = false

    init(store: AriStore) {
        self.store = store
    }

    static var endpoint: URL? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "AriInformesURL") as? String, !value.isEmpty else {
            return nil
        }
        return URL(string: value)
    }

    func start() {
        MXMetricManager.shared.add(self)
    }

    // MARK: Errores atrapados

    @MainActor
    func reportError(_ error: Error, summary: String, model: String) {
        let report = Reporter.makeReport(kind: .error, summary: summary, details: String(describing: error), model: model)
        store.enqueueReport(report)
        Task { await flush() }
    }

    // MARK: MetricKit

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        var reports: [CrashReport] = []
        for payload in payloads {
            for crash in payload.crashDiagnostics ?? [] {
                var parts: [String] = []
                if let signal = crash.signal { parts.append("señal \(signal)") }
                if let type = crash.exceptionType { parts.append("excepción \(type)") }
                if let reason = crash.terminationReason { parts.append(reason) }
                let details = String(data: crash.callStackTree.jsonRepresentation(), encoding: .utf8) ?? ""
                reports.append(Reporter.makeReport(
                    kind: .cierre,
                    summary: "Ari se cerró: " + (parts.isEmpty ? "sin detalle" : parts.joined(separator: " · ")),
                    details: details, model: "",
                    appVersion: crash.metaData.applicationBuildVersion
                ))
            }
            for hang in payload.hangDiagnostics ?? [] {
                let details = String(data: hang.callStackTree.jsonRepresentation(), encoding: .utf8) ?? ""
                reports.append(Reporter.makeReport(
                    kind: .congelada,
                    summary: "Ari dejó de responder \(Int(hang.hangDuration.converted(to: .seconds).value)) s",
                    details: details, model: "",
                    appVersion: hang.metaData.applicationBuildVersion
                ))
            }
        }
        guard !reports.isEmpty else { return }
        Task { @MainActor in
            reports.forEach { self.store.enqueueReport($0) }
            await self.flush()
        }
    }

    // MARK: Envío

    /// Manda los informes pendientes que ya toca reintentar. Sin red, quedan para después.
    @MainActor
    func flush() async {
        guard !flushing, store.data.reportsAllowed == true, let endpoint = Reporter.endpoint else { return }
        flushing = true
        defer { flushing = false }

        for report in store.data.pendingReports where report.isDue() {
            var request = URLRequest(url: endpoint)
            request.httpMethod = "POST"
            request.timeoutInterval = 20
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("ari", forHTTPHeaderField: "X-Ari-App")
            request.httpBody = try? report.body(installId: store.data.installId)
            do {
                let (_, response) = try await URLSession.shared.data(for: request)
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                switch CrashReport.outcome(forStatus: status) {
                case .sent, .drop: store.removeReport(id: report.id)
                case .retryLater: store.recordFailedAttempt(id: report.id)
                }
            } catch {
                store.recordFailedAttempt(id: report.id)
            }
        }
    }

    // MARK: Datos técnicos del teléfono

    static func makeReport(
        kind: CrashReport.Kind, summary: String, details: String, model: String, appVersion: String? = nil
    ) -> CrashReport {
        CrashReport(
            kind: kind,
            summary: summary,
            details: details,
            appVersion: appVersion ?? Reporter.appVersion,
            osVersion: "iOS " + ProcessInfo.processInfo.operatingSystemVersionString,
            device: Reporter.deviceModel,
            ramGb: ModelCatalog.gb(Int64(ProcessInfo.processInfo.physicalMemory)),
            model: model
        )
    }

    static var appVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0.0.0"
    }

    /// Identificador del modelo de iPhone, por ejemplo "iPhone16,1".
    static var deviceModel: String {
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { buffer in
            String(decoding: buffer.prefix { $0 != 0 }, as: UTF8.self)
        }
    }
}
