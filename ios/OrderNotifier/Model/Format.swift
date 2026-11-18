import Foundation

/// Text formatting shared by the screens and the alerts.
enum Format {
    private static let decimal: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.locale = Locale(identifier: "en_US")
        return f
    }()

    private static let whole: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = 0
        f.locale = Locale(identifier: "en_US")
        return f
    }()

    /// "$1,250.00"
    static func money(_ cents: Int, _ currency: String) -> String {
        currency + (decimal.string(from: NSNumber(value: Double(cents) / 100)) ?? "0.00")
    }

    /// Compact money for stats: "$843", "$12.3K", "$1.2M".
    static func moneyShort(_ cents: Int, _ currency: String) -> String {
        let v = Double(cents) / 100
        if v >= 1_000_000 { return currency + String(format: "%.1fM", v / 1_000_000) }
        if v >= 10_000 { return currency + String(format: "%.1fK", v / 1000) }
        return currency + (whole.string(from: NSNumber(value: v.rounded())) ?? "0")
    }

    /// "1,204" or "12.3K"
    static func count(_ n: Int) -> String {
        if n >= 10_000 { return String(format: "%.1fK", Double(n) / 1000) }
        return whole.string(from: NSNumber(value: n)) ?? "\(n)"
    }

    /// "45s", "2m", "2m 30s", "1h", "1h 30m"
    static func seconds(_ s: Int) -> String {
        if s < 60 { return "\(s)s" }
        if s < 3600 { return s % 60 == 0 ? "\(s / 60)m" : "\(s / 60)m \(s % 60)s" }
        return s % 3600 == 0 ? "\(s / 3600)h" : "\(s / 3600)h \((s % 3600) / 60)m"
    }

    /// Words for a time window: "10 minutes", "hour", "8 hours", "day".
    static func window(_ s: Int) -> String {
        if s < 3600 { return s < 120 ? "minute" : "\(s / 60) minutes" }
        if s == 3600 { return "hour" }
        if s < 86_400 { return "\(s / 3600) hours" }
        return "day"
    }

    /// Feed-style age: "now", "5m", "3h", "2d".
    static func ago(_ date: Date, now: Date) -> String {
        let s = max(0, Int(now.timeIntervalSince(date)))
        if s < 60 { return "now" }
        if s < 3600 { return "\(s / 60)m" }
        if s < 86_400 { return "\(s / 3600)h" }
        return "\(s / 86_400)d"
    }

    static func items(_ n: Int) -> String { n == 1 ? "1 item" : "\(n) items" }

    /// "12a", "6a", "12p", "6p"
    static func hour(_ h: Int) -> String {
        let h12 = h % 12 == 0 ? 12 : h % 12
        return "\(h12)\(h < 12 ? "a" : "p")"
    }

    static func time(_ date: Date) -> String {
        date.formatted(date: .omitted, time: .shortened)
    }
}
