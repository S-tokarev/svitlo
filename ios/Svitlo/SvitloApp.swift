import SwiftUI

@main
struct SvitloApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        RefreshScheduler.register()
    }

    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(model)
                .preferredColorScheme(scheme)
                .task { await model.startup() }
                .onChange(of: scenePhase) { _, phase in
                    guard phase == .active else { return }
                    RefreshScheduler.schedule()
                    Task { await model.refreshIfStale() }
                }
        }
    }

    private var scheme: ColorScheme? {
        switch model.theme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}
