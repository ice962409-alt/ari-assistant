import SwiftUI
import AriCore

@main
struct AriApp: App {
    @StateObject private var chat = ChatStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(chat)
                .preferredColorScheme(.dark)
                .tint(AriTheme.ice)
                .task { await chat.start() }
        }
    }
}

enum AriTheme {
    /// El color hielo de Ari, el mismo de Android.
    static let ice = Color(red: 0.63, green: 0.85, blue: 0.95)
    static let background = Color(red: 0.07, green: 0.07, blue: 0.09)
    static let bubble = Color(red: 0.16, green: 0.16, blue: 0.19)
}

struct RootView: View {
    @EnvironmentObject private var chat: ChatStore
    @State private var askReports = false

    var body: some View {
        ChatView()
            .fullScreenCover(isPresented: .constant(chat.needsOnboarding)) {
                OnboardingView {
                    // Se pregunta cuando ya se cerró la bienvenida, para que la alerta sí se vea.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                        askReports = chat.reportsAllowed == nil
                    }
                }
                    .environmentObject(chat)
            }
            .alert("¿Enviar informes de fallos?", isPresented: $askReports) {
                Button("Sí, enviar") { chat.setReportsAllowed(true) }
                Button("No", role: .cancel) { chat.setReportsAllowed(false) }
            } message: {
                Text("Si Ari se cierra o falla, puede mandar un informe técnico para arreglarlo. "
                    + "Nunca incluye tus mensajes, recuerdos ni tu nombre. Lo puedes cambiar en Ajustes.")
            }
    }
}
