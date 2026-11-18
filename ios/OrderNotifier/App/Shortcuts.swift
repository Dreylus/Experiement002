import AppIntents

// Siri, Spotlight, the Shortcuts app and the Action Button can run these without opening the app,
// e.g. a Shortcuts automation that goes live at 9:00 and pauses at 15:00.

struct GoLiveIntent: AppIntent {
    static var title: LocalizedStringResource = "Go Live"

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let model = AppModel.shared
        if !model.settings.running { await model.start() }
        return .result(dialog: model.settings.running ? "Orders are live." : "Open the app and allow notifications first.")
    }
}

struct PauseOrdersIntent: AppIntent {
    static var title: LocalizedStringResource = "Pause Orders"

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        AppModel.shared.stop()
        return .result(dialog: "Orders are paused.")
    }
}

struct SendTestOrderIntent: AppIntent {
    static var title: LocalizedStringResource = "Send a Test Order"

    @MainActor
    func perform() async throws -> some IntentResult {
        await AppModel.shared.sendTest(after: 0)
        return .result()
    }
}

struct OrderShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: GoLiveIntent(),
            phrases: ["Go live in \(.applicationName)", "Start orders in \(.applicationName)"],
            shortTitle: "Go Live",
            systemImageName: "dot.radiowaves.left.and.right"
        )
        AppShortcut(
            intent: PauseOrdersIntent(),
            phrases: ["Pause orders in \(.applicationName)", "Stop orders in \(.applicationName)"],
            shortTitle: "Pause Orders",
            systemImageName: "pause.fill"
        )
        AppShortcut(
            intent: SendTestOrderIntent(),
            phrases: ["Send a test order in \(.applicationName)"],
            shortTitle: "Test Order",
            systemImageName: "paperplane.fill"
        )
    }
}
