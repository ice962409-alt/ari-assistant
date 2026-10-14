import SwiftUI
import AriCore

struct SettingsView: View {
    @EnvironmentObject private var chat: ChatStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var tone: Tone = .frank
    @State private var confirmClear = false

    var body: some View {
        Form {
            Section("Tú") {
                TextField("Tu nombre", text: $name)
                    .textInputAutocapitalization(.words)
                Picker("Tono de Ari", selection: $tone) {
                    ForEach(Tone.allCases, id: \.self) { Text($0.title).tag($0) }
                }
            }

            Section {
                Toggle("Enviar informes de fallos", isOn: Binding(
                    get: { chat.reportsAllowed == true },
                    set: { chat.setReportsAllowed($0) }
                ))
            } footer: {
                Text("Solo datos técnicos (versión, modelo de iPhone y el error). Nunca tus mensajes, recuerdos ni tu nombre.")
            }

            Section {
                Button("Borrar la conversación", role: .destructive) { confirmClear = true }
            } footer: {
                Text("Los recuerdos se conservan.")
            }

            Section("Acerca de Ari") {
                LabeledContent("Versión", value: Reporter.appVersion)
                LabeledContent("Modelo", value: chat.modelLabel)
                Text("Ari usa Gemma 4 de Google mediante LiteRT-LM (licencia Apache 2.0). "
                    + "Tus pláticas y recuerdos se quedan en este iPhone.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Ajustes")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Listo") {
                    chat.saveProfile(Profile(userName: name, tone: tone))
                    dismiss()
                }
            }
        }
        .onAppear {
            name = chat.profile?.userName ?? ""
            tone = chat.profile?.tone ?? .frank
        }
        .confirmationDialog("¿Borrar toda la conversación?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Borrar", role: .destructive, action: chat.clearChat)
        }
    }
}
