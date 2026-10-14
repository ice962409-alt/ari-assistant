import SwiftUI
import UniformTypeIdentifiers
import AriCore

/// Descargar, cargar o elegir el modelo.
struct ModelView: View {
    @EnvironmentObject private var chat: ChatStore
    @Environment(\.dismiss) private var dismiss
    @State private var importing = false

    var body: some View {
        let advice = chat.recommendation()
        Form {
            Section("Estado") {
                switch chat.modelState {
                case .missing:
                    Text("Todavía no tengo modelo.")
                case .downloading(let model, let progress):
                    VStack(alignment: .leading) {
                        Text("Descargando \(model.title)…")
                        ProgressView(value: progress)
                        Text("Solo por Wi-Fi. Sigue aunque bloquees el iPhone.").font(.footnote).foregroundStyle(.secondary)
                    }
                    Button("Cancelar descarga", role: .destructive, action: chat.cancelDownload)
                case .loading(let model):
                    HStack { ProgressView(); Text("Cargando \(model.title)…") }
                case .ready(let label):
                    Label(label, systemImage: "checkmark.circle.fill").foregroundStyle(AriTheme.ice)
                case .failed(let message):
                    Text(message).foregroundStyle(.orange)
                }
            }

            Section {
                Text(advice.reason).foregroundStyle(advice.tooWeak ? Color.orange : Color.secondary)
                ForEach(AriModel.allCases, id: \.self) { model in
                    modelRow(model, recommended: model == advice.model)
                }
            } header: {
                Text("Modelos")
            } footer: {
                Text("E4B es el más listo; E2B es más ligero y rápido. Después de descargarlo, Ari funciona sin internet.")
            }

            Section {
                Button("Usar un archivo .litertlm") { importing = true }
            } footer: {
                Text("Si ya descargaste el modelo con el navegador, elígelo en Archivos.")
            }
        }
        .navigationTitle("Modelo")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Listo") { dismiss() } } }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
            if case .success(let url) = result {
                Task { await chat.importModel(from: url) }
            }
        }
    }

    @ViewBuilder
    private func modelRow(_ model: AriModel, recommended: Bool) -> some View {
        let downloaded = ModelFiles.exists(model)
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(model.title).font(.headline)
                if recommended { Text("Recomendado").font(.caption).foregroundStyle(AriTheme.ice) }
                Spacer()
                Text("\(ModelCatalog.gb(model.bytes)) GB").font(.caption).foregroundStyle(.secondary)
            }
            HStack {
                if downloaded {
                    Button("Usar") { Task { await chat.loadModel(model) } }
                        .disabled(chat.currentModel == model && isReady)
                    Button("Borrar", role: .destructive) { chat.deleteModel(model) }
                } else {
                    Button("Descargar") { chat.download(model) }
                        .disabled(isDownloading)
                }
            }
            .buttonStyle(.bordered)
        }
        .padding(.vertical, 4)
    }

    private var isReady: Bool {
        if case .ready = chat.modelState { return true }
        return false
    }

    private var isDownloading: Bool {
        if case .downloading = chat.modelState { return true }
        return false
    }
}
