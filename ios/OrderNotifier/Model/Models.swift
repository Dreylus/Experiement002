import Foundation
import UserNotifications

/// Which sound plays with each order alert.
enum SoundChoice: String, Codable, CaseIterable, Identifiable {
    case system, custom, silent

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "Default"
        case .custom: return "Custom"
        case .silent: return "Silent"
        }
    }
}

/// Everything the user can tweak. Stored as JSON; unknown or missing keys fall back to defaults.
struct OrderSettings: Codable, Equatable {
    // Notification text
    var storeName = "Store"
    var fromText = "Online Store"
    var titlePrefix = "Order #"
    var currency = "$"
    var nextOrder = 1001
    /// Second line of the alert: "from Store".
    var showSubtitle = true
    /// Adds " · Store" after the from text in the body.
    var storeSuffix = false

    // Order contents (whole currency units)
    var minPrice = 15
    var maxPrice = 120
    var maxItems = 3

    // Timing (seconds) and bursts
    var minGap = 20
    var maxGap = 120
    var burstChance = 25
    var burstMax = 4

    // Sound
    var sound: SoundChoice = .system
    var customSoundFile: String?
    var customSoundName: String?
    var customSoundSeconds: Double?

    // State
    var alwaysOn = false
    var running = false
    var onboarded = false

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = OrderSettings()
        func value<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T {
            (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback
        }
        storeName = value(.storeName, d.storeName)
        fromText = value(.fromText, d.fromText)
        titlePrefix = value(.titlePrefix, d.titlePrefix)
        currency = value(.currency, d.currency)
        nextOrder = value(.nextOrder, d.nextOrder)
        showSubtitle = value(.showSubtitle, d.showSubtitle)
        storeSuffix = value(.storeSuffix, d.storeSuffix)
        minPrice = value(.minPrice, d.minPrice)
        maxPrice = value(.maxPrice, d.maxPrice)
        maxItems = value(.maxItems, d.maxItems)
        minGap = value(.minGap, d.minGap)
        maxGap = value(.maxGap, d.maxGap)
        burstChance = value(.burstChance, d.burstChance)
        burstMax = value(.burstMax, d.burstMax)
        sound = value(.sound, d.sound)
        customSoundFile = try? c.decodeIfPresent(String.self, forKey: .customSoundFile)
        customSoundName = try? c.decodeIfPresent(String.self, forKey: .customSoundName)
        customSoundSeconds = try? c.decodeIfPresent(Double.self, forKey: .customSoundSeconds)
        alwaysOn = value(.alwaysOn, d.alwaysOn)
        running = value(.running, d.running)
        onboarded = value(.onboarded, d.onboarded)
    }
}

/// An order that has been handed to iOS to deliver at `fireDate`.
struct PlannedOrder: Codable, Identifiable, Equatable {
    var id: String
    var number: Int
    var cents: Int
    var items: Int
    var fireDate: Date
    var isTest: Bool
}

/// An order that has already been shown.
struct LoggedOrder: Codable, Identifiable, Equatable {
    var id: String
    var number: Int
    var title: String
    var cents: Int
    var items: Int
    var date: Date
}

/// Stats and the recent-orders feed for the Home tab.
struct OrderLog: Codable, Equatable {
    var recent: [LoggedOrder] = []
    var totalOrders = 0
    var totalCents = 0
    var dayKey = 0
    var todayOrders = 0
    var todayCents = 0
    var hourCents: [Int] = Array(repeating: 0, count: 24)

    static func key(for date: Date) -> Int {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return (c.year ?? 0) * 10_000 + (c.month ?? 0) * 100 + (c.day ?? 0)
    }

    mutating func rollDay(to today: Int) {
        guard dayKey != today else { return }
        dayKey = today
        todayOrders = 0
        todayCents = 0
        hourCents = Array(repeating: 0, count: 24)
    }

    mutating func record(_ order: PlannedOrder, title: String, today: Int) {
        rollDay(to: today)
        if Self.key(for: order.fireDate) == today {
            todayOrders += 1
            todayCents += order.cents
            let hour = Calendar.current.component(.hour, from: order.fireDate)
            if hourCents.indices.contains(hour) { hourCents[hour] += order.cents }
        }
        totalOrders += 1
        totalCents += order.cents
        let entry = LoggedOrder(
            id: order.id, number: order.number, title: title,
            cents: order.cents, items: order.items, date: order.fireDate
        )
        recent.insert(entry, at: 0)
        if recent.count > 100 { recent.removeLast(recent.count - 100) }
    }
}

/// What iOS currently allows for our alerts.
struct NotificationHealth: Equatable {
    var status: UNAuthorizationStatus = .notDetermined
    var alertsOn = true
    var bannersOn = true
    var soundsOn = true
    var scheduledSummary = false

    var problems: [String] {
        var list: [String] = []
        switch status {
        case .denied:
            list.append("Notifications are turned off for this app.")
        case .notDetermined:
            list.append("Notifications haven't been allowed yet.")
        default:
            if !alertsOn || !bannersOn { list.append("Banners are turned off, so orders won't slide in at the top.") }
            if !soundsOn { list.append("Sounds are turned off for this app.") }
            if scheduledSummary { list.append("Scheduled Summary is holding alerts back. Set this app to Immediate Delivery.") }
        }
        return list
    }
}

/// An app icon the user can switch to. The pictures come from ios/Branding at build time.
struct AppIconOption: Codable, Identifiable, Hashable {
    /// nil = the primary icon.
    var iconName: String?
    var title: String
    /// Image set used to show the icon inside the app.
    var preview: String

    var id: String { iconName ?? "primary" }

    static func load() -> [AppIconOption] {
        guard
            let url = Bundle.main.url(forResource: "Icons", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let list = try? JSONDecoder().decode([AppIconOption].self, from: data),
            !list.isEmpty
        else {
            return [AppIconOption(iconName: nil, title: "Default", preview: "BrandIcon")]
        }
        return list
    }
}
