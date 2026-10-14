import SwiftUI
import AriCore

/// La primera vez: el nombre, el tono y el modelo recomendado para este iPhone.
struct OnboardingView: View {
    @EnvironmentObject private var chat: ChatStore
    @State private var name = ""
    @State private var tone: Tone = .frank
    @State private var step = 0
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                if step == 0 {
                    Section {
                        Text("Hola, soy Ari, tu asistente personal. Funciono en tu iPhone, sin mandar tus pláticas a internet.")
                    }
                    Section("¿Cómo te llamas?") {
                        TextField("Tu nombre", text: $name)
                            .textInputAutocapitalization(.words)
                    }
                    Section("¿Cómo quieres que te hable?") {
                        Picker("Tono", selection: $tone) {
                            ForEach(Tone.allCases, id: \.self) { Text($0.title).tag($0) }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                    }
                } else {
                    let advice = chat.recommendation()
                    Section("Mi cerebro") {
                        Text("Para pensar necesito descargar un modelo de IA una sola vez. Después funciono sin internet.")
                        Text(advice.reason).foregroundStyle(advice.tooWeak ? Color.orange : Color.secondary)
                        Text("\(advice.model.title): unos \(ModelCatalog.gb(advice.model.bytes)) GB, solo por Wi-Fi.")
                            .font(.footnote)
                    }
                    Section {
                        Button("Descargar ahora") {
                            chat.download(advice.model)
                            finish()
                        }
                        Button("Más tarde", role: .cancel, action: finish)
                    } footer: {
                        Text("Lo puedes hacer cuando quieras desde Modelo.")
                    }
                }
            }
            .navigationTitle(step == 0 ? "Bienvenida" : "Último paso")
            .toolbar {
                if step == 0 {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Siguiente") { step = 1 }
                    }
                }
            }
        }
        .interactiveDismissDisabled()
    }

    private func finish() {
        chat.saveProfile(Profile(userName: name, tone: tone))
        onDone()
    }
}
