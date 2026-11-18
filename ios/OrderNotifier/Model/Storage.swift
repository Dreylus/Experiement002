import Foundation
import UserNotifications

/// Saves settings in UserDefaults and the order queue / log as JSON files.
enum Storage {
    private static let settingsKey = "settings.v2"

    private static var folder: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("OrderNotifier", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func loadSettings() -> OrderSettings {
        guard
            let data = UserDefaults.standard.data(forKey: settingsKey),
            let settings = try? JSONDecoder().decode(OrderSettings.self, from: data)
        else { return OrderSettings() }
        return settings
    }

    static func save(_ settings: OrderSettings) {
        if let data = try? JSONEncoder().encode(settings) {
            UserDefaults.standard.set(data, forKey: settingsKey)
        }
    }

    static func loadPlanned() -> [PlannedOrder] { load("planned.json") ?? [] }
    static func save(_ planned: [PlannedOrder]) { store(planned, "planned.json") }

    static func loadLog() -> OrderLog { load("log.json") ?? OrderLog() }
    static func save(_ log: OrderLog) { store(log, "log.json") }

    /// Used by the screenshot UI test so every run starts fresh.
    static func resetAll() {
        UserDefaults.standard.removeObject(forKey: settingsKey)
        try? FileManager.default.removeItem(at: folder)
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    private static func load<T: Decodable>(_ name: String) -> T? {
        guard let data = try? Data(contentsOf: folder.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func store<T: Encodable>(_ value: T, _ name: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: folder.appendingPathComponent(name), options: .atomic)
    }
}
