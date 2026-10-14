import SwiftUI
import AriCore

struct ChatView: View {
    @EnvironmentObject private var chat: ChatStore
    @State private var input = ""
    @State private var sheet: Sheet?
    @FocusState private var typing: Bool

    enum Sheet: String, Identifiable {
        case memories, model, settings
        var id: String { rawValue }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                messageList
                inputBar
            }
            .background(AriTheme.background)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Ari").font(.headline)
                        Text(statusText).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("Memoria") { sheet = .memories }
                    Button("Modelo") { sheet = .model }
                    Button { sheet = .settings } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel("Ajustes")
                }
            }
            .toolbarBackground(AriTheme.background, for: .navigationBar)
            .sheet(item: $sheet) { sheet in
                NavigationStack {
                    switch sheet {
                    case .memories: MemoriesView()
                    case .model: ModelView()
                    case .settings: SettingsView()
                    }
                }
                .environmentObject(chat)
                .preferredColorScheme(.dark)
            }
        }
    }

    private var statusText: String {
        if chat.busy { return "Pensando…" }
        switch chat.modelState {
        case .missing: return "Sin modelo: toca Modelo"
        case .downloading(_, let progress): return "Descargando el modelo: \(Int(progress * 100)) %"
        case .loading(let model): return "Cargando \(model.title)…"
        case .ready: return "Ari está lista"
        case .failed: return "El modelo no está listo"
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    if chat.messages.isEmpty {
                        Text(greeting)
                            .font(.title3)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.top, 80)
                    }
                    ForEach(chat.messages) { message in
                        MessageRow(message: message).id(message.id)
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: chat.messages.last?.text) { _, _ in
                if let last = chat.messages.last { proxy.scrollTo(last.id, anchor: .bottom) }
            }
            .onChange(of: chat.messages.count) { _, _ in
                if let last = chat.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
            }
        }
    }

    private var greeting: String {
        let name = chat.profile?.userName ?? ""
        return name.isEmpty ? "Hola, ¿en qué te ayudo?" : "Hola, \(name). ¿En qué te ayudo?"
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField("Escribe a Ari…", text: $input, axis: .vertical)
                .lineLimit(1...5)
                .focused($typing)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(AriTheme.bubble, in: RoundedRectangle(cornerRadius: 20))
                .submitLabel(.send)
                .onSubmit(send)
            if chat.busy {
                Button(action: chat.stop) {
                    Image(systemName: "stop.fill").frame(width: 40, height: 40)
                }
                .background(AriTheme.bubble, in: Circle())
                .accessibilityLabel("Detener")
            } else {
                Button(action: send) {
                    Image(systemName: "arrow.up").font(.headline).frame(width: 40, height: 40)
                }
                .foregroundStyle(.black)
                .background(AriTheme.ice, in: Circle())
                .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityLabel("Enviar")
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private func send() {
        let text = input
        input = ""
        chat.send(text)
    }
}

struct MessageRow: View {
    let message: ChatMessage

    var body: some View {
        switch message.role {
        case .user:
            HStack {
                Spacer(minLength: 48)
                Text(message.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(AriTheme.bubble, in: RoundedRectangle(cornerRadius: 18))
                    .textSelection(.enabled)
            }
        case .ari:
            VStack(alignment: .leading, spacing: 4) {
                Label("Ari", systemImage: "circle.fill")
                    .font(.caption.bold())
                    .foregroundStyle(AriTheme.ice)
                    .labelStyle(.titleOnly)
                if message.text.isEmpty {
                    Text("Pensando…").foregroundStyle(.secondary)
                } else {
                    Text(LocalizedStringKey(message.text)).textSelection(.enabled)
                }
            }
        case .note:
            Text(message.text)
                .font(.footnote.italic())
                .foregroundStyle(.secondary)
        }
    }
}
