import SwiftUI

@main
struct TraceMarkApp: App {
    @StateObject private var settingsStore = SettingsStore()
    @StateObject private var templateStore = TemplateStore()

    var body: some Scene {
        WindowGroup {
            HomeView(settingsStore: settingsStore, templateStore: templateStore)
                .tint(TMColor.gold)
                .preferredColorScheme(.dark)
        }
    }
}
