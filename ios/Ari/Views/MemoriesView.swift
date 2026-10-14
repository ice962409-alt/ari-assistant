import SwiftUI
import AriCore

/// Ver, agregar y borrar recuerdos.
struct MemoriesView: View {
    @EnvironmentObject private var chat: ChatStore
    @Environment(\.dismiss) private var dismiss
    @State private var newMemory = ""

    var body: some View {
        List {
            Section {
                HStack {
                    TextField("Algo que Ari debe recordar", text: $newMemory, axis: .vertical)
                    Button("Guardar") {
                        chat.addMemory(newMemory)
                        newMemory = ""
                    }
                    .disabled(newMemory.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            } footer: {
                Text("También puedes escribirle «recuerda que …» en el chat.")
            }

            Section("Recuerdos") {
                if chat.memories.isEmpty {
                    Text("Todavía no hay recuerdos.").foregroundStyle(.secondary)
                }
                ForEach(chat.memories) { memory in
                    Text(memory.text)
                }
                .onDelete { offsets in
                    offsets.map { chat.memories[$0] }.forEach(chat.deleteMemory)
                }
            }
        }
        .navigationTitle("Memoria")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Listo") { dismiss() } } }
    }
}
